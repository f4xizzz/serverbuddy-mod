package com.f4xizzz.serverbuddy.modules.gts

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.ModuleWebhookConfig
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonFilter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/GTS/config.json` (only created when Cobblemon is installed). */
@Doc(
    "GTS · Global Trading Station: a market where players list Pokémon and items for a price and others buy them (/gts).\n" +
        "Inspired by the GTS mod for Cobblemon by bencrow11 (MIT license).\n" +
        "\n" +
        "LISTING (commands)\n" +
        "  /gts list pokemon <party slot 1-6> <price>     /gts list item <price> [amount]     (the item in your hand)\n" +
        "  The Pokémon or items leave the player and wait in the market. Rules that apply: min_price / max_price, the minimum prices\n" +
        "  below (a shiny, a legendary, perfect IVs, a hidden ability or a given species cannot be sold too cheap), the blacklists,\n" +
        "  the number of listings a player may have (max_listings + the best permission tier) and the optional listing fee.\n" +
        "BROWSING (/gts)\n" +
        "  A menu with tabs: Pokémon, Items, My listings, Returned (expired or cancelled listings to take back) and a chat history\n" +
        "  (/gts history). Click a listing to buy it; click your own listing to cancel it. Listings expire after listing_hours and wait\n" +
        "  in \"Returned\" for their owner.\n" +
        "MONEY\n" +
        "  The seller receives the price minus sale_tax_percent. A seller who is offline is paid when they join.\n" +
        "  The economy used is \"economy\" (\"\" = the first installed; \"impactor\" or \"impactor:dollars\"...).\n" +
        "MODERATION (permission serverbuddy.gts.admin, level 2)\n" +
        "  /gts admin remove <listing id>     /gts admin timeout <player> <time> (\"2h\", \"3d\")     /gts admin untimeout <player>\n" +
        "Players use /gts (permission serverbuddy.gts.use, on by default)."
)
class GtsConfig(
    @Doc("false = the market is closed (listings stay in the database).")
    val enabled: Boolean = true,
    @Doc("Which economy pays and charges: \"\" (the first installed), \"impactor\", \"impactor:dollars\"...")
    val economy: String = "",
    @SerializedName("currency_symbol")
    val currencySymbol: String = "$",
    @SerializedName("allow_pokemon")
    val allowPokemon: Boolean = true,
    @SerializedName("allow_items")
    val allowItems: Boolean = true,
    @SerializedName("listing_hours")
    @Doc("How long a listing stays up before it expires.")
    val listingHours: Int = 48,
    @SerializedName("max_listings")
    @Doc("Active listings every player may have.")
    val maxListings: Int = 5,
    @SerializedName("listing_tiers")
    @Doc("A permission → how many listings players with it may have (the biggest counts).")
    val listingTiers: Map<String, Int> = mapOf("serverbuddy.gts.listings.vip" to 10),
    @SerializedName("sale_tax_percent")
    @Doc("Percent of the price the server keeps when something sells.")
    val saleTaxPercent: Double = 5.0,
    @SerializedName("listing_fee")
    @Doc("A flat amount charged to list (0 = free).")
    val listingFee: Double = 0.0,
    @SerializedName("min_price")
    val minPrice: Double = 1.0,
    @SerializedName("max_price")
    val maxPrice: Double = 100000000.0,
    @SerializedName("keep_one_in_party")
    @Doc("true = the last Pokémon of the party cannot be listed while the PC is empty.")
    val keepOneInParty: Boolean = true,
    @SerializedName("pokemon_minimums")
    val pokemonMinimums: PokemonMinimums = PokemonMinimums(),
    @SerializedName("item_minimums")
    @Doc("An item id → the least that item may cost PER ITEM (a stack of 8 needs 8 × this).")
    val itemMinimums: Map<String, Double> = mapOf("minecraft:diamond" to 50.0, "minecraft:netherite_ingot" to 500.0),
    @SerializedName("pokemon_blacklist")
    @Doc("Pokémon that cannot be listed.")
    val pokemonBlacklist: PokemonFilter = PokemonFilter(),
    @SerializedName("item_blacklist")
    @Doc("Items that cannot be listed: ids (\"minecraft:bedrock\"), \"mod:*\" for a whole mod, or #tags.")
    val itemBlacklist: List<String> = listOf("minecraft:bedrock", "minecraft:command_block", "minecraft:barrier", "minecraft:spawner"),
    @SerializedName("history_size")
    @Doc("How many sales /gts history shows.")
    val historySize: Int = 10,
    @Doc("Discord posts for sales.")
    val webhook: GtsWebhook = GtsWebhook(),
    val menu: GtsMenu = GtsMenu(),
    val messages: GtsMessages = GtsMessages()
)

class PokemonMinimums(
    @Doc("The least any Pokémon may cost.")
    val base: Double = 0.0,
    @Doc("Added to the minimum of a shiny.")
    val shiny: Double = 0.0,
    @Doc("Added to the minimum of a legendary, mythical, ultra beast or paradox Pokémon.")
    val legendary: Double = 0.0,
    @SerializedName("hidden_ability")
    @Doc("Added to the minimum of a Pokémon with its hidden ability.")
    val hiddenAbility: Double = 0.0,
    @SerializedName("perfect_ivs")
    @Doc("How many perfect (31) IVs → the minimum price. The highest one the Pokémon qualifies for applies. Example: \"6\": 5000.")
    val perfectIvs: Map<String, Double> = mapOf("6" to 2000.0, "5" to 1000.0),
    @Doc("A species → its own minimum.")
    val species: Map<String, Double> = emptyMap()
)

class GtsWebhook(
    val settings: ModuleWebhookConfig = ModuleWebhookConfig(),
    @SerializedName("min_price")
    @Doc("Only sales of at least this price are posted (0 = every sale).")
    val minPrice: Double = 0.0,
    @Doc("Discord text (markdown). Placeholders: %seller% %buyer% %what% %price%.")
    val message: String = "**%buyer%** bought **%what%** from **%seller%** for **%price%** on the GTS."
)

class GtsMenu(
    @Doc("Title. Placeholders: %tab% %page% %pages%.")
    val title: String = "<dark_aqua><bold>GTS <dark_gray>» <white>%tab% <gray>(%page%/%pages%)",
    @SerializedName("type", alternate = ["size"])
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("listing_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the listings of a page go.")
    val listingSlots: List<Int> = (10..16).toList() + (19..25) + (28..34) + (37..43),
    val filler: ItemSpec = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true),
    @Doc("Lines added to every listing's description. Placeholders: %seller% %price% %symbol% %time% %id%.")
    val listingLore: List<String> = listOf("", "<gray>Seller: <white>%seller%", "<gray>Price: <gold>%symbol%%price%", "<gray>Expires in: <white>%time%", "", "<yellow>Click to buy"),
    @SerializedName("own_lore")
    @Doc("Added to the player's own listings instead of listing_lore. Placeholders as above.")
    val ownLore: List<String> = listOf("", "<gray>Price: <gold>%symbol%%price%", "<gray>Expires in: <white>%time%", "", "<red>Click to cancel this listing"),
    @SerializedName("returned_lore")
    @Doc("Added to the Returned tab. Placeholders as above.")
    val returnedLore: List<String> = listOf("", "<gray>Listed for: <gold>%symbol%%price%", "", "<green>Click to take it back"),
    @SerializedName("tab_pokemon")
    val tabPokemon: MenuButton = MenuButton(slots = listOf(1), hideWhenUnavailable = false, icon = ItemSpec("cobblemon:poke_ball", name = "<aqua><bold>Pokémon", lore = listOf("<gray>%count% for sale"))),
    @SerializedName("tab_items")
    val tabItems: MenuButton = MenuButton(slots = listOf(2), hideWhenUnavailable = false, icon = ItemSpec("minecraft:chest", name = "<aqua><bold>Items", lore = listOf("<gray>%count% for sale"))),
    @SerializedName("tab_mine")
    val tabMine: MenuButton = MenuButton(slots = listOf(4), hideWhenUnavailable = false, icon = ItemSpec("minecraft:writable_book", name = "<yellow><bold>My listings", lore = listOf("<gray>%count% active", "", "<gray>List with <white>/gts list pokemon <slot> <price>", "<gray>or <white>/gts list item <price>"))),
    @SerializedName("tab_returned")
    val tabReturned: MenuButton = MenuButton(slots = listOf(6), hideWhenUnavailable = false, icon = ItemSpec("minecraft:ender_chest", name = "<green><bold>Returned", lore = listOf("<gray>%count% waiting for you"))),
    val previous: MenuButton = MenuButton(slots = listOf(45), icon = ItemSpec("minecraft:arrow", name = "<yellow>Previous page")),
    val next: MenuButton = MenuButton(slots = listOf(53), icon = ItemSpec("minecraft:arrow", name = "<yellow>Next page")),
    val close: MenuButton = MenuButton(slots = listOf(49), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red><bold>Close")),
    val empty: ItemSpec = ItemSpec("minecraft:gray_dye", name = "<dark_gray>Nothing here", lore = listOf("<gray>There are no listings in this tab.")),
    @Doc("The yes/no menu for buying and cancelling.")
    val confirm: ConfirmSettings = ConfirmSettings(
        confirm = MenuButton(slots = listOf(11), hideWhenUnavailable = false, icon = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green><bold>Confirm", lore = listOf("<gray>%action%"))),
        cancel = MenuButton(slots = listOf(15), hideWhenUnavailable = false, icon = ItemSpec("minecraft:red_stained_glass_pane", name = "<red><bold>Back"))
    ),
    @SerializedName("confirm_buy_title")
    val confirmBuyTitle: String = "<dark_aqua><bold>Buy for %symbol%%price%?",
    @SerializedName("confirm_cancel_title")
    val confirmCancelTitle: String = "<dark_aqua><bold>Cancel this listing?",
    @SerializedName("shiny_prefix")
    val shinyPrefix: String = "<gold>★ </gold>",
    @SerializedName("pokemon_name")
    val pokemonName: String = "%shiny%<aqua>%name% %gender%",
    @SerializedName("pokemon_lore")
    val pokemonLore: List<String> = listOf("<gray>Level <white>%level%", "<gray>Nature <white>%nature%</white> · Ability <white>%ability%", "<gray>IVs <white>%ivs%", "<gray>EVs <white>%evs%", "<gray>Moves <white>%move1%, %move2%, %move3%, %move4%")
)

class GtsMessages(
    @SerializedName("listed")
    @Doc("Placeholders: %what% %price% %symbol% %id%.")
    val listed: String = "<green>You listed <white>%what%</white> for <gold>%symbol%%price%</gold>. <gray>(/gts)",
    @SerializedName("bought")
    @Doc("To the buyer. Placeholders: %what% %price% %symbol% %seller%.")
    val bought: String = "<green>You bought <white>%what%</white> from <white>%seller%</white> for <gold>%symbol%%price%</gold>.",
    @SerializedName("sold")
    @Doc("To the seller. Placeholders: %what% %price% %symbol% %buyer% %tax% (what the server kept) %received%.")
    val sold: String = "<green><white>%buyer%</white> bought <white>%what%</white>! You received <gold>%symbol%%received%</gold> <gray>(tax %symbol%%tax%)",
    @SerializedName("sold_offline")
    @Doc("Shown after joining when sales were paid while the player was offline. Placeholders: %count% %amount% %symbol%.")
    val soldOffline: String = "<green>While you were away <white>%count%</white> of your listings sold: <gold>%symbol%%amount%</gold> were paid to you.",
    @SerializedName("cancelled")
    val cancelled: String = "<yellow>The listing was cancelled. Take <white>%what%</white> back from the <white>Returned</white> tab.",
    @SerializedName("returned")
    val returned: String = "<green>You took <white>%what%</white> back.",
    @SerializedName("expired_notice")
    @Doc("Shown after joining. Placeholders: %count%.")
    val expiredNotice: String = "<yellow>You have <white>%count%</white> listing(s) waiting in the GTS Returned tab. <gray>/gts",
    @SerializedName("not_enough_money")
    val notEnoughMoney: String = "<red>You need <white>%symbol%%price%</white> to buy that.",
    @SerializedName("own_listing")
    val ownListing: String = "<red>That is your own listing.",
    @SerializedName("gone")
    val gone: String = "<red>That listing is gone (sold, cancelled or expired).",
    @SerializedName("no_room")
    val noRoom: String = "<red>You have no room for that Pokémon (party and PC are full).",
    @SerializedName("price_range")
    @Doc("Placeholders: %min% %max% %symbol%.")
    val priceRange: String = "<red>The price must be between <white>%symbol%%min%</white> and <white>%symbol%%max%</white>.",
    @SerializedName("price_too_low")
    @Doc("Placeholders: %min% %symbol% %what%.")
    val priceTooLow: String = "<red>The least you can ask for <white>%what%</white> is <white>%symbol%%min%</white>.",
    @SerializedName("too_many")
    @Doc("Placeholders: %max%.")
    val tooMany: String = "<red>You already have <white>%max%</white> active listings.",
    @SerializedName("blacklisted")
    val blacklisted: String = "<red><white>%what%</white> cannot be sold on the GTS.",
    @SerializedName("timed_out")
    @Doc("Placeholders: %time%.")
    val timedOut: String = "<red>You are not allowed to list on the GTS for <white>%time%</white> more.",
    @SerializedName("no_item")
    val noItem: String = "<red>Hold the item you want to sell.",
    @SerializedName("no_pokemon")
    val noPokemon: String = "<red>There is no Pokémon in that party slot.",
    @SerializedName("in_battle")
    val inBattle: String = "<red>You cannot use the GTS during a battle.",
    @SerializedName("last_pokemon")
    val lastPokemon: String = "<red>You cannot list your last Pokémon.",
    @SerializedName("fee_failed")
    @Doc("Placeholders: %fee% %symbol%.")
    val feeFailed: String = "<red>Listing costs <white>%symbol%%fee%</white> and you cannot pay it.",
    @SerializedName("disabled_kind")
    val disabledKind: String = "<red>That kind of listing is turned off.",
    @SerializedName("no_economy")
    val noEconomy: String = "<red>The server has no economy for the GTS.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>Only players can use the GTS.",
    @SerializedName("history_header")
    val historyHeader: String = "<dark_aqua>Your last GTS sales and purchases:",
    @SerializedName("history_line")
    @Doc("Placeholders: %ago% %what% %price% %symbol% %other% %role% (sold to / bought from).")
    val historyLine: String = " <gray>%ago% ago: <white>%role% %other%</white> <white>%what%</white> <gold>%symbol%%price%",
    @SerializedName("history_empty")
    val historyEmpty: String = "<gray>You have no GTS history yet.",
    @SerializedName("role_sold")
    val roleSold: String = "sold to",
    @SerializedName("role_bought")
    val roleBought: String = "bought from",
    @SerializedName("admin_removed")
    val adminRemoved: String = "<green>Listing <white>%id%</white> was removed and returned to its owner.",
    @SerializedName("admin_unknown_listing")
    val adminUnknownListing: String = "<red>There is no active listing with that id.",
    @SerializedName("admin_timeout")
    @Doc("Placeholders: %player% %time%.")
    val adminTimeout: String = "<green><white>%player%</white> cannot list on the GTS for <white>%time%</white>.",
    @SerializedName("admin_untimeout")
    val adminUntimeout: String = "<green><white>%player%</white> can list on the GTS again.",
    @SerializedName("bad_time")
    val badTime: String = "<red>Use a time like 30m, 2h or 3d.",
    @SerializedName("unknown_player")
    val unknownPlayer: String = "<red>Unknown player: <white>%player%",
    @SerializedName("tab_names")
    val tabNames: GtsTabNames = GtsTabNames()
)

class GtsTabNames(
    val pokemon: String = "Pokémon",
    val items: String = "Items",
    val mine: String = "My listings",
    val returned: String = "Returned"
)
