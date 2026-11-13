package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID

/**
 * A monthly calendar, a weekly calendar and a login streak, each a chest menu of reward days (everything editable in
 * `config/ServerBuddy/Calendar`). Players use `/calendar` and `/streak`.
 */
class CalendarModule : SbModule {
    override val id = "calendar"
    override val folder = CalendarService.FOLDER
    override val legacyFolders = emptyList<String>()
    override val description = "Monthly and weekly reward calendars and a login streak, all configurable (/calendar, /streak)."

    override fun init() {
        CalendarService.load()
        ServerLifecycleEvents.SERVER_STARTING.register {
            CalendarRepository.createTables()
            CalendarService.ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { CalendarService.ready = false }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> onJoin(handler.player) }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }

        // %serverbuddy:streak%, %serverbuddy:streak_best% and %serverbuddy:calendar_ready% (rewards waiting)
        SbPlaceholders.register("streak") { player, _ -> player?.let { CalendarService.streakOf(it.uuid).streak.toString() } ?: "0" }
        SbPlaceholders.register("streak_best") { player, _ -> player?.let { CalendarService.streakOf(it.uuid).best.toString() } ?: "0" }
        SbPlaceholders.register("calendar_ready") { player, _ -> player?.let { CalendarService.totalClaimable(it.uuid).toString() } ?: "0" }
    }

    override fun reload() {
        CalendarService.load()
    }

    private fun onJoin(player: ServerPlayer) {
        if (!CalendarService.ready || !CalendarService.config.enabled) return
        CalendarService.onJoin(player)
        val reminder = CalendarService.config.reminder
        ServerScheduler.later(reminder.delayTicks.coerceAtLeast(1)) {
            if (player.hasDisconnected()) return@later
            val streak = CalendarService.streak
            if (streak.openOnJoin && CalendarService.isOn(CalendarView.STREAK) && CalendarService.claimableCount(player.uuid, CalendarView.STREAK) > 0) {
                CalendarGui(player, CalendarView.STREAK).open()
                return@later
            }
            if (!reminder.onJoin) return@later
            val ready = CalendarService.totalClaimable(player.uuid)
            if (ready > 0) Texts.send(player, CalendarService.config.messages.reminder, mapOf("count" to ready))
        }
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>("serverbuddy.calendar.use", true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>("serverbuddy.calendar.admin", 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "calendar") { name -> calendarNode(name) }
        ModuleCommands.mount(dispatcher, calendarNode("calendar").build(), "calendar")
        SbCommands.register(dispatcher, "streak") { name ->
            Commands.literal(name).requires(USE).executes { open(it, CalendarView.STREAK) }
        }
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun calendarNode(name: String): LiteralArgumentBuilder<CommandSourceStack> {
        val node = Commands.literal(name).requires(USE).executes { open(it, CalendarView.parse(CalendarService.config.defaultView) ?: CalendarView.MONTHLY) }
        for (view in CalendarView.entries) node.then(Commands.literal(view.id).executes { open(it, view) })
        node.then(
            Commands.literal("admin").requires(ADMIN)
                .then(
                    Commands.literal("reset").then(
                        Commands.argument("player", StringArgumentType.word()).suggests(online).then(
                            Commands.argument("what", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(listOf("monthly", "weekly", "streak", "all"), builder) }
                                .executes { reset(it) }
                        )
                    )
                )
                .then(
                    Commands.literal("streak").then(
                        Commands.argument("player", StringArgumentType.word()).suggests(online).then(
                            Commands.argument("days", IntegerArgumentType.integer(0, 100000)).executes { setStreak(it) }
                        )
                    )
                )
                .then(
                    Commands.literal("clock").then(
                        Commands.argument("date", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(listOf("reset"), builder) }.executes { clock(it) }
                    )
                )
        )
        return node
    }

    private fun open(ctx: CommandContext<CommandSourceStack>, view: CalendarView): Int {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can open the calendar.")
            return 0
        }
        if (!CalendarService.isOn(view)) {
            Texts.fail(ctx.source, CalendarService.config.messages.moduleOff)
            return 0
        }
        CalendarGui(player, view).open()
        return 1
    }

    private fun resolve(ctx: CommandContext<CommandSourceStack>, name: String): UUID? {
        val server = ctx.source.server
        server.playerList.getPlayerByName(name)?.let { return it.uuid }
        return server.profileCache?.get(name)?.orElse(null)?.id
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val what = StringArgumentType.getString(ctx, "what").lowercase()
        val uuid = resolve(ctx, name)
        if (uuid == null) {
            Texts.fail(ctx.source, "<red>Unknown player: <white>${Texts.escape(name)}")
            return 0
        }
        val targets = if (what == "all") CalendarView.entries.map { it.id } else listOfNotNull(CalendarView.parse(what)?.id)
        if (targets.isEmpty()) {
            Texts.fail(ctx.source, "<red>Use monthly, weekly, streak or all.")
            return 0
        }
        for (target in targets) CalendarService.reset(uuid, target)
        Texts.reply(ctx.source, "<green>Reset <white>${targets.joinToString(", ")}</white> for <white>${Texts.escape(name)}</white>.")
        return 1
    }

    private fun setStreak(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val uuid = resolve(ctx, name)
        if (uuid == null) {
            Texts.fail(ctx.source, "<red>Unknown player: <white>${Texts.escape(name)}")
            return 0
        }
        val days = IntegerArgumentType.getInteger(ctx, "days")
        CalendarService.setStreak(uuid, days)
        Texts.reply(ctx.source, "<green>The login streak of <white>${Texts.escape(name)}</white> is now <white>$days</white> day(s).")
        return 1
    }

    private fun clock(ctx: CommandContext<CommandSourceStack>): Int {
        val text = StringArgumentType.getString(ctx, "date")
        if (text.equals("reset", ignoreCase = true)) {
            CalendarService.clockOverride = null
            Texts.reply(ctx.source, "<green>The calendar follows the real date again (<white>${CalendarService.today()}</white>).")
            return 1
        }
        val date = runCatching { LocalDate.parse(text) }.getOrNull()
        if (date == null) {
            Texts.fail(ctx.source, "<red>Use a date like 2026-12-24, or <white>reset</white>.")
            return 0
        }
        CalendarService.clockOverride = date
        Texts.reply(ctx.source, "<yellow>The calendar now believes today is <white>$date</white> (until restart or <white>reset</white>).")
        return 1
    }
}
