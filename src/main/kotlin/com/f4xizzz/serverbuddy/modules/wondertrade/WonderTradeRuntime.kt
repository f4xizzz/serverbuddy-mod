package com.f4xizzz.serverbuddy.modules.wondertrade

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmGui
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonDisplay
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonPickerGui
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer

/** Loads the Wonder Trade config, registers `/wondertrade` (and `/wt`) and builds its menus. Cobblemon only. */
object WonderTradeRuntime {
    @Volatile
    var installed = false
        private set

    fun install(folder: String) {
        WonderTradeService.load(load(folder))
        installed = true
        ServerLifecycleEvents.SERVER_STARTING.register { WonderTradeRepository.createTables() }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        WonderTradeService.load(load(folder))
    }

    private fun load(folder: String): WonderTradeConfig = ConfigFiles.load(folder, "config.json", WonderTradeConfig::class.java) { WonderTradeConfig() }

    // ---------------------------------------------------------------- menus

    fun openPicker(player: ServerPlayer) {
        val config = WonderTradeService.config
        PokemonPickerGui(
            player, config.menu,
            refuse = { WonderTradeService.check(player, it) },
            onPick = { openConfirm(player, it) }
        ).open()
    }

    private fun openConfirm(player: ServerPlayer, pokemon: Pokemon) {
        val config = WonderTradeService.config
        val menu = config.confirm
        val icon = PokemonDisplay.item(pokemon, config.menu)
        ConfirmGui(
            player, ConfirmSettings(menu.confirm, menu.cancel, menu.iconSlot, menu.filler), menu.title, icon, PokemonVars.of(pokemon, config.menu.shinyPrefix),
            onConfirm = {
                player.closeContainer()
                WonderTradeService.trade(player, pokemon)
            },
            onCancel = { openPicker(player) }
        ).open()
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>(WonderTradeService.USE, true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>(WonderTradeService.ADMIN, 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = SbCommands.register(dispatcher, "wondertrade") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("wondertrade").build(), "wondertrade")
        SbCommands.alias(dispatcher, "wt", root)
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE).executes { open(it) }
            .then(Commands.literal("history").executes { history(it) })
            .then(
                Commands.literal("admin").requires(ADMIN)
                    .then(Commands.literal("pool").executes { pool(it) })
                    .then(Commands.literal("clear").executes { ctx ->
                        Texts.reply(ctx.source, WonderTradeService.config.messages.adminCleared, mapOf("count" to WonderTradeRepository.clear()))
                        1
                    })
                    .then(Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word()).suggests(online).executes { reset(it) }))
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) Texts.fail(ctx.source, WonderTradeService.config.messages.onlyPlayers)
        return player
    }

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        if (!WonderTradeService.config.enabled) return 0
        openPicker(player)
        return 1
    }

    private fun history(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        val messages = WonderTradeService.config.messages
        val records = WonderTradeRepository.history(player.uuid, WonderTradeService.config.historySize)
        if (records.isEmpty()) {
            Texts.reply(ctx.source, messages.historyEmpty)
            return 0
        }
        Texts.reply(ctx.source, messages.historyHeader)
        for (record in records) {
            Texts.reply(ctx.source, messages.historyLine, mapOf("gave" to Texts.escape(record.gave), "got" to Texts.escape(record.got), "level" to record.level, "ago" to Durations.format(System.currentTimeMillis() - record.time)))
        }
        return records.size
    }

    private fun pool(ctx: CommandContext<CommandSourceStack>): Int {
        Texts.reply(ctx.source, WonderTradeService.config.messages.adminPool, mapOf("size" to WonderTradeRepository.poolSize()))
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val server = ctx.source.server
        val uuid = server.playerList.getPlayerByName(name)?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id
        if (uuid == null) {
            Texts.fail(ctx.source, WonderTradeService.config.messages.unknownPlayer, mapOf("player" to Texts.escape(name)))
            return 0
        }
        WonderTradeService.resetCooldown(uuid)
        Texts.reply(ctx.source, WonderTradeService.config.messages.adminReset, mapOf("player" to Texts.escape(name)))
        return 1
    }
}
