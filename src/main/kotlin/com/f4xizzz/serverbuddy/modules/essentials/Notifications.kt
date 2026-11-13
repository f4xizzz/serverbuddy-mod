package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** O que vale para um jogador depois de juntar a config (padrões) com o que ele ajustou. */
class NotifyPrefs(val enabled: Boolean, val sound: String, val pitch: Float, val volume: Float, val afkOnly: Boolean, val onChat: Boolean)

/**
 * Aviso sonoro para quem foi citado no chat (e quando chega uma mensagem privada, um correio ou um pedido de teleporte). Cada jogador
 * ajusta o seu com /notifications; o que ele não ajustou vale o padrão de `styling.mentions` na config.
 */
object Notifications {
    private val rows = ConcurrentHashMap<UUID, EssentialsRepository.NotifyRow>()
    private val EMPTY = EssentialsRepository.NotifyRow(true, null, null, null, null, null)

    private fun row(uuid: UUID): EssentialsRepository.NotifyRow = rows.getOrPut(uuid) { EssentialsRepository.notify(uuid) ?: EMPTY }

    fun onLeave(uuid: UUID) {
        rows.remove(uuid)
    }

    fun prefs(uuid: UUID): NotifyPrefs {
        val defaults = EssentialsModule.config.styling.mentions
        val row = row(uuid)
        return NotifyPrefs(
            row.enabled, row.sound ?: defaults.sound, (row.pitch ?: defaults.pitch.toDouble()).toFloat(), (row.volume ?: defaults.volume.toDouble()).toFloat(),
            row.afkOnly ?: defaults.afkOnly, row.onChat ?: true
        )
    }

    private fun play(player: ServerPlayer, prefs: NotifyPrefs) {
        val id = ResourceLocation.tryParse(prefs.sound) ?: return
        player.playNotifySound(SoundEvent.createVariableRangeEvent(id), SoundSource.MASTER, prefs.volume, prefs.pitch)
    }

    /** Toca o aviso para [player], se ele quer receber (e, para menções do chat, [chat] = true respeita o ajuste "chat"). */
    fun notify(player: ServerPlayer, chat: Boolean = false) {
        if (!EssentialsModule.ready || !EssentialsModule.config.styling.mentions.enabled) return
        val prefs = prefs(player.uuid)
        if (!prefs.enabled || (chat && !prefs.onChat)) return
        if (prefs.afkOnly && !AfkService.isAfk(player.uuid)) return
        play(player, prefs)
    }

    private fun update(uuid: UUID, change: (EssentialsRepository.NotifyRow) -> EssentialsRepository.NotifyRow) {
        val next = change(row(uuid))
        rows[uuid] = next
        EssentialsRepository.saveNotify(uuid, next)
    }

    // ---------------------------------------------------------------- /notifications

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "notifications") {
            executes { status(it) }
            then(Commands.literal("toggle").executes { toggle(it) })
            then(Commands.literal("test").executes { test(it) })
            then(Commands.literal("reset").executes { reset(it) })
            then(Commands.literal("sound").then(Commands.argument("som", ResourceLocationArgument.id()).suggests(SuggestionProviders.AVAILABLE_SOUNDS).executes { setSound(it) }))
            then(Commands.literal("volume").then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0, 2.0)).executes { setVolume(it) }))
            then(Commands.literal("pitch").then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.5, 2.0)).executes { setPitch(it) }))
            then(Commands.literal("afkonly").then(Commands.argument("valor", BoolArgumentType.bool()).executes { setAfkOnly(it) }))
            then(Commands.literal("chat").then(Commands.argument("valor", BoolArgumentType.bool()).executes { setChat(it) }))
        }
    }

    private fun yes(value: Boolean) = if (value) "<green>yes" else "<red>no"

    private fun status(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val prefs = prefs(me.uuid)
        Msg.reply(ctx.source, "notifications_title")
        fun line(label: String, value: String) = Msg.reply(ctx.source, "notifications_line", mapOf("label" to label, "value" to value))
        line("Enabled", yes(prefs.enabled))
        line("Sound", prefs.sound)
        line("Volume", String.format(Locale.ROOT, "%.2f", prefs.volume))
        line("Pitch", String.format(Locale.ROOT, "%.2f", prefs.pitch))
        line("Only while I am AFK", yes(prefs.afkOnly))
        line("Notify me when my name is mentioned in chat", yes(prefs.onChat))
        return 1
    }

    private fun toggle(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        var now = true
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(!row.enabled, row.sound, row.pitch, row.volume, row.afkOnly, row.onChat).also { now = it.enabled } }
        Msg.reply(ctx.source, if (now) "notifications_on" else "notifications_off")
        return 1
    }

    private fun test(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        play(me, prefs(me.uuid))
        Msg.reply(ctx.source, "notifications_tested")
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        rows.remove(me.uuid)
        EssentialsRepository.deleteNotify(me.uuid)
        Msg.reply(ctx.source, "notifications_reset")
        return 1
    }

    private fun setSound(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val id = ResourceLocationArgument.getId(ctx, "som").toString()
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(row.enabled, id, row.pitch, row.volume, row.afkOnly, row.onChat) }
        play(me, prefs(me.uuid))
        Msg.reply(ctx.source, "notifications_value_set", mapOf("label" to "Sound", "value" to id))
        return 1
    }

    private fun setVolume(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val value = DoubleArgumentType.getDouble(ctx, "valor")
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(row.enabled, row.sound, row.pitch, value, row.afkOnly, row.onChat) }
        play(me, prefs(me.uuid))
        Msg.reply(ctx.source, "notifications_value_set", mapOf("label" to "Volume", "value" to String.format(Locale.ROOT, "%.2f", value)))
        return 1
    }

    private fun setPitch(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val value = DoubleArgumentType.getDouble(ctx, "valor")
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(row.enabled, row.sound, value, row.volume, row.afkOnly, row.onChat) }
        play(me, prefs(me.uuid))
        Msg.reply(ctx.source, "notifications_value_set", mapOf("label" to "Pitch", "value" to String.format(Locale.ROOT, "%.2f", value)))
        return 1
    }

    private fun setAfkOnly(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val value = BoolArgumentType.getBool(ctx, "valor")
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(row.enabled, row.sound, row.pitch, row.volume, value, row.onChat) }
        Msg.reply(ctx.source, "notifications_value_set", mapOf("label" to "Only while I am AFK", "value" to yes(value)))
        return 1
    }

    private fun setChat(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val value = BoolArgumentType.getBool(ctx, "valor")
        update(me.uuid) { row -> EssentialsRepository.NotifyRow(row.enabled, row.sound, row.pitch, row.volume, row.afkOnly, value) }
        Msg.reply(ctx.source, "notifications_value_set", mapOf("label" to "Notify me when my name is mentioned in chat", "value" to yes(value)))
        return 1
    }
}
