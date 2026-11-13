package com.f4xizzz.serverbuddy.modules.daycare

import com.cobblemon.mod.common.item.PokemonItem
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonPickerGui
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer

/** Loads the daycare config, registers `/daycare` and builds the menu. Cobblemon only. */
object DaycareRuntime {
    @Volatile
    var installed = false
        private set

    fun install(folder: String) {
        DaycareService.load(load(folder))
        installed = true
        ServerLifecycleEvents.SERVER_STARTING.register { DaycareRepository.createTable() }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        DaycareService.load(load(folder))
    }

    private fun load(folder: String): DaycareConfig = ConfigFiles.load(folder, "config.json", DaycareConfig::class.java) { DaycareConfig() }

    // ---------------------------------------------------------------- menu

    class DaycareGui(player: ServerPlayer) : SimpleGui(DaycareService.config.menu.type.type, player, false) {
        private var ticks = 0

        init {
            refresh()
        }

        override fun onTick() {
            super.onTick()
            if (++ticks % 40 == 0) refresh()
        }

        fun refresh() {
            val config = DaycareService.config
            val menu = config.menu
            for (slot in 0 until virtualSize) clearSlot(slot)
            val filler = menu.filler.build()
            if (!filler.isEmpty) for (slot in 0 until virtualSize) setSlot(slot, GuiElementBuilder.from(filler).build())

            val deposits = DaycareRepository.deposits(player.uuid)
            val (capacity, price) = DaycareService.allowance(player)
            val shown = maxOf(capacity, (deposits.maxOfOrNull { it.slot } ?: -1) + 1)
            val priceVars = mapOf("price" to com.f4xizzz.serverbuddy.core.Economy.format(price), "symbol" to config.currencySymbol)

            for ((index, slotPos) in menu.slots.withIndex()) {
                val deposit = deposits.firstOrNull { it.slot == index }
                when {
                    deposit != null -> putDeposit(slotPos, deposit)
                    index >= shown -> menu.locked.build().takeIf { !it.isEmpty }?.let { putSafe(slotPos, GuiElementBuilder.from(it).build()) }
                    else -> menu.free.build(priceVars).takeIf { !it.isEmpty }?.let { stack ->
                        putSafe(slotPos, GuiElementBuilder.from(stack).setCallback { _ -> openPicker() }.build())
                    }
                }
            }
            val vars = mapOf("used" to deposits.size, "capacity" to capacity)
            putButton(menu.close, true, vars) { close() }
            setTitle(Texts.render(menu.title, vars))
        }

        private fun putDeposit(slotPos: Int, deposit: Deposit) {
            val menu = DaycareService.config.menu
            val pokemon = PokemonTools.fromJson(deposit.pokemonJson, player) ?: return
            val vars = DaycareService.vars(deposit, player, pokemon)
            val styled = menu.occupied.build(vars)
            val stack = PokemonItem.from(pokemon)
            stack.set(DataComponents.CUSTOM_NAME, styled.get(DataComponents.ITEM_NAME))
            stack.set(DataComponents.LORE, styled.get(DataComponents.LORE))
            putSafe(slotPos, GuiElementBuilder.from(stack).setCallback { _ ->
                DaycareService.withdraw(player, deposit.slot)
                refresh()
            }.build())
        }

        private fun openPicker() {
            val config = DaycareService.config
            val (_, price) = DaycareService.allowance(player)
            PokemonPickerGui(
                player, config.picker,
                refuse = { DaycareService.checkDeposit(player, it) },
                extra = { mapOf("price" to com.f4xizzz.serverbuddy.core.Economy.format(price), "symbol" to config.currencySymbol) },
                onPick = { pokemon ->
                    DaycareService.deposit(player, pokemon)
                    DaycareGui(player).open()
                }
            ).open()
        }
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>(DaycareService.USE, true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>(DaycareService.ADMIN, 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "daycare") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("daycare").build(), "daycare")
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE).executes { open(it) }
            .then(
                Commands.literal("admin").requires(ADMIN).then(
                    Commands.literal("list").then(Commands.argument("player", StringArgumentType.word()).suggests(online).executes { list(it) })
                )
            )

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) {
            Texts.fail(ctx.source, DaycareService.config.messages.onlyPlayers)
            return 0
        }
        if (!DaycareService.config.enabled) return 0
        DaycareGui(player).open()
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val messages = DaycareService.config.messages
        val name = StringArgumentType.getString(ctx, "player")
        val server = ctx.source.server
        val uuid = server.playerList.getPlayerByName(name)?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id
        if (uuid == null) {
            Texts.fail(ctx.source, messages.unknownPlayer, mapOf("player" to Texts.escape(name)))
            return 0
        }
        val deposits = DaycareRepository.deposits(uuid)
        if (deposits.isEmpty()) {
            Texts.reply(ctx.source, messages.adminEmpty)
            return 0
        }
        Texts.reply(ctx.source, messages.adminHeader, mapOf("player" to Texts.escape(name), "count" to deposits.size))
        for (deposit in deposits) {
            val species = runCatching { JsonParser.parseString(deposit.pokemonJson).asJsonObject.get("species").asString }.getOrDefault("?")
            Texts.reply(ctx.source, messages.adminLine, mapOf("slot" to deposit.slot + 1, "species" to Texts.escape(species), "level" to DaycareService.currentLevel(deposit), "gained" to DaycareService.levelsGained(deposit)))
        }
        return deposits.size
    }
}
