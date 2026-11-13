package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.CommandHooks
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.commandblock.CommandBlockService
import com.f4xizzz.serverbuddy.modules.commandblock.CommandMatcher
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * O gerenciador de chat: barra mensagens repetidas (e, se ligado, rápidas demais), dá os avisos, aplica o ban de chat e guarda o
 * estado de cada jogador no banco. A conta fica em [ChatGuardLogic]; aqui é a ligação com o Minecraft.
 */
object ChatGuardService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/ChatGuard")

    const val BYPASS = "serverbuddy.chatguard.bypass"
    const val ADMIN = "serverbuddy.chatguard.admin"
    const val NOTIFY = "serverbuddy.chatguard.notify"

    /** A checagem roda antes dos outros ouvintes de chat (mute, chat da equipe...), para o banido não furar nenhum deles. */
    private val EARLY: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "chatguard")

    @Volatile
    var config: ChatGuardConfig = ChatGuardConfig()
        private set

    @Volatile
    var ready: Boolean = false
        private set

    private class Tracker {
        val messages = ArrayList<RecentMessage>()
        val times = ArrayList<Long>()
        var lastNotice = 0L
    }

    private val trackers = ConcurrentHashMap<UUID, Tracker>()
    private val states = ConcurrentHashMap<UUID, GuardState>()

    fun load() {
        config = sanitize(ConfigFiles.load("ChatGuard", "config.json", ChatGuardConfig::class.java) { ChatGuardConfig() })
    }

    /** Arruma o que um JSON editado à mão pode deixar nulo (`"ban": null`), para o resto do código não se preocupar. */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
    internal fun sanitize(raw: ChatGuardConfig): ChatGuardConfig {
        val ban: ChatBanSettings = (raw.ban as ChatBanSettings?) ?: ChatBanSettings()
        return ChatGuardConfig(
            raw.enabled,
            (raw.duplicates as DuplicateSettings?) ?: DuplicateSettings(),
            (raw.flood as FloodSettings?) ?: FloodSettings(),
            (raw.warnings as WarningSettings?) ?: WarningSettings(),
            ChatBanSettings(
                (ban.durations as List<String?>?).orEmpty().filterNotNull(),
                ban.extraPerWarning ?: "0",
                ban.max ?: "0",
                ban.memoryDays,
                (ban.blockedCommands as List<String?>?).orEmpty().filterNotNull()
            ),
            (raw.staff as ChatGuardStaff?) ?: ChatGuardStaff(),
            (raw.messages as ChatGuardMessages?) ?: ChatGuardMessages()
        )
    }

    fun register() {
        ServerLifecycleEvents.SERVER_STARTING.register {
            ChatGuardRepository.createTables()
            ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { ready = false }
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE)
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(EARLY, ServerMessageEvents.AllowChatMessage { message, sender, _ -> onChat(sender, message.signedContent()) })
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> if (ready) stateOf(handler.player.uuid) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val id = handler.player.uuid
            trackers.remove(id)
            states.remove(id)
        }
        CommandHooks.register { source, command -> blockCommand(source, command) }
    }

    // ---------------------------------------------------------------- estado

    /** O estado de [uuid] (do cache se está online; lê do banco na primeira vez). */
    fun stateOf(uuid: UUID): GuardState = states.getOrPut(uuid) { ChatGuardRepository.load(uuid) ?: GuardState() }

    /** O estado de qualquer jogador (online ou não), já com o esquecimento aplicado. Quem chama grava se mudar. */
    fun peek(uuid: UUID): GuardState {
        val state = states[uuid] ?: (ChatGuardRepository.load(uuid) ?: GuardState())
        ChatGuardLogic.decay(state, System.currentTimeMillis(), config.warnings, config.ban)
        return state
    }

    fun persist(uuid: UUID, name: String, state: GuardState) {
        states[uuid]?.let { if (it !== state) states[uuid] = state }
        ChatGuardRepository.save(uuid, name, state)
    }

    // ---------------------------------------------------------------- o chat

    private fun record(tracker: Tracker, normalized: String, now: Long, cfg: ChatGuardConfig) {
        if (normalized.isNotEmpty()) tracker.messages.add(RecentMessage(normalized, now))
        tracker.times.add(now)
        val keep = maxOf(cfg.duplicates.historySize, 1) * 2
        while (tracker.messages.size > keep) tracker.messages.removeAt(0)
        val floodMs = cfg.flood.seconds.coerceAtLeast(1) * 1000L
        tracker.times.removeAll { now - it > floodMs }
    }

    /** Devolve true se a mensagem pode seguir. */
    private fun onChat(sender: ServerPlayer, text: String): Boolean {
        val cfg = config
        if (!cfg.enabled || !ready) return true
        if (SbPermissions.check(sender, BYPASS, 3)) return true
        val now = System.currentTimeMillis()
        val state = stateOf(sender.uuid)
        ChatGuardLogic.decay(state, now, cfg.warnings, cfg.ban)
        if (state.bannedAt(now)) {
            noticeBanned(sender, state, now)
            return false
        }
        val tracker = trackers.getOrPut(sender.uuid) { Tracker() }
        val normalized = ChatGuardLogic.normalize(text)
        val flood = ChatGuardLogic.isFlooding(tracker.times, now, cfg.flood)
        val duplicate = !flood && ChatGuardLogic.isDuplicate(tracker.messages, normalized, now, cfg.duplicates)
        // a tentativa entra no histórico mesmo barrada: quem insiste mantém a janela aberta
        record(tracker, normalized, now, cfg)
        if (!flood && !duplicate) return true

        val outcome = ChatGuardLogic.violation(state, now, cfg.warnings, cfg.ban)
        persist(sender.uuid, sender.gameProfile.name, state)
        val reason = if (flood) cfg.messages.reasonFlood else cfg.messages.reasonDuplicate
        when (outcome) {
            is Outcome.Warned -> warned(sender, outcome, reason, flood, text)
            is Outcome.Banned -> {
                tracker.messages.clear()
                banned(sender.server, sender.uuid, sender.gameProfile.name, outcome, reason, "automatic")
            }
        }
        return false
    }

    private fun warned(player: ServerPlayer, outcome: Outcome.Warned, reason: String, flood: Boolean, text: String) {
        val cfg = config
        val vars = mapOf("count" to outcome.count, "max" to outcome.max, "reason" to reason, "player" to player.gameProfile.name)
        val template = when {
            flood -> cfg.messages.warningFlood
            outcome.count >= outcome.max -> cfg.messages.warningLast
            else -> cfg.messages.warning
        }
        Texts.send(player, template, vars)
        if (cfg.warnings.showTitle) {
            player.connection.send(ClientboundSetTitlesAnimationPacket(5, 40, 10))
            player.connection.send(ClientboundSetTitleTextPacket(Texts.render(cfg.messages.warningTitle, vars)))
            player.connection.send(ClientboundSetSubtitleTextPacket(Texts.render(cfg.messages.warningSubtitle, vars)))
        }
        ChatGuardRepository.log(player.uuid, player.gameProfile.name, "WARNING", "${outcome.count}/${outcome.max} · $reason: $text")
        if (cfg.staff.logToConsole) LOGGER.info("{} levou o aviso {}/{} ({}): {}", player.gameProfile.name, outcome.count, outcome.max, reason, text)
        if (cfg.staff.notifyWarnings) notifyStaff(player.server, cfg.messages.staffWarning, vars + ("text" to Texts.escape(text.take(120))))
    }

    /** Avisa o jogador (se está online), a equipe e, se ligado, todo mundo; e grava no histórico. */
    private fun banned(server: MinecraftServer, uuid: UUID, name: String, outcome: Outcome.Banned, reason: String, by: String) {
        val cfg = config
        val vars = mapOf("time" to Durations.format(outcome.millis), "bans" to outcome.bans, "warnings" to outcome.totalWarnings, "reason" to reason, "player" to name, "by" to by)
        server.playerList.getPlayer(uuid)?.let { Texts.send(it, cfg.messages.bannedNow, vars) }
        ChatGuardRepository.log(uuid, name, "BAN", "#${outcome.bans} · $reason · $by", outcome.millis)
        if (cfg.staff.logToConsole) LOGGER.info("{} was banned from chat for {} (ban #{}, {} accumulated warnings, {})", name, Durations.format(outcome.millis), outcome.bans, outcome.totalWarnings, by)
        if (cfg.staff.notifyBans) notifyStaff(server, cfg.messages.staffBan, vars)
        if (cfg.staff.announceBans) server.playerList.broadcastSystemMessage(Texts.render(cfg.messages.announceBan, vars), false)
    }

    private fun noticeBanned(player: ServerPlayer, state: GuardState, now: Long) {
        val tracker = trackers.getOrPut(player.uuid) { Tracker() }
        if (now - tracker.lastNotice < 1000) return
        tracker.lastNotice = now
        Texts.send(player, config.messages.bannedAttempt, mapOf("time" to Durations.format(state.banUntil - now)))
    }

    private fun notifyStaff(server: MinecraftServer, template: String, vars: Map<String, Any?>) {
        val line = Texts.render(template, vars)
        for (member in server.playerList.players) if (SbPermissions.check(member, NOTIFY, 2)) member.sendSystemMessage(line)
    }

    /** Quem está banido do chat não usa os comandos de mensagem ("ban.blocked_commands"). */
    private fun blockCommand(source: CommandSourceStack, command: String): Boolean {
        val cfg = config
        if (!cfg.enabled || !ready) return false
        val player = source.player ?: return false
        val state = states[player.uuid] ?: return false
        val now = System.currentTimeMillis()
        if (!state.bannedAt(now) || cfg.ban.blockedCommands.isEmpty()) return false
        if (SbPermissions.check(player, BYPASS, 3)) return false
        val typed = CommandMatcher.words(command)
        if (typed.isEmpty()) return false
        val names = CommandBlockService.firstNames(source.server, typed[0])
        if (cfg.ban.blockedCommands.none { CommandMatcher.matches(it, typed, names, ModuleSettings::commandName) }) return false
        noticeBanned(player, state, now)
        return true
    }

    // ---------------------------------------------------------------- ações da equipe

    /**
     * Bane [uuid] do chat. Sem [millis], usa a escada de tempos (nº do ban e avisos acumulados); com [millis], esse tempo (e conta como mais um ban).
     * Devolve o tempo aplicado.
     */
    fun manualBan(server: MinecraftServer, uuid: UUID, name: String, millis: Long?, reason: String, by: String): Long {
        val now = System.currentTimeMillis()
        val state = peek(uuid)
        val duration = millis ?: ChatGuardLogic.banDuration(config.ban, state.bans, state.totalWarnings)
        val outcome = ChatGuardLogic.applyBan(state, now, duration)
        persist(uuid, name, state)
        banned(server, uuid, name, outcome, reason, by)
        return duration
    }

    /** Tira o ban de chat. false se a pessoa não estava banida. */
    fun unban(server: MinecraftServer, uuid: UUID, name: String, by: String): Boolean {
        val state = peek(uuid)
        if (!state.bannedAt(System.currentTimeMillis())) return false
        state.banUntil = 0
        persist(uuid, name, state)
        ChatGuardRepository.log(uuid, name, "UNBAN", by)
        server.playerList.getPlayer(uuid)?.let { Texts.send(it, config.messages.unbannedYou) }
        return true
    }

    /** Zera avisos, bans e o ban de agora. O histórico fica (é o registro do que a equipe fez). */
    fun reset(uuid: UUID, name: String, by: String) {
        val state = states[uuid]
        if (state != null) {
            state.warnings = 0
            state.lastViolation = 0
            state.bans = 0
            state.totalWarnings = 0
            state.banUntil = 0
            state.lastBan = 0
            persist(uuid, name, state)
        } else {
            ChatGuardRepository.delete(uuid)
        }
        trackers.remove(uuid)
        ChatGuardRepository.log(uuid, name, "RESET", by)
    }
}
