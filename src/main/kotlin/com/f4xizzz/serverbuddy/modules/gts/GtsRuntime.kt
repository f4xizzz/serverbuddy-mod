package com.f4xizzz.serverbuddy.modules.gts

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmGui
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max

enum class GtsTab { POKEMON, ITEMS, MINE, RETURNED }

/** Loads the GTS config, hooks the server events, registers `/gts` and builds the market menu. Cobblemon only. */
object GtsRuntime {
    @Volatile
    var installed = false
        private set

    fun install(folder: String) {
        GtsService.load(load(folder))
        installed = true
        ServerLifecycleEvents.SERVER_STARTING.register { GtsRepository.createTables() }
        ServerTickEvents.END_SERVER_TICK.register { server -> if (GtsService.config.enabled) GtsService.tick(server) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (GtsService.config.enabled) ServerScheduler.later(80) { if (!player.hasDisconnected()) GtsService.onJoin(player) }
        }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        GtsService.load(load(folder))
    }

    private fun load(folder: String): GtsConfig = ConfigFiles.load(folder, "config.json", GtsConfig::class.java) { GtsConfig() }

    // ---------------------------------------------------------------- menu

    class GtsGui(player: ServerPlayer, private var tab: GtsTab = GtsTab.POKEMON, private var page: Int = 0) :
        SimpleGui(GtsService.config.menu.type.type, player, false) {
        init {
            refresh()
        }

        private fun counts(): Map<GtsTab, Int> = mapOf(
            GtsTab.POKEMON to GtsRepository.count(ListingStatus.ACTIVE, ListingKind.POKEMON, null),
            GtsTab.ITEMS to GtsRepository.count(ListingStatus.ACTIVE, ListingKind.ITEM, null),
            GtsTab.MINE to GtsRepository.count(ListingStatus.ACTIVE, null, player.uuid),
            GtsTab.RETURNED to GtsRepository.returnableCount(player.uuid)
        )

        fun refresh() {
            val config = GtsService.config
            val menu = config.menu
            for (slot in 0 until virtualSize) clearSlot(slot)
            val filler = menu.filler.build()
            if (!filler.isEmpty) for (slot in 0 until virtualSize) setSlot(slot, GuiElementBuilder.from(filler).build())

            val counts = counts()
            val perPage = menu.listingSlots.size
            val total = counts.getValue(tab)
            val pages = max(1, (total + perPage - 1) / perPage)
            page = page.coerceIn(0, pages - 1)
            val offset = page * perPage
            val listings = when (tab) {
                GtsTab.POKEMON -> GtsRepository.page(ListingStatus.ACTIVE, ListingKind.POKEMON, null, offset, perPage)
                GtsTab.ITEMS -> GtsRepository.page(ListingStatus.ACTIVE, ListingKind.ITEM, null, offset, perPage)
                GtsTab.MINE -> GtsRepository.page(ListingStatus.ACTIVE, null, player.uuid, offset, perPage)
                GtsTab.RETURNED -> GtsRepository.returnable(player.uuid, offset, perPage)
            }
            val kind = when (tab) { GtsTab.MINE -> "own"; GtsTab.RETURNED -> "returned"; else -> "market" }
            for ((index, listing) in listings.withIndex()) {
                val slot = menu.listingSlots.getOrNull(index) ?: break
                val stack = GtsService.icon(listing, player, if (listing.sellerUuid == player.uuid && tab != GtsTab.RETURNED) "own" else kind) ?: continue
                putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> onClick(listing) }.build())
            }
            if (listings.isEmpty()) menu.empty.build().takeIf { !it.isEmpty }?.let { putSafe(menu.listingSlots[menu.listingSlots.size / 2], GuiElementBuilder.from(it).build()) }

            val names = config.messages.tabNames
            val tabName = when (tab) { GtsTab.POKEMON -> names.pokemon; GtsTab.ITEMS -> names.items; GtsTab.MINE -> names.mine; GtsTab.RETURNED -> names.returned }
            fun vars(of: GtsTab) = mapOf("count" to counts.getValue(of), "tab" to tabName, "page" to page + 1, "pages" to pages)
            putButton(menu.tabPokemon, config.allowPokemon, vars(GtsTab.POKEMON)) { tab = GtsTab.POKEMON; page = 0; refresh() }
            putButton(menu.tabItems, config.allowItems, vars(GtsTab.ITEMS)) { tab = GtsTab.ITEMS; page = 0; refresh() }
            putButton(menu.tabMine, true, vars(GtsTab.MINE)) { tab = GtsTab.MINE; page = 0; refresh() }
            putButton(menu.tabReturned, true, vars(GtsTab.RETURNED)) { tab = GtsTab.RETURNED; page = 0; refresh() }
            putButton(menu.previous, page > 0, vars(tab)) { page--; refresh() }
            putButton(menu.next, page < pages - 1, vars(tab)) { page++; refresh() }
            putButton(menu.close, true, vars(tab)) { close() }
            setTitle(Texts.render(menu.title, vars(tab)))
        }

        private fun onClick(listing: Listing) {
            val menu = GtsService.config.menu
            when {
                tab == GtsTab.RETURNED -> {
                    GtsService.takeBack(player, listing.id)
                    refresh()
                }
                listing.sellerUuid == player.uuid -> confirm(listing, menu.confirmCancelTitle, "Cancel the listing") { GtsService.cancel(player, listing.id) }
                else -> confirm(listing, menu.confirmBuyTitle, "Pay ${GtsService.config.currencySymbol}${com.f4xizzz.serverbuddy.core.Economy.format(listing.price)}") { GtsService.buy(player, listing.id) }
            }
        }

        private fun confirm(listing: Listing, title: String, action: String, run: () -> Unit) {
            val config = GtsService.config
            val icon = GtsService.icon(listing, player, "none") ?: return
            val vars = mapOf("price" to com.f4xizzz.serverbuddy.core.Economy.format(listing.price), "symbol" to config.currencySymbol, "action" to action)
            val back = { GtsGui(player, tab, page).open() }
            ConfirmGui(player, config.menu.confirm, title, icon, vars, onConfirm = { run(); back() }, onCancel = { back() }).open()
        }
    }

    // ---------------------------------------------------------------- commands

    private val USE = SbPermissions.require<CommandSourceStack>(GtsService.USE, true)
    private val ADMIN = SbPermissions.require<CommandSourceStack>(GtsService.ADMIN, 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "gts") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("gts").build(), "gts")
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(USE).executes { open(it) }
            .then(
                Commands.literal("list")
                    .then(
                        Commands.literal("pokemon").then(
                            Commands.argument("slot", IntegerArgumentType.integer(1, 6)).then(
                                Commands.argument("price", DoubleArgumentType.doubleArg(0.0)).executes { ctx ->
                                    val player = player(ctx) ?: return@executes 0
                                    if (GtsService.listPokemon(player, IntegerArgumentType.getInteger(ctx, "slot"), DoubleArgumentType.getDouble(ctx, "price"))) 1 else 0
                                }
                            )
                        )
                    )
                    .then(
                        Commands.literal("item").then(
                            Commands.argument("price", DoubleArgumentType.doubleArg(0.0))
                                .executes { ctx ->
                                    val player = player(ctx) ?: return@executes 0
                                    if (GtsService.listItem(player, DoubleArgumentType.getDouble(ctx, "price"), null)) 1 else 0
                                }
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1, 99)).executes { ctx ->
                                        val player = player(ctx) ?: return@executes 0
                                        if (GtsService.listItem(player, DoubleArgumentType.getDouble(ctx, "price"), IntegerArgumentType.getInteger(ctx, "amount"))) 1 else 0
                                    }
                                )
                        )
                    )
            )
            .then(Commands.literal("history").executes { history(it) })
            .then(
                Commands.literal("admin").requires(ADMIN)
                    .then(
                        Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).executes { ctx ->
                            val id = StringArgumentType.getString(ctx, "id")
                            val messages = GtsService.config.messages
                            if (GtsService.adminRemove(id)) Texts.reply(ctx.source, messages.adminRemoved, mapOf("id" to Texts.escape(id))) else Texts.fail(ctx.source, messages.adminUnknownListing)
                            1
                        })
                    )
                    .then(
                        Commands.literal("timeout").then(
                            Commands.argument("player", StringArgumentType.word()).suggests(online).then(
                                Commands.argument("time", StringArgumentType.word()).executes { timeout(it) }
                            )
                        )
                    )
                    .then(Commands.literal("untimeout").then(Commands.argument("player", StringArgumentType.word()).suggests(online).executes { untimeout(it) }))
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) Texts.fail(ctx.source, GtsService.config.messages.onlyPlayers)
        return player
    }

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        if (!GtsService.config.enabled) return 0
        GtsGui(player, if (GtsService.config.allowPokemon) GtsTab.POKEMON else GtsTab.ITEMS).open()
        return 1
    }

    private fun history(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        val config = GtsService.config
        val sales = GtsRepository.history(player.uuid, config.historySize)
        if (sales.isEmpty()) {
            Texts.reply(ctx.source, config.messages.historyEmpty)
            return 0
        }
        Texts.reply(ctx.source, config.messages.historyHeader)
        for (sale in sales) {
            val sold = sale.sellerUuid == player.uuid
            Texts.reply(
                ctx.source, config.messages.historyLine,
                mapOf(
                    "ago" to Durations.format(System.currentTimeMillis() - sale.soldAt), "what" to Texts.escape(sale.label), "price" to com.f4xizzz.serverbuddy.core.Economy.format(sale.price),
                    "symbol" to config.currencySymbol, "other" to Texts.escape(if (sold) sale.buyerName.orEmpty() else sale.sellerName), "role" to if (sold) config.messages.roleSold else config.messages.roleBought
                )
            )
        }
        return sales.size
    }

    private fun resolve(ctx: CommandContext<CommandSourceStack>, name: String): java.util.UUID? {
        val server = ctx.source.server
        return server.playerList.getPlayerByName(name)?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id
    }

    private fun timeout(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val messages = GtsService.config.messages
        val uuid = resolve(ctx, name)
        if (uuid == null) {
            Texts.fail(ctx.source, messages.unknownPlayer, mapOf("player" to Texts.escape(name)))
            return 0
        }
        val millis = Durations.parseMillis(StringArgumentType.getString(ctx, "time"))
        if (millis == null || millis <= 0) {
            Texts.fail(ctx.source, messages.badTime)
            return 0
        }
        GtsRepository.setTimeout(uuid, System.currentTimeMillis() + millis)
        Texts.reply(ctx.source, messages.adminTimeout, mapOf("player" to Texts.escape(name), "time" to Durations.format(millis)))
        return 1
    }

    private fun untimeout(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val messages = GtsService.config.messages
        val uuid = resolve(ctx, name)
        if (uuid == null) {
            Texts.fail(ctx.source, messages.unknownPlayer, mapOf("player" to Texts.escape(name)))
            return 0
        }
        GtsRepository.setTimeout(uuid, 0)
        Texts.reply(ctx.source, messages.adminUntimeout, mapOf("player" to Texts.escape(name)))
        return 1
    }
}
