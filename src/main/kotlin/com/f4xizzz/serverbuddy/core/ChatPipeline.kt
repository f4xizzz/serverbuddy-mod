package com.f4xizzz.serverbuddy.core

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs handlers by the order of their stage, not by the order they were registered. The first handler that answers `false` blocks the
 * message and the rest never run (the same rule as a Fabric `ALLOW_*` event), so *who runs first* decides who sees the message.
 * Registering in a different order (a different module starting first) must never change the result: that is what this class is for.
 *
 * Generic on purpose, so the order can be unit tested without a server; [ChatPipeline] is the one for the public chat.
 */
class StageChain<S : Comparable<S>, C>(private val onError: (S, Exception) -> Unit = { _, _ -> }) {
    private class Entry<S, C>(val stage: S, val handler: (C) -> Boolean)

    private val entries = CopyOnWriteArrayList<Entry<S, C>>()

    /** The handlers sorted by stage; inside a stage, by registration order (the sort is stable). */
    @Volatile
    private var sorted: List<Entry<S, C>> = emptyList()

    @Synchronized
    fun register(stage: S, handler: (C) -> Boolean) {
        entries.add(Entry(stage, handler))
        sorted = entries.sortedBy { it.stage }
    }

    /**
     * Runs the handlers of the stages accepted by [only] (all of them by default). Returns false as soon as one blocks the message.
     * A handler that throws is logged through `onError` and counted as "let it through": a bug in one module must not silence the chat.
     */
    fun run(context: C, only: (S) -> Boolean = { true }): Boolean {
        for (entry in sorted) {
            if (!only(entry.stage)) continue
            val allowed = try {
                entry.handler(context)
            } catch (e: Exception) {
                onError(entry.stage, e)
                true
            }
            if (!allowed) return false
        }
        return true
    }
}

/**
 * The only door of the public chat (`ServerMessageEvents.ALLOW_CHAT_MESSAGE`). Every ServerBuddy module that has an opinion about a chat
 * message registers a handler in a [Stage]; the stages ALWAYS run in this order, whichever module started first:
 *
 *  1. [Stage.GATE]    mute and staff chat (Essentials): what is not public chat never reaches the next stages.
 *  2. [Stage.ANSWER]  trivia: sees every public message first, so a correct answer is never eaten by the guard or by the format.
 *  3. [Stage.GUARD]   chat guard: repeated messages, flood and chat bans. A message that already won a trivia round is not counted.
 *  4. [Stage.FORMAT]  chat format (Essentials): cancels the vanilla line and sends its own, so it has to be the last.
 *
 * GATE, ANSWER and GUARD run BEFORE the listeners of other mods (so nobody else's chat mod can take a trivia answer away) and FORMAT runs
 * AFTER them (so what another mod blocks is not re-sent with our format). Each Fabric phase has exactly one listener, ours.
 */
object ChatPipeline {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    enum class Stage { GATE, ANSWER, GUARD, FORMAT }

    /** One chat message going through the stages. */
    class Context(val message: PlayerChatMessage, val sender: ServerPlayer, val params: ChatType.Bound) {
        /** What the player typed (the text that was signed). */
        val text: String get() = message.signedContent()

        /** Set by [Stage.ANSWER] when the text was a correct answer to a game: the guard does not count it as a repeat. */
        var answered: Boolean = false
    }

    private val chain = StageChain<Stage, Context> { stage, error -> LOGGER.error("The chat stage {} failed; the message goes on.", stage, error) }

    private val EARLY: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "chat_pipeline_early")
    private val LATE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "chat_pipeline_late")

    /** The context of the message being handled, shared by the two Fabric phases (they run one after the other, on the same thread). */
    private val current = ThreadLocal<Context?>()

    @Volatile
    private var installed = false

    /** [handler] answers true to let the message go on and false to block it (nobody sees it). */
    @JvmStatic
    fun register(stage: Stage, handler: (Context) -> Boolean) {
        chain.register(stage, handler)
    }

    /** Hooks the pipeline into Fabric. Called once, before the modules start. */
    @JvmStatic
    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        val event = ServerMessageEvents.ALLOW_CHAT_MESSAGE
        event.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE)
        event.addPhaseOrdering(Event.DEFAULT_PHASE, LATE)
        event.register(EARLY, ServerMessageEvents.AllowChatMessage { message, sender, params ->
            val context = Context(message, sender, params)
            current.set(context)
            val allowed = chain.run(context) { it != Stage.FORMAT }
            if (!allowed) current.remove()
            allowed
        })
        event.register(LATE, ServerMessageEvents.AllowChatMessage { message, sender, params ->
            val context = current.get()?.takeIf { it.message === message && it.sender === sender } ?: Context(message, sender, params)
            current.remove()
            chain.run(context) { it == Stage.FORMAT }
        })
    }
}
