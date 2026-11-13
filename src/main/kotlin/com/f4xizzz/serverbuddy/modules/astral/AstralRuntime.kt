package com.f4xizzz.serverbuddy.modules.astral

import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmGui
import com.f4xizzz.serverbuddy.modules.pokeservices.PickerSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonDisplay
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType

/** Loads the Astral Trader config, registers `/astral`, the placeholders and the rotation timer. Cobblemon only. */
object AstralRuntime {
    fun install(folder: String) {
        AstralService.load(load(folder))
        ServerLifecycleEvents.SERVER_STARTING.register { AstralRepository.createTables() }
        ServerTickEvents.END_SERVER_TICK.register { server -> if (server.tickCount % 1200 == 0) tick(server) }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
        // %serverbuddy:astral_special% and %serverbuddy:astral_shiny%: the player's tokens
        for (currency in listOf("special", "shiny")) {
            SbPlaceholders.register("astral_$currency") { player, _ -> player?.let { AstralService.balance(it.uuid, currency).toString() } }
        }
    }

    fun reload(folder: String) {
        AstralService.load(load(folder))
    }

    private fun load(folder: String): AstralConfig = ConfigFiles.load(folder, "config.json", AstralConfig::class.java) { AstralConfig() }

    /** Every minute: pools whose time is up pick new Pokémon (the very first pick, at the first boot, is silent). */
    private fun tick(server: MinecraftServer) {
        val config = AstralService.config
        if (!config.enabled) return
        val firstTime = AstralService.poolNames().any { com.f4xizzz.serverbuddy.modules.astral.AstralRepository.rotatedAt(it) == null }
        val changed = AstralService.rotateIfNeeded()
        if (changed.isNotEmpty() && !firstTime && config.announceRotation) {
            server.playerList.broadcastSystemMessage(Texts.render(config.messages.rotated), false)
        }
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>(AstralService.USE, true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>(AstralService.ADMIN, 2)

    private val online = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }
    private val currencies = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(listOf("special", "shiny"), builder) }
    private val pools = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(AstralService.poolNames(), builder) }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "astral") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("astral").build(), "astral")
    }

    private fun adjust(name: String, op: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).then(
            Commands.argument("player", StringArgumentType.word()).suggests(online).then(
                Commands.argument("currency", StringArgumentType.word()).suggests(currencies).then(
                    Commands.argument("amount", IntegerArgumentType.integer(if (op == "set") 0 else 1)).executes { change(it, op) }
                )
            )
        )

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE).executes { open(it) }
            .then(
                Commands.literal("balance").executes { balance(it, null) }
                    .then(Commands.argument("player", StringArgumentType.word()).requires(ADMIN).suggests(online).executes { balance(it, StringArgumentType.getString(it, "player")) })
            )
            .then(
                Commands.literal("admin").requires(ADMIN)
                    .then(adjust("give", "give"))
                    .then(adjust("take", "take"))
                    .then(adjust("set", "set"))
                    .then(Commands.literal("offers").executes { offers(it) })
                    .then(
                        Commands.literal("rotate").executes { rotate(it, null) }
                            .then(Commands.argument("pool", StringArgumentType.word()).suggests(pools).executes { rotate(it, StringArgumentType.getString(it, "pool")) })
                    )
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) Texts.fail(ctx.source, AstralService.config.messages.onlyPlayers)
        return player
    }

    private fun tokenVars(uuid: java.util.UUID, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf("special" to AstralService.balance(uuid, "special"), "shiny" to AstralService.balance(uuid, "shiny")) + extra

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        if (!AstralService.config.enabled) {
            Texts.fail(ctx.source, AstralService.config.messages.closed)
            return 0
        }
        AstralService.rotateIfNeeded()
        AstralGui(player).open()
        return 1
    }

    private fun balance(ctx: CommandContext<CommandSourceStack>, who: String?): Int {
        val messages = AstralService.config.messages
        if (who == null) {
            val player = player(ctx) ?: return 0
            Texts.reply(ctx.source, messages.balance, tokenVars(player.uuid))
            return 1
        }
        val uuid = AstralService.findUuid(ctx.source.server, who)
        if (uuid == null) {
            Texts.fail(ctx.source, messages.unknownPlayer, mapOf("player" to Texts.escape(who)))
            return 0
        }
        Texts.reply(ctx.source, messages.balanceOther, tokenVars(uuid, mapOf("player" to Texts.escape(who))))
        return 1
    }

    private fun change(ctx: CommandContext<CommandSourceStack>, op: String): Int {
        val messages = AstralService.config.messages
        val who = StringArgumentType.getString(ctx, "player")
        val currency = StringArgumentType.getString(ctx, "currency").lowercase()
        val amount = IntegerArgumentType.getInteger(ctx, "amount").toLong()
        if (currency != "special" && currency != "shiny") {
            Texts.fail(ctx.source, messages.unknownCurrency, mapOf("currency" to Texts.escape(currency)))
            return 0
        }
        val uuid = AstralService.findUuid(ctx.source.server, who)
        if (uuid == null) {
            Texts.fail(ctx.source, messages.unknownPlayer, mapOf("player" to Texts.escape(who)))
            return 0
        }
        val now = when (op) {
            "give" -> AstralRepository.add(uuid, currency, amount)
            "take" -> AstralRepository.add(uuid, currency, -amount)
            else -> AstralRepository.set(uuid, currency, amount)
        }
        Texts.reply(ctx.source, messages.adminChanged, mapOf("player" to Texts.escape(who), "amount" to now, "currency" to AstralService.currencyName(currency, now)))
        return 1
    }

    /** What every pool shows right now (admin view: species, level, shiny, price and the copies left). */
    private fun offers(ctx: CommandContext<CommandSourceStack>): Int {
        var total = 0
        for ((name, settings) in AstralService.config.pools.entries()) {
            val offers = AstralService.offers(name)
            total += offers.size
            val line = offers.joinToString(" | ") { offer ->
                val left = AstralService.stockLeft(offer)?.let { ", $it left" } ?: ""
                "${offer.slot}: ${offer.speciesId} lv${offer.level}${if (offer.shiny) " shiny" else ""}$left"
            }
            val reset = AstralService.msUntilReset(name).let { if (it < 0) "never" else Durations.format(it) }
            Texts.reply(ctx.source, "<gray>${Texts.escape(name)} <dark_gray>(${settings.price} ${AstralService.currencyName(settings.currency, settings.price)}, new in $reset)<gray>: <white>${Texts.escape(line.ifEmpty { "-" })}")
        }
        return total
    }

    private fun rotate(ctx: CommandContext<CommandSourceStack>, pool: String?): Int {
        val messages = AstralService.config.messages
        val targets = if (pool == null) AstralService.poolNames() else listOf(pool)
        val done = targets.filter { AstralService.pool(it)?.enabled == true && AstralService.rotate(it) }
        if (done.isEmpty()) {
            Texts.fail(ctx.source, messages.unknownPool, mapOf("pool" to Texts.escape(pool ?: "")))
            return 0
        }
        Texts.reply(ctx.source, messages.adminRotated, mapOf("pools" to done.joinToString(", ")))
        return done.size
    }
}

/** The trader's menu: the player's tokens on top, then one row per pool (a pool icon, then up to 7 offers). */
class AstralGui(player: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, player, false) {
    init {
        refresh()
    }

    private fun refresh() {
        val config = AstralService.config
        val menu = config.menu
        for (slot in 0 until virtualSize) clearSlot(slot)

        val soonest = AstralService.poolNames().map { AstralService.msUntilReset(it) }.filter { it >= 0 }.minOrNull()
        val vars = mapOf(
            "special" to AstralService.balance(player.uuid, "special"),
            "shiny" to AstralService.balance(player.uuid, "shiny"),
            "reset" to (soonest?.let { Durations.format(it) } ?: "—")
        )
        val header = menu.header.build(vars)
        if (!header.isEmpty) putSafe(menu.headerSlot, GuiElementBuilder.from(header).build())

        for ((pool, settings) in config.pools.entries()) {
            if (!settings.enabled) continue
            val row = (menu.poolRows[pool] ?: continue).coerceIn(1, 4)
            val poolVars = mapOf("price" to settings.price, "currency" to AstralService.currencyName(settings.currency, settings.price))
            menu.poolIcons[pool]?.build(poolVars)?.takeIf { !it.isEmpty }?.let { putSafe(row * 9, GuiElementBuilder.from(it).build()) }
            for ((index, offer) in AstralService.offers(pool).withIndex()) {
                if (index >= 7) break
                putOffer(row * 9 + 1 + index, settings, offer)
            }
        }

        putButton(menu.close, true, vars) { close() }
        val filler = menu.filler.build()
        if (!filler.isEmpty) for (slot in 0 until virtualSize) if (getSlot(slot) == null) setSlot(slot, GuiElementBuilder.from(filler).build())
        setTitle(Texts.render(menu.title, vars))
    }

    private fun picker(): PickerSettings {
        val menu = AstralService.config.menu
        return PickerSettings(name = menu.offerName, lore = menu.offerLore, shinyPrefix = menu.shinyPrefix)
    }

    private fun offerVars(settings: PoolSettings, offer: Offer, pokemon: Pokemon): Map<String, Any?> {
        val menu = AstralService.config.menu
        return PokemonVars.of(pokemon, menu.shinyPrefix) + mapOf(
            "price" to settings.price,
            "currency" to AstralService.currencyName(settings.currency, settings.price),
            "balance" to AstralService.balance(player.uuid, settings.currency)
        )
    }

    /** "3 left" and "you can trade this 1 more time", for the offer's description. */
    private fun stockText(offer: Offer): String {
        val lines = ArrayList<String>()
        AstralService.stockLeft(offer)?.let { lines.add("<gray>Left: <white>$it") }
        AstralService.playerLeft(player.uuid, offer)?.let { lines.add("<gray>You can trade this <white>$it</white> more time(s)") }
        return lines.joinToString("\n")
    }

    private fun putOffer(slot: Int, settings: PoolSettings, offer: Offer) {
        val menu = AstralService.config.menu
        val pokemon = AstralService.pokemonFor(offer) ?: return
        val vars = offerVars(settings, offer, pokemon)
        if (AstralService.availability(player.uuid, offer) != Availability.OPEN) {
            val gone = menu.soldOut.build(vars)
            if (!gone.isEmpty) putSafe(slot, GuiElementBuilder.from(gone).build())
            return
        }
        val stack = PokemonDisplay.item(pokemon, picker(), mapOf("price" to settings.price, "currency" to AstralService.currencyName(settings.currency, settings.price), "stock" to stockText(offer)))
        putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> clicked(settings, offer) }.build())
    }

    private fun clicked(settings: PoolSettings, offer: Offer) {
        val config = AstralService.config
        val pokemon = AstralService.pokemonFor(offer) ?: return
        val vars = offerVars(settings, offer, pokemon)
        if (!config.confirm) {
            execute(settings, offer, vars)
            return
        }
        val icon = PokemonDisplay.item(pokemon, picker(), mapOf("price" to settings.price, "currency" to AstralService.currencyName(settings.currency, settings.price), "stock" to stockText(offer)))
        ConfirmGui(
            player, config.menu.confirmMenu, config.menu.confirmTitle, icon, vars,
            onConfirm = { execute(settings, offer, vars) },
            onCancel = { AstralGui(player).open() }
        ).open()
    }

    private fun execute(settings: PoolSettings, offer: Offer, vars: Map<String, Any?>) {
        val messages = AstralService.config.messages
        val result = AstralService.trade(player, offer.pool, offer.slot)
        val text = when (result) {
            TradeResult.OK -> messages.bought
            TradeResult.CLOSED -> messages.closed
            TradeResult.GONE -> messages.gone
            TradeResult.SOLD_OUT -> messages.soldOut
            TradeResult.LIMIT_REACHED -> messages.limitReached
            TradeResult.IN_BATTLE -> messages.inBattle
            TradeResult.NOT_ENOUGH -> messages.notEnough
            TradeResult.NO_ROOM -> messages.noRoom
        }
        Texts.send(player, text, vars + mapOf("balance" to AstralService.balance(player.uuid, settings.currency)))
        if (result == TradeResult.OK) player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f)
        AstralGui(player).open()
    }
}
