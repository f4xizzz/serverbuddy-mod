package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Leaderboards/config.json` */
@Doc(
    "Leaderboards · top lists of players: time played on the server, money (one board per Impactor currency) and Pokédex progress.\n" +
        "\n" +
        "SHOWING THEM\n" +
        "  /leaderboard                 lists the boards (also /lb and /top)        /leaderboard <board>   the top of one board in chat\n" +
        "  /leaderboard gui [board]     a chest menu with the players' heads\n" +
        "  Placeholders for holograms, scoreboards and chat (the argument goes after a slash):\n" +
        "      %serverbuddy:lb/<board>_<position>_name%      %serverbuddy:lb/<board>_<position>_value%     e.g. %serverbuddy:lb/playtime_1_name%\n" +
        "      %serverbuddy:lb/<board>_<position>_raw%       the plain number (no formatting)\n" +
        "      %serverbuddy:lb/<board>_rank%                 the rank of the player looking at it\n" +
        "\n" +
        "BOARD TYPES (\"type\")\n" +
        "  playtime        time played on the server (the game's own play-time statistic, so the history of old players counts)\n" +
        "  money           a balance of the Impactor economy. \"currency\" is the currency id (\"impactor:dollars\"); empty = the primary one.\n" +
        "                  With \"auto_currency_boards\": true every Impactor currency gets its own board named money_<currency> by itself.\n" +
        "                  /leaderboard currencies lists the ids.\n" +
        "  pokedex_caught  species caught (needs Cobblemon)          pokedex_seen     species seen        pokedex_percent  completion in %\n" +
        "  The Pokédex values are remembered while a player is online, so a player appears after joining once with this module on.\n" +
        "\n" +
        "Lists refresh every \"refresh_seconds\" in the background (money scans the balances of recent players, so keep it at a few minutes).\n" +
        "Players with the permission serverbuddy.leaderboards.hidden never appear (staff accounts, for example)."
)
class LeaderboardsConfig(
    @Doc("false = the module does nothing.")
    val enabled: Boolean = true,
    @SerializedName("refresh_seconds")
    @Doc("How often the lists are rebuilt.")
    val refreshSeconds: Int = 300,
    @Doc("How many players each list shows.")
    val size: Int = 10,
    @SerializedName("active_days")
    @Doc("Only players seen in the last N days are ranked (their player data file was touched). 0 = everyone who ever joined.")
    val activeDays: Int = 90,
    @SerializedName("max_players")
    @Doc("Safety limit: the most recent N players are considered.")
    val maxPlayers: Int = 5000,
    @SerializedName("auto_currency_boards")
    @Doc("true = every Impactor currency gets a board named money_<currency> unless you already defined one for it.")
    val autoCurrencyBoards: Boolean = true,
    @Doc("Names or UUIDs that never appear (besides the hidden permission).")
    val exclude: List<String> = emptyList(),
    @Doc("The boards. The key is the board's id (used in commands and placeholders).")
    val boards: Map<String, BoardConfig> = defaultBoards(),
    val menu: LeaderboardMenu = LeaderboardMenu(),
    val messages: LeaderboardMessages = LeaderboardMessages()
) {
    companion object {
        fun defaultBoards(): Map<String, BoardConfig> {
            val boards = LinkedHashMap<String, BoardConfig>()
            boards["playtime"] = BoardConfig(type = "playtime", title = "<gold>Most time played")
            // Cobblemon-only boards (the Pokédex), and the money board example, are only written when their mod is installed
            if (Integrations.cobblemon()) {
                boards["pokedex"] = BoardConfig(type = "pokedex_caught", title = "<aqua>Pokédex: species caught", valueFormat = "%value% caught")
                boards["pokedex_percent"] = BoardConfig(type = "pokedex_percent", title = "<aqua>Pokédex completion", valueFormat = "%value%%")
            }
            if (Integrations.isLoaded("impactor")) boards["money"] = BoardConfig(type = "money", title = "<green>Richest players", currency = "", valueFormat = "%symbol%%value%")
            return boards
        }
    }
}

class BoardConfig(
    @Doc("false = this board is hidden.")
    val enabled: Boolean = true,
    @Doc("playtime | money | pokedex_caught | pokedex_seen | pokedex_percent")
    val type: String = "playtime",
    @Doc("Title (MiniMessage).")
    val title: String = "<gold>Leaderboard",
    @Doc("money only: the Impactor currency id (\"impactor:dollars\"). Empty = the primary currency.")
    val currency: String = "",
    @SerializedName("value_format")
    @Doc("How a value is written. Placeholders: %value% (formatted: \"5h 30m\", \"1250\", \"42.5\") %raw% (plain number) %symbol% (money: the currency symbol).")
    val valueFormat: String = "%value%"
)

class LeaderboardMenu(
    @Doc("Title. Placeholders: %board% (the board's title).")
    val title: String = "<dark_purple><bold>Leaderboards</bold> <dark_gray>» <white>%board%",
    @SerializedName("type", alternate = ["size"])
    val type: InventoryType = InventoryType.GENERIC_9x4,
    @SerializedName("player_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the players' heads go, best first. Needs at least \"size\" slots.")
    val playerSlots: List<Int> = listOf(4, 12, 14, 19, 20, 21, 22, 23, 24, 25),
    val fillers: List<MenuFiller> = listOf(MenuFiller(slots = (0..8).toList() + (27..35).toList(), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
    @Doc("The head of a player. Placeholders: %position% %player% %value% %board%.")
    val head: ItemSpec = ItemSpec("minecraft:player_head", name = "<yellow>#%position% <white>%player%", lore = listOf("<gray>%value%")),
    @Doc("Shown for an empty position.")
    val empty: ItemSpec = ItemSpec("minecraft:gray_dye", name = "<dark_gray>#%position%", lore = listOf("<gray>Nobody yet")),
    @Doc("A button per board is added in these slots, in order (the board's own icon below).")
    @SerializedName("board_slots")
    @JsonAdapter(SlotsAdapter::class)
    val boardSlots: List<Int> = (28..34).toList(),
    @SerializedName("board_icon")
    @Doc("The button that opens a board. Placeholders: %board%.")
    val boardIcon: ItemSpec = ItemSpec("minecraft:paper", name = "%board%", lore = listOf("<gray>Click to see this list")),
    val close: MenuButton = MenuButton(slots = listOf(35), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red>Close"))
)

class LeaderboardMessages(
    @Doc("First line of /leaderboard <board>. Placeholders: %title% %board%.")
    val header: String = "<gold>━━ %title% <gold>━━",
    @Doc("One line per player. Placeholders: %position% %player% %value%.")
    val line: String = " <yellow>#%position% <white>%player% <dark_gray>» <gray>%value%",
    @SerializedName("your_rank")
    @Doc("Last line when the player is not in the top. Placeholders: %rank% %value%.")
    val yourRank: String = "<gray>You: <yellow>#%rank%</yellow> <dark_gray>» <gray>%value%",
    val empty: String = "<gray>Nobody is ranked on this board yet.",
    @SerializedName("list_header")
    val listHeader: String = "<gold>Leaderboards: <gray>(click or type <white>/leaderboard <board></white>)",
    @SerializedName("list_line")
    @Doc("Placeholders: %board% (the id) %title%.")
    val listLine: String = " <yellow>%board% <dark_gray>» <white>%title%",
    @SerializedName("unknown_board")
    val unknownBoard: String = "<red>There is no board called <white>%board%</white>.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>Only players can open the menu.",
    @SerializedName("module_off")
    val moduleOff: String = "<red>The leaderboards are turned off."
)
