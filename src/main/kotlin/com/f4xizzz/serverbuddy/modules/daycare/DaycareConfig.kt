package com.f4xizzz.serverbuddy.modules.daycare

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.f4xizzz.serverbuddy.modules.pokeservices.PickerSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonFilter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Daycare/config.json` (only created when Cobblemon is installed). */
@Doc(
    "Daycare · players leave Pokémon in a daycare and they gain levels while they wait (/daycare).\n" +
        "\n" +
        "  - A Pokémon gains one level every (minutes_to_level_100 ÷ 100) minutes, up to max_level, even while the player is offline.\n" +
        "  - The levels are applied when the Pokémon is taken out again.\n" +
        "  - How many Pokémon a player can leave (their slots) and what each deposit costs depend on base_capacity / base_price and\n" +
        "    on permission_tiers: the best tier a player has wins. Slots beyond the capacity show as locked.\n" +
        "  - Deposits are paid with the economy named in \"economy\" (\"\" = the first installed; price 0 = free, no economy needed).\n" +
        "Players use /daycare (permission serverbuddy.daycare.use, on by default). Staff: /daycare admin list <player>\n" +
        "(permission serverbuddy.daycare.admin, level 2)."
)
class DaycareConfig(
    @Doc("false = the module does nothing (Pokémon already inside stay safe in the database).")
    val enabled: Boolean = true,
    @Doc("Which economy is charged: \"\" (the first installed), \"impactor\", \"impactor:dollars\"...")
    val economy: String = "",
    @SerializedName("currency_symbol")
    val currencySymbol: String = "$",
    @SerializedName("minutes_to_level_100")
    @Doc("Minutes a Pokémon needs to gain 100 levels (so 1 level every minutes_to_level_100 / 100 minutes).")
    val minutesToLevel100: Int = 40,
    @SerializedName("max_level")
    @Doc("Pokémon stop gaining levels here.")
    val maxLevel: Int = 100,
    @SerializedName("base_capacity")
    @Doc("Slots every player has.")
    val baseCapacity: Int = 1,
    @SerializedName("base_price")
    @Doc("Price of a deposit for players without a tier.")
    val basePrice: Double = 5000.0,
    @SerializedName("permission_tiers")
    @Doc("A permission → the slots and the deposit price for players who have it (the tier with most slots wins).")
    val permissionTiers: Map<String, DaycareTier> = mapOf(
        "serverbuddy.daycare.tier.vip" to DaycareTier(capacity = 3, price = 4000.0),
        "serverbuddy.daycare.tier.mvp" to DaycareTier(capacity = 5, price = 3000.0)
    ),
    @SerializedName("keep_one_in_party")
    @Doc("true = the last Pokémon of the party cannot be left while the PC is empty.")
    val keepOneInParty: Boolean = true,
    @Doc("Pokémon that cannot be left in the daycare.")
    val blacklist: PokemonFilter = PokemonFilter(),
    val menu: DaycareMenu = DaycareMenu(),
    val picker: PickerSettings = PickerSettings(
        title = "<dark_gray><bold>Daycare <gray>» choose a Pokémon",
        pcTitle = "<dark_gray><bold>Daycare <gray>» PC box %box%/%boxes%",
        lore = listOf("<gray>Level <white>%level%", "", "<yellow>Click to leave it here", "<red>Costs <white>%symbol%%price%")
    ),
    val messages: DaycareMessages = DaycareMessages()
)

class DaycareTier(
    @Doc("Slots.")
    val capacity: Int = 1,
    @Doc("Price of a deposit.")
    val price: Double = 0.0
)

class DaycareMenu(
    @Doc("Title. Placeholders: %used% %capacity%.")
    val title: String = "<bold><white>%used%/%capacity%</white></bold> <gradient:yellow:gold><bold>▶ Daycare ◀</bold></gradient>",
    @SerializedName("type", alternate = ["size"])
    val type: InventoryType = InventoryType.GENERIC_9x3,
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the daycare slots go, in order. Needs at least as many as the biggest capacity a player can have.")
    val slots: List<Int> = (10..16).toList(),
    @Doc("A Pokémon inside. Placeholders: the Pokémon ones plus %old_level% %current_level% %gained% %time% (until level 100).")
    val occupied: ItemSpec = ItemSpec(
        "minecraft:paper", name = "<aqua>%name%",
        lore = listOf("<gray>Original level: <white>%old_level%", "<gray>Level now: <green>%current_level%", "<gray>Levels gained: <yellow>+%gained%", "<gray>Time to max level: <red>%time%", "", "<green><bold>Click to take it back!")
    ),
    @Doc("A free slot. Placeholders: %price% %symbol%.")
    val free: ItemSpec = ItemSpec("cobblemon:poke_ball", name = "<green><bold>Free ✔", lore = listOf("<gray>Price: <green>%symbol%%price%", "", "<yellow>Click to leave a Pokémon here!")),
    @Doc("A slot the player has not unlocked.")
    val locked: ItemSpec = ItemSpec("minecraft:black_stained_glass_pane", name = "<red><bold>✘ Locked ✘", lore = listOf("<gray>Get a VIP rank or a tier to unlock more slots.")),
    val filler: ItemSpec = ItemSpec("minecraft:yellow_stained_glass_pane", name = " ", hideTooltip = true),
    val close: MenuButton = MenuButton(slots = listOf(22), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red><bold>Close"))
)

class DaycareMessages(
    @SerializedName("deposited")
    @Doc("Placeholders: the Pokémon ones plus %price% %symbol%.")
    val deposited: String = "<green><white>%name%</white> is now in the daycare!",
    @SerializedName("withdrawn")
    @Doc("Placeholders: the Pokémon ones plus %old_level% %gained%.")
    val withdrawn: String = "<green>You took <white>%name%</white> back: level <white>%old_level%</white> → <white>%level%</white> <yellow>(+%gained%)</yellow>.",
    @SerializedName("no_room")
    val noRoom: String = "<red>You have no room for that Pokémon (party and PC are full).",
    @SerializedName("no_money")
    val noMoney: String = "<red>You need <white>%symbol%%price%</white> to leave a Pokémon here.",
    @SerializedName("no_economy")
    val noEconomy: String = "<red>The server has no economy to charge the daycare with.",
    @SerializedName("full")
    val full: String = "<red>Your daycare is full.",
    val blacklisted: String = "<red>The daycare does not accept <white>%name%</white>.",
    @SerializedName("in_battle")
    val inBattle: String = "<red>You cannot use the daycare during a battle.",
    @SerializedName("last_pokemon")
    val lastPokemon: String = "<red>You cannot leave your last Pokémon.",
    @SerializedName("already_gone")
    val alreadyGone: String = "<red>That Pokémon is no longer in your party or PC.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>Only players can use the daycare.",
    @SerializedName("admin_header")
    @Doc("Placeholders: %player% %count%.")
    val adminHeader: String = "<gold>Daycare of <white>%player%</white> (%count%):",
    @SerializedName("admin_line")
    @Doc("Placeholders: %slot% %species% %level% %gained%.")
    val adminLine: String = " <gray>#%slot% <white>%species%</white> level %level% <yellow>(+%gained%)",
    @SerializedName("admin_empty")
    val adminEmpty: String = "<gray>That player has nothing in the daycare.",
    @SerializedName("unknown_player")
    val unknownPlayer: String = "<red>Unknown player: <white>%player%"
)
