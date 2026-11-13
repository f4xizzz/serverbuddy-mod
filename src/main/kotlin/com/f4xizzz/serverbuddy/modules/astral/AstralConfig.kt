package com.f4xizzz.serverbuddy.modules.astral

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmSettings
import com.google.gson.annotations.SerializedName

@Doc(
    "Astral Trader · trade tokens for Pokémon (/astral)\n" +
        "\n" +
        "There are two kinds of tokens, kept in the database: SPECIAL tokens (for legendary, paradox and Ultra Beast Pokémon) and\n" +
        "SHINY tokens (for shiny Pokémon). Admins and rewards hand them out with /astral admin give <player> <special|shiny> <amount>.\n" +
        "Every pool shows a few Pokémon at a time and picks new ones every \"reset_hours\". Each pool has ONE price for all of its offers.\n" +
        "An offer that ran out for the whole server, or that a player already traded for, shows as SOLD OUT for that player."
)
class AstralConfig(
    @Doc("Master switch: false closes the trader (no menu, no trades).")
    val enabled: Boolean = true,
    @SerializedName("reset_hours")
    @Doc("Every how many hours every pool picks new Pokémon (checked every minute). 0 = never on its own, only /astral admin rotate.")
    val resetHours: Double = 24.0,
    @SerializedName("announce_rotation")
    @Doc("Tell everybody online when the offers change.")
    val announceRotation: Boolean = true,
    @Doc("Ask the player to confirm before a trade is made.")
    val confirm: Boolean = true,
    @Doc("The four pools. Turn one off with enabled=false.")
    val pools: AstralPools = AstralPools(),
    @SerializedName("currency_names")
    @Doc("How each token is called in the texts.")
    val currencyNames: Map<String, String> = mapOf("special" to "special tokens", "shiny" to "shiny tokens"),
    val menu: AstralMenu = AstralMenu(),
    val messages: AstralMessages = AstralMessages()
)

class AstralPools(
    @Doc("Legendary and mythical Pokémon, paid in special tokens.")
    val legendary: PoolSettings = PoolSettings(size = 4, price = 3, currency = "special", labels = listOf("legendary", "mythical"), level = 70, perfectIvs = 3),
    @Doc("Paradox Pokémon, paid in special tokens.")
    val paradox: PoolSettings = PoolSettings(size = 3, price = 2, currency = "special", labels = listOf("paradox"), level = 60, perfectIvs = 3),
    @SerializedName("ultra_beast")
    @Doc("Ultra Beasts, paid in special tokens.")
    val ultraBeast: PoolSettings = PoolSettings(size = 3, price = 1, currency = "special", labels = listOf("ultra_beast"), level = 60, perfectIvs = 3),
    @Doc("Shiny Pokémon, paid in shiny tokens: ONE price for the whole pool.")
    val shiny: PoolSettings = PoolSettings(
        size = 4, price = 1, currency = "shiny", labels = emptyList(),
        excludeLabels = listOf("legendary", "mythical", "ultra_beast", "paradox"), level = 30, shiny = true
    )
) {
    fun entries(): List<Pair<String, PoolSettings>> = listOf("legendary" to legendary, "paradox" to paradox, "ultra_beast" to ultraBeast, "shiny" to shiny)
}

class PoolSettings(
    val enabled: Boolean = true,
    @Doc("How many Pokémon the pool shows at a time (1 to 7).")
    val size: Int = 4,
    @Doc("What ONE offer of this pool costs.")
    val price: Int = 1,
    @Doc("Which token pays for it: \"special\" or \"shiny\".")
    val currency: String = "special",
    @SerializedName("per_player_limit")
    @Doc("How many times each player can trade for each offer before the next rotation (0 = no limit).")
    val perPlayerLimit: Int = 1,
    @Doc("How many copies of each offer exist for the whole server before the next rotation (0 = unlimited). A Pokémon that ran out shows as SOLD OUT.")
    val stock: Int = 0,
    @Doc("Cobblemon labels that put a species in this pool (legendary, mythical, ultra_beast, paradox...). Empty = any species.")
    val labels: List<String> = emptyList(),
    @Doc("Only these species (ids like cobblemon:mewtwo, or names). Empty = every species the labels allow.")
    val species: List<String> = emptyList(),
    @Doc("Species that never show up in this pool.")
    val exclude: List<String> = emptyList(),
    @SerializedName("exclude_labels")
    @Doc("Species with any of these labels never show up in this pool.")
    val excludeLabels: List<String> = emptyList(),
    @Doc("Level of the Pokémon you get.")
    val level: Int = 50,
    @Doc("true = the Pokémon you get are shiny.")
    val shiny: Boolean = false,
    @SerializedName("perfect_ivs")
    @Doc("How many of the 6 IVs come out perfect (31).")
    val perfectIvs: Int = 0
)

class AstralMenu(
    val title: String = "<gradient:#a855f7:#6366f1><bold>Astral Trader</bold></gradient>",
    val filler: ItemSpec = ItemSpec("minecraft:black_stained_glass_pane", name = " ", hideTooltip = true),
    @Doc("The star on top. Placeholders: %special% and %shiny% (the player's tokens) and %reset% (time until the next rotation).")
    val header: ItemSpec = ItemSpec(
        "minecraft:nether_star", name = "<light_purple><bold>Astral Trader",
        lore = listOf("<gray>Special tokens: <white>%special%", "<gray>Shiny tokens: <white>%shiny%", "", "<gray>New offers in <white>%reset%")
    ),
    @SerializedName("header_slot")
    val headerSlot: Int = 4,
    @SerializedName("pool_rows")
    @Doc("The menu row (1 to 4) where each pool's offers go. The pool icon is the first slot of the row.")
    val poolRows: Map<String, Int> = mapOf("legendary" to 1, "paradox" to 2, "ultra_beast" to 3, "shiny" to 4),
    @SerializedName("pool_icons")
    @Doc("The icon at the start of each row. Placeholders: %price% and %currency%.")
    val poolIcons: Map<String, ItemSpec> = mapOf(
        "legendary" to ItemSpec("minecraft:amethyst_shard", name = "<gold><bold>Legendary", lore = listOf("<gray>Each costs <white>%price% %currency%")),
        "paradox" to ItemSpec("minecraft:ender_eye", name = "<aqua><bold>Paradox", lore = listOf("<gray>Each costs <white>%price% %currency%")),
        "ultra_beast" to ItemSpec("minecraft:echo_shard", name = "<light_purple><bold>Ultra Beast", lore = listOf("<gray>Each costs <white>%price% %currency%")),
        "shiny" to ItemSpec("minecraft:glow_berries", name = "<yellow><bold>Shiny", lore = listOf("<gray>Each costs <white>%price% %currency%"))
    ),
    @SerializedName("offer_name")
    @Doc("Name of an offer (the Pokémon placeholders work: %name%, %level%, %shiny%...).")
    val offerName: String = "%shiny%<aqua>%name% <gray>Lv.%level%",
    @SerializedName("offer_lore")
    @Doc("Description of an offer. %price%, %currency% and %stock% (what is left) are added.")
    val offerLore: List<String> = listOf("<gray>Price: <white>%price% %currency%", "%stock%", "<yellow>Click to trade"),
    @SerializedName("shiny_prefix")
    val shinyPrefix: String = "<gold>★ </gold>",
    @SerializedName("sold_out")
    @Doc("Shown instead of an offer that is gone. %name% is the Pokémon.")
    val soldOut: ItemSpec = ItemSpec("minecraft:barrier", name = "<red><bold>SOLD OUT", lore = listOf("<gray>%name% is gone until the next rotation.")),
    val close: MenuButton = MenuButton(slots = listOf(49), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red><bold>Close")),
    @SerializedName("confirm_menu")
    val confirmMenu: ConfirmSettings = ConfirmSettings(),
    @SerializedName("confirm_title")
    val confirmTitle: String = "<dark_gray><bold>Trade for %name%?"
)

class AstralMessages(
    @SerializedName("only_players") val onlyPlayers: String = "<red>Only players can use this command.",
    val closed: String = "<red>The Astral Trader is closed right now.",
    @SerializedName("not_enough") val notEnough: String = "<red>You need <white>%price% %currency%</white> and have <white>%balance%</white>.",
    val bought: String = "<green>You traded <white>%price% %currency%</white> for <white>%name%</white>!",
    @SerializedName("sold_out") val soldOut: String = "<red>%name% is sold out.",
    @SerializedName("limit_reached") val limitReached: String = "<red>You already traded for %name% in this rotation.",
    @SerializedName("no_room") val noRoom: String = "<red>There is no room in your party or PC. Your tokens were not spent.",
    @SerializedName("in_battle") val inBattle: String = "<red>You can't trade in the middle of a battle.",
    @SerializedName("gone") val gone: String = "<red>That offer is not available anymore.",
    val balance: String = "<light_purple>✦ <gray>Astral tokens: <white>%special%</white> special · <white>%shiny%</white> shiny",
    @SerializedName("balance_other") val balanceOther: String = "<light_purple>✦ <white>%player%</white> <gray>has <white>%special%</white> special · <white>%shiny%</white> shiny",
    @SerializedName("unknown_player") val unknownPlayer: String = "<red>No player called <white>%player%</white>.",
    @SerializedName("unknown_currency") val unknownCurrency: String = "<red>Unknown token <white>%currency%</white>: use special or shiny.",
    @SerializedName("unknown_pool") val unknownPool: String = "<red>Unknown pool <white>%pool%</white>.",
    @SerializedName("admin_changed") val adminChanged: String = "<green>%player% now has <white>%amount% %currency%</white>.",
    @SerializedName("admin_rotated") val adminRotated: String = "<green>New offers picked for: <white>%pools%",
    val rotated: String = "<light_purple>✦ <white>The Astral Trader has new offers! <gray>/astral"
)
