package com.f4xizzz.serverbuddy.modules.sts

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonDisplay
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonPickerGui
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType

/** Loads the STS config, registers `/sts` and builds its menus. Cobblemon only. */
object StsRuntime {
    @Volatile
    var installed = false
        private set

    fun install(folder: String) {
        StsService.load(load(folder))
        installed = true
        ServerLifecycleEvents.SERVER_STARTING.register { StsRepository.createTable() }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        StsService.load(load(folder))
    }

    private fun load(folder: String): StsConfig = ConfigFiles.load(folder, "config.json", StsConfig::class.java) { StsConfig() }

    // ---------------------------------------------------------------- menus

    fun openPicker(player: ServerPlayer) {
        val config = StsService.config
        PokemonPickerGui(
            player, config.menu,
            refuse = { StsService.check(player, it, release = false) },
            extra = { StsService.vars(it, player).filterKeys { key -> key in setOf("price", "release_price", "symbol") } },
            onPick = { openConfirm(player, it) }
        ).open()
    }

    private fun openConfirm(player: ServerPlayer, pokemon: Pokemon) {
        val config = StsService.config
        val vars = StsService.vars(pokemon, player)
        val menu = config.confirm
        val gui = object : SimpleGui(MenuType.GENERIC_9x3, player, false) {}
        val filler = menu.filler.build()
        if (!filler.isEmpty) for (slot in 0 until gui.virtualSize) gui.setSlot(slot, GuiElementBuilder.from(filler).build())
        gui.putSafe(menu.iconSlot, GuiElementBuilder.from(PokemonDisplay.item(pokemon, config.menu, vars.filterKeys { it in setOf("price", "release_price", "symbol") })).build())
        gui.putButton(menu.sell, true, vars) {
            gui.close()
            StsService.sell(player, pokemon, release = false)
        }
        gui.putButton(menu.release, config.release.enabled, vars) {
            gui.close()
            StsService.sell(player, pokemon, release = true)
        }
        gui.putButton(menu.cancel, true, vars) { openPicker(player) }
        gui.setTitle(Texts.render(menu.title, vars))
        gui.open()
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>(StsService.USE, true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>(StsService.ADMIN, 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "sts") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("sts").build(), "sts")
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE)
            .executes { open(it) }
            .then(
                Commands.literal("price").executes { price(it, 1) }
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes { price(it, IntegerArgumentType.getInteger(it, "slot")) })
            )
            .then(
                Commands.literal("admin").requires(ADMIN).then(
                    Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word()).suggests(online).executes { reset(it) })
                )
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) Texts.fail(ctx.source, StsService.config.messages.onlyPlayers)
        return player
    }

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        if (!StsService.config.enabled) return 0
        if (!StsService.economyReady()) {
            Texts.fail(ctx.source, StsService.config.messages.noEconomy)
            return 0
        }
        openPicker(player)
        return 1
    }

    private fun price(ctx: CommandContext<CommandSourceStack>, slot: Int): Int {
        val player = player(ctx) ?: return 0
        val pokemon = Cobblemon.storage.getParty(player).get(slot - 1)
        if (pokemon == null) {
            Texts.fail(ctx.source, StsService.config.messages.noPokemon)
            return 0
        }
        Texts.reply(ctx.source, StsService.config.messages.priceLine, StsService.vars(pokemon, player))
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val server = ctx.source.server
        val uuid = server.playerList.getPlayerByName(name)?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id
        if (uuid == null) {
            Texts.fail(ctx.source, StsService.config.messages.unknownPlayer, mapOf("player" to Texts.escape(name)))
            return 0
        }
        StsService.resetCooldown(uuid)
        Texts.reply(ctx.source, StsService.config.messages.adminReset, mapOf("player" to Texts.escape(name)))
        return 1
    }
}
