package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * Leaderboards of time played, money (per Impactor currency) and Pokédex progress: chat lists, a head menu and placeholders.
 * See [LeaderboardsConfig].
 */
class LeaderboardsModule : SbModule {
    override val id = "leaderboards"
    override val folder = "Leaderboards"
    override val legacyFolders = emptyList<String>()
    override val description = "Top lists: time played, money (one board per Impactor currency) and Pokédex (/leaderboard, placeholders)."

    override fun init() {
        LeaderboardService.load(loadConfig())
        ServerLifecycleEvents.SERVER_STARTING.register {
            LeaderboardRepository.createTable()
            LeaderboardService.ready = true
        }
        // the economy registers its currencies late: build the first lists once the server is up
        ServerLifecycleEvents.SERVER_STARTED.register { server -> LeaderboardService.refresh(server) }
        ServerLifecycleEvents.SERVER_STOPPED.register { LeaderboardService.ready = false }
        ServerTickEvents.END_SERVER_TICK.register { server -> LeaderboardService.tick(server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> LeaderboardService.onLeave(handler.player) }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }

        SbPlaceholders.register("lb") { player, argument -> LeaderboardService.resolvePlaceholder(player?.uuid, argument) }
    }

    override fun reload() {
        LeaderboardService.load(loadConfig())
    }

    private fun loadConfig(): LeaderboardsConfig = ConfigFiles.load(folder, "config.json", LeaderboardsConfig::class.java) { LeaderboardsConfig() }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>("serverbuddy.leaderboards.use", true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>("serverbuddy.leaderboards.admin", 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val node = SbCommands.register(dispatcher, "leaderboard") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("leaderboard").build(), "leaderboards")
        SbCommands.alias(dispatcher, "lb", node)
        SbCommands.alias(dispatcher, "top", node)
    }

    private val boardIds = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(LeaderboardService.boards.keys, builder)
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE)
            .executes { list(it) }
            .then(Commands.literal("gui").executes { gui(it, null) }.then(Commands.argument("board", StringArgumentType.word()).suggests(boardIds).executes { gui(it, StringArgumentType.getString(it, "board")) }))
            .then(Commands.literal("currencies").requires(ADMIN).executes { currencies(it) })
            .then(Commands.literal("refresh").requires(ADMIN).executes { ctx ->
                LeaderboardService.refresh(ctx.source.server)
                Texts.reply(ctx.source, "<green>Rebuilding the leaderboards in the background.")
                1
            })
            .then(Commands.argument("board", StringArgumentType.word()).suggests(boardIds).executes { show(it, StringArgumentType.getString(it, "board")) })

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val messages = LeaderboardService.config.messages
        if (!LeaderboardService.isOn()) {
            Texts.fail(ctx.source, messages.moduleOff)
            return 0
        }
        Texts.reply(ctx.source, messages.listHeader)
        for ((id, board) in LeaderboardService.boards) Texts.reply(ctx.source, messages.listLine, mapOf("board" to id, "title" to board.title))
        return LeaderboardService.boards.size
    }

    private fun show(ctx: CommandContext<CommandSourceStack>, id: String): Int {
        val config = LeaderboardService.config
        val messages = config.messages
        val board = LeaderboardService.board(id)
        if (board == null) {
            Texts.fail(ctx.source, messages.unknownBoard, mapOf("board" to Texts.escape(id)))
            return 0
        }
        val snapshot = LeaderboardService.snapshot(id.lowercase(Locale.ROOT))
        Texts.reply(ctx.source, messages.header, mapOf("title" to board.title, "board" to id))
        val entries = snapshot?.entries.orEmpty()
        if (entries.isEmpty()) {
            Texts.reply(ctx.source, messages.empty)
            return 0
        }
        for ((index, entry) in entries.take(config.size).withIndex()) {
            Texts.reply(ctx.source, messages.line, mapOf("position" to index + 1, "player" to Texts.escape(entry.name), "value" to LeaderboardService.formatValue(board, entry.value)))
        }
        val player = ctx.source.entity as? ServerPlayer
        val rank = player?.let { snapshot?.ranks?.get(it.uuid) }
        if (player != null && rank != null && rank > config.size) {
            val value = entries[rank - 1].value
            Texts.reply(ctx.source, messages.yourRank, mapOf("rank" to rank, "value" to LeaderboardService.formatValue(board, value)))
        }
        return entries.size.coerceAtMost(config.size)
    }

    private fun gui(ctx: CommandContext<CommandSourceStack>, id: String?): Int {
        val messages = LeaderboardService.config.messages
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) {
            Texts.fail(ctx.source, messages.onlyPlayers)
            return 0
        }
        val boardId = (id ?: LeaderboardService.boards.keys.firstOrNull())?.lowercase(Locale.ROOT)
        if (boardId == null || LeaderboardService.board(boardId) == null) {
            Texts.fail(ctx.source, messages.unknownBoard, mapOf("board" to Texts.escape(id ?: "")))
            return 0
        }
        LeaderboardGui(player, boardId).open()
        return 1
    }

    private fun currencies(ctx: CommandContext<CommandSourceStack>): Int {
        if (!Integrations.isLoaded("impactor")) {
            Texts.reply(ctx.source, "<gray>The Impactor economy is not installed.")
            return 0
        }
        val currencies = ImpactorBalances.currencies()
        Texts.reply(ctx.source, "<gold>Impactor currencies <gray>(use the id in a board's \"currency\"):")
        for (currency in currencies) Texts.reply(ctx.source, " <yellow>${Texts.escape(currency.key)} <dark_gray>» <white>${Texts.escape(currency.name)}")
        return currencies.size
    }
}
