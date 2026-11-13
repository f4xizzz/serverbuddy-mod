package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.suggestWords
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.text
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.world.BossEvent

/**
 * Mais comandos da equipe, do Solstice: /checkjail, /doas, /powertool, /cooldown e /timebar. As mecânicas ficam em [PowerTools],
 * [CommandCooldowns] e [TimeBars]; aqui só os comandos.
 */
object StaffExtras {
    private val ONLINE = suggestWords { ServerHolder.server?.playerList?.playerNamesArray?.toList() ?: emptyList() }
    private val GROUPS = suggestWords { EssentialsModule.config.cooldowns.groups.keys }
    private val ACTIONS = suggestWords { PowerAction.entries.map { it.label } }
    private val COLORS = suggestWords { BossEvent.BossBarColor.entries.map { it.getName() } }
    private val STYLES = suggestWords { BossEvent.BossBarOverlay.entries.map { it.getName() } }
    private val DIRECTIONS = suggestWords { listOf("down", "up") }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "checkjail", level = 2) { then(word("jogador").suggests(ONLINE).executes { checkJail(it) }) }
        register(dispatcher, "doas", level = 3) { then(EssentialsCommands.playerArg().then(text("comando").executes { doAs(it) })) }
        register(dispatcher, "powertool", aliases = listOf("ptool"), level = 2) {
            executes { powerList(it) }
            then(Commands.literal("list").executes { powerList(it) })
            then(Commands.literal("toggle").executes { powerToggle(it) })
            then(Commands.literal("clear").executes { powerClear(it, null) }.then(word("acao").suggests(ACTIONS).executes { powerClear(it, StringArgumentType.getString(it, "acao")) }))
            then(Commands.literal("set").then(word("acao").suggests(ACTIONS).then(text("comando").executes { powerSet(it) })))
        }
        register(dispatcher, "cooldown", level = 3) {
            then(Commands.literal("list").executes { cooldownList(it) })
            then(
                Commands.literal("check").then(
                    word("jogador").suggests(ONLINE).executes { cooldownCheck(it, null) }
                        .then(word("grupo").suggests(GROUPS).executes { cooldownCheck(it, StringArgumentType.getString(it, "grupo")) })
                )
            )
            then(
                Commands.literal("clear").then(
                    word("jogador").suggests(ONLINE).executes { cooldownClear(it, null) }
                        .then(word("grupo").suggests(GROUPS).executes { cooldownClear(it, StringArgumentType.getString(it, "grupo")) })
                )
            )
        }
        register(dispatcher, "timebar", level = 3) {
            then(Commands.literal("list").executes { timeBarList(it) })
            then(
                Commands.literal("start").then(
                    word("tempo").then(
                        Commands.argument("texto", StringArgumentType.string()).executes { timeBarStart(it, "purple", "progress", "down", false) }
                            .then(text("comando").executes { timeBarStart(it, "purple", "progress", "down", true) })
                    )
                )
            )
            then(
                Commands.literal("custom").then(
                    word("tempo").then(
                        word("cor").suggests(COLORS).then(
                            word("estilo").suggests(STYLES).then(
                                word("sentido").suggests(DIRECTIONS).then(
                                    Commands.argument("texto", StringArgumentType.string()).executes { timeBarCustom(it, false) }
                                        .then(text("comando").executes { timeBarCustom(it, true) })
                                )
                            )
                        )
                    )
                )
            )
            then(
                Commands.literal("cancel")
                    .then(Commands.literal("all").executes { timeBarCancelAll(it) })
                    .then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes { timeBarCancel(it) })
            )
        }
    }

    // ---------------------------------------------------------------- /checkjail e /doas

    private fun checkJail(ctx: CommandContext<CommandSourceStack>): Int {
        val wanted = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
            return 0
        }
        val record = JailService.recordOf(found.first)
        if (record == null) {
            Msg.reply(ctx.source, "checkjail_not", mapOf("player" to found.second))
            return 0
        }
        Msg.reply(ctx.source, "checkjail_title", mapOf("player" to found.second))
        Msg.reply(ctx.source, "checkjail_cell", mapOf("jail" to record.jail))
        EssentialsRepository.jailInfo(found.first)?.let { (by, at) ->
            Msg.reply(ctx.source, "checkjail_by", mapOf("by" to Texts.escape(by), "when" to ChatService.whenText(at)))
        }
        val time = if (record.until == Long.MAX_VALUE) "forever" else "faltam ${Durations.format(record.until - System.currentTimeMillis())}"
        Msg.reply(ctx.source, "checkjail_time", mapOf("time" to time))
        record.reason?.let { Msg.reply(ctx.source, "checkjail_reason", mapOf("reason" to Texts.escape(it))) }
        return 1
    }

    /** Roda o comando como [alvo] (nome, posição e mundo dele), mas com o nível de permissão de quem digitou o /doas. */
    private fun doAs(ctx: CommandContext<CommandSourceStack>): Int {
        val victim = EntityArgument.getPlayer(ctx, "jogador")
        val command = StringArgumentType.getString(ctx, "comando").removePrefix("/")
        val level = (4 downTo 0).firstOrNull { ctx.source.hasPermission(it) } ?: 0
        Msg.reply(ctx.source, "doas_done", mapOf("player" to victim.gameProfile.name, "command" to Texts.escape(command)))
        ctx.source.server.commands.performPrefixedCommand(victim.createCommandSourceStack().withPermission(level), command)
        return 1
    }

    // ---------------------------------------------------------------- /powertool

    private fun powerList(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val all = PowerTools.of(me.uuid)
        if (all.isEmpty() || all.values.all { it.isEmpty() }) {
            Msg.reply(ctx.source, "powertool_none")
            return 0
        }
        Msg.reply(ctx.source, "powertool_header")
        var lines = 0
        for ((item, actions) in all.toSortedMap()) {
            for ((action, command) in actions.toSortedMap(compareBy { it.ordinal })) {
                Msg.reply(ctx.source, "powertool_line", mapOf("item" to item, "action" to action.label, "command" to Texts.escape(command)))
                lines++
            }
        }
        return lines
    }

    private fun heldItem(ctx: CommandContext<CommandSourceStack>): Pair<net.minecraft.server.level.ServerPlayer, String>? {
        val me = EssentialsCommands.player(ctx) ?: return null
        val stack = me.mainHandItem
        if (stack.isEmpty) {
            Msg.fail(ctx.source, "powertool_no_item")
            return null
        }
        return me to PowerTools.itemId(stack)
    }

    private fun powerSet(ctx: CommandContext<CommandSourceStack>): Int {
        val (me, item) = heldItem(ctx) ?: return 0
        val rawAction = StringArgumentType.getString(ctx, "acao")
        val action = PowerAction.parse(rawAction)
        if (action == null) {
            Msg.fail(ctx.source, "powertool_bad_action", mapOf("action" to Texts.escape(rawAction), "actions" to PowerAction.entries.joinToString(", ") { it.label }))
            return 0
        }
        val command = StringArgumentType.getString(ctx, "comando").trim().removePrefix("/")
        if (command.isEmpty()) {
            Msg.fail(ctx.source, "powertool_bad_action", mapOf("action" to Texts.escape(rawAction), "actions" to PowerAction.entries.joinToString(", ") { it.label }))
            return 0
        }
        PowerTools.set(me.uuid, item, action, command)
        Msg.reply(ctx.source, "powertool_set", mapOf("item" to item, "action" to action.label, "command" to Texts.escape(command)))
        return 1
    }

    private fun powerClear(ctx: CommandContext<CommandSourceStack>, rawAction: String?): Int {
        val (me, item) = heldItem(ctx) ?: return 0
        val action = rawAction?.let { PowerAction.parse(it) }
        if (rawAction != null && action == null) {
            Msg.fail(ctx.source, "powertool_bad_action", mapOf("action" to Texts.escape(rawAction), "actions" to PowerAction.entries.joinToString(", ") { it.label }))
            return 0
        }
        if (!PowerTools.clear(me.uuid, item, action)) {
            Msg.fail(ctx.source, "powertool_nothing", mapOf("item" to item))
            return 0
        }
        Msg.reply(ctx.source, "powertool_removed", mapOf("item" to item))
        return 1
    }

    private fun powerToggle(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        Msg.reply(ctx.source, if (PowerTools.toggle(me.uuid)) "powertool_toggle_on" else "powertool_toggle_off")
        return 1
    }

    // ---------------------------------------------------------------- /cooldown

    private fun cooldownList(ctx: CommandContext<CommandSourceStack>): Int {
        val settings = EssentialsModule.config.cooldowns
        if (settings.groups.isEmpty()) {
            Msg.reply(ctx.source, "cooldown_list_empty")
            return 0
        }
        Msg.reply(ctx.source, "cooldown_list_header", mapOf("state" to if (settings.enabled) "<green>ons" else "<red>desligadas"))
        for ((key, group) in settings.groups) {
            Msg.reply(
                ctx.source, "cooldown_list_line",
                mapOf(
                    "group" to Texts.escape(key), "seconds" to group.seconds, "commands" to Texts.escape(group.commands.joinToString(", ")),
                    "state" to if (group.enabled) "<green>✔" else "<red>✖"
                )
            )
        }
        return settings.groups.size
    }

    private fun resolveTarget(ctx: CommandContext<CommandSourceStack>): Pair<java.util.UUID, String>? {
        val wanted = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
        return found
    }

    private fun groupOrFail(ctx: CommandContext<CommandSourceStack>, key: String): String? {
        val real = EssentialsModule.config.cooldowns.groups.keys.firstOrNull { it.equals(key, ignoreCase = true) }
        if (real == null) Msg.fail(ctx.source, "cooldown_unknown_group", mapOf("group" to Texts.escape(key), "groups" to Texts.escape(EssentialsModule.config.cooldowns.groups.keys.joinToString(", "))))
        return real
    }

    private fun cooldownCheck(ctx: CommandContext<CommandSourceStack>, rawGroup: String?): Int {
        val (uuid, name) = resolveTarget(ctx) ?: return 0
        if (rawGroup != null) {
            val group = groupOrFail(ctx, rawGroup) ?: return 0
            val left = CommandCooldowns.left(uuid, group)
            if (left <= 0) Msg.reply(ctx.source, "cooldown_free", mapOf("player" to name, "group" to group))
            else Msg.reply(ctx.source, "cooldown_check_line", mapOf("player" to name, "group" to group, "time" to Durations.format(left)))
            return if (left > 0) 1 else 0
        }
        val active = CommandCooldowns.active(uuid)
        if (active.isEmpty()) {
            Msg.reply(ctx.source, "cooldown_check_none", mapOf("player" to name))
            return 0
        }
        for ((group, left) in active) Msg.reply(ctx.source, "cooldown_check_line", mapOf("player" to name, "group" to group, "time" to Durations.format(left)))
        return active.size
    }

    private fun cooldownClear(ctx: CommandContext<CommandSourceStack>, rawGroup: String?): Int {
        val (uuid, name) = resolveTarget(ctx) ?: return 0
        val groups = if (rawGroup != null) listOf(groupOrFail(ctx, rawGroup) ?: return 0) else CommandCooldowns.active(uuid).keys.toList()
        var cleared = 0
        for (group in groups) if (CommandCooldowns.clear(uuid, group)) cleared++
        if (cleared == 0) {
            Msg.reply(ctx.source, "cooldown_check_none", mapOf("player" to name))
            return 0
        }
        Msg.reply(ctx.source, "cooldown_cleared", mapOf("player" to name, "count" to cleared), broadcast = true)
        return cleared
    }

    // ---------------------------------------------------------------- /timebar

    private fun timeBarStart(ctx: CommandContext<CommandSourceStack>, color: String, style: String, direction: String, withCommand: Boolean): Int =
        startBar(ctx, color, style, direction, withCommand)

    private fun timeBarCustom(ctx: CommandContext<CommandSourceStack>, withCommand: Boolean): Int =
        startBar(
            ctx, StringArgumentType.getString(ctx, "cor"), StringArgumentType.getString(ctx, "estilo"),
            StringArgumentType.getString(ctx, "sentido"), withCommand
        )

    private fun startBar(ctx: CommandContext<CommandSourceStack>, rawColor: String, rawStyle: String, rawDirection: String, withCommand: Boolean): Int {
        val millis = Durations.parseMillis(StringArgumentType.getString(ctx, "tempo"))
        if (millis == null || millis < 1000) {
            Msg.fail(ctx.source, "bad_time")
            return 0
        }
        val color = BossEvent.BossBarColor.entries.firstOrNull { it.getName().equals(rawColor, ignoreCase = true) }
        if (color == null) {
            Msg.fail(ctx.source, "timebar_bad_option", mapOf("option" to Texts.escape(rawColor), "values" to BossEvent.BossBarColor.entries.joinToString(", ") { it.getName() }))
            return 0
        }
        val style = BossEvent.BossBarOverlay.entries.firstOrNull { it.getName().equals(rawStyle, ignoreCase = true) }
        if (style == null) {
            Msg.fail(ctx.source, "timebar_bad_option", mapOf("option" to Texts.escape(rawStyle), "values" to BossEvent.BossBarOverlay.entries.joinToString(", ") { it.getName() }))
            return 0
        }
        val countdown = when (rawDirection.lowercase()) {
            "down", "countdown" -> true
            "up", "countup" -> false
            else -> {
                Msg.fail(ctx.source, "timebar_bad_option", mapOf("option" to Texts.escape(rawDirection), "values" to "down, up"))
                return 0
            }
        }
        val label = StringArgumentType.getString(ctx, "texto")
        val command = if (withCommand) StringArgumentType.getString(ctx, "comando").trim().removePrefix("/").ifEmpty { null } else null
        val bar = TimeBars.start(ctx.source.server, label, (millis / 1000).toInt(), color, style, countdown, command, ctx.source)
        Msg.reply(ctx.source, "timebar_started", mapOf("id" to bar.id, "time" to Durations.format(millis)))
        return bar.id
    }

    private fun timeBarList(ctx: CommandContext<CommandSourceStack>): Int {
        val bars = TimeBars.all()
        if (bars.isEmpty()) {
            Msg.reply(ctx.source, "timebar_none")
            return 0
        }
        Msg.reply(ctx.source, "timebar_list_header", mapOf("count" to bars.size))
        for (bar in bars) {
            Msg.reply(
                ctx.source, "timebar_list_line",
                mapOf("id" to bar.id, "left" to Durations.format(bar.remaining * 1000L), "command" to Texts.escape(bar.command ?: "—"))
            )
        }
        return bars.size
    }

    private fun timeBarCancel(ctx: CommandContext<CommandSourceStack>): Int {
        val id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id")
        if (!TimeBars.cancel(id)) {
            Msg.fail(ctx.source, "timebar_unknown", mapOf("id" to id))
            return 0
        }
        Msg.reply(ctx.source, "timebar_cancelled", mapOf("id" to id))
        return 1
    }

    private fun timeBarCancelAll(ctx: CommandContext<CommandSourceStack>): Int {
        val count = TimeBars.cancelAll()
        if (count == 0) {
            Msg.fail(ctx.source, "timebar_none")
            return 0
        }
        Msg.reply(ctx.source, "timebar_cancelled_all", mapOf("count" to count))
        return count
    }
}
