package com.f4xizzz.serverbuddy.modules.wondertrade

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.ModuleWebhookConfig
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.modules.pokeservices.PickerSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonFilter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/WonderTrade/config.json` (only created when Cobblemon is installed). */
@Doc(
    "Wonder Trade · a blind trade with other players (/wondertrade or /wt).\n" +
        "\n" +
        "A player offers one Pokémon and gets a mystery Pokémon back: one that another player offered earlier (the pool is stored in the\n" +
        "ServerBuddy database), or a freshly generated random one when the pool has nothing suitable (or random_only is on).\n" +
        "The offered Pokémon joins the pool for the next trader. Nobody ever gets back their own Pokémon.\n" +
        "\n" +
        "  - cooldown_minutes / permission_cooldowns: how long a player waits between trades (the shortest cooldown they qualify for wins).\n" +
        "  - level_matching: only pool Pokémon within level_match_range levels of the offered one are given.\n" +
        "  - legendary_matching / shiny_matching: a legendary (or shiny) can only be swapped for a legendary (or shiny), and a normal\n" +
        "    Pokémon never returns one, so rare Pokémon are not handed out for free.\n" +
        "  - blacklist: Pokémon that cannot be offered (species, labels, shiny, level limits).\n" +
        "  - generated: how the random replacement is made when the pool cannot provide one.\n" +
        "  - broadcast: who hears about trades. webhook: Discord posts for legendary/shiny trades (see webhook.conf for the default).\n" +
        "Players use /wondertrade (permission serverbuddy.wondertrade.use, on by default); /wondertrade history shows their last trades.\n" +
        "Staff (serverbuddy.wondertrade.admin, level 2): /wondertrade admin pool (size of the pool), /wondertrade admin clear (empties the pool),\n" +
        "/wondertrade admin reset <player> (clears a cooldown)."
)
class WonderTradeConfig(
    @Doc("false = the module does nothing (the pool stays in the database).")
    val enabled: Boolean = true,
    @SerializedName("cooldown_minutes")
    val cooldownMinutes: Int = 60,
    @SerializedName("permission_cooldowns")
    @Doc("A permission → the cooldown in minutes for players who have it.")
    val permissionCooldowns: Map<String, Int> = mapOf("serverbuddy.wondertrade.cooldown.vip" to 30),
    @SerializedName("level_matching")
    val levelMatching: Boolean = false,
    @SerializedName("level_match_range")
    val levelMatchRange: Int = 10,
    @SerializedName("random_only")
    @Doc("true = always give a generated Pokémon (the pool is not used).")
    val randomOnly: Boolean = false,
    @SerializedName("legendary_matching")
    val legendaryMatching: Boolean = true,
    @SerializedName("shiny_matching")
    val shinyMatching: Boolean = true,
    @SerializedName("keep_one_in_party")
    @Doc("true = the last Pokémon of the party cannot be traded while the PC is empty.")
    val keepOneInParty: Boolean = true,
    @SerializedName("pool_max")
    @Doc("The pool keeps at most this many Pokémon (the oldest are dropped).")
    val poolMax: Int = 500,
    @SerializedName("history_size")
    @Doc("How many past trades /wondertrade history remembers per player.")
    val historySize: Int = 10,
    @Doc("Pokémon that cannot be offered.")
    val blacklist: PokemonFilter = PokemonFilter(),
    val generated: WonderGenerated = WonderGenerated(),
    val broadcast: WonderBroadcast = WonderBroadcast(),
    @Doc("Discord posts for legendary and shiny trades.")
    val webhook: ModuleWebhookConfig = ModuleWebhookConfig(),
    val menu: PickerSettings = PickerSettings(
        title = "<light_purple><bold>Wonder Trade <gray>» offer a Pokémon",
        pcTitle = "<light_purple><bold>Wonder Trade <gray>» PC box %box%/%boxes%",
        lore = listOf("<gray>Level <white>%level%", "<gray>IVs <white>%ivs%", "", "<yellow>Click to trade it for a mystery Pokémon!")
    ),
    val confirm: WonderConfirmMenu = WonderConfirmMenu(),
    val messages: WonderMessages = WonderMessages()
)

class WonderGenerated(
    @SerializedName("level_range")
    @Doc("The generated Pokémon is within this many levels of the offered one.")
    val levelRange: Int = 5,
    @SerializedName("shiny_odds")
    @Doc("1 in this many generated Pokémon is shiny (0 = never).")
    val shinyOdds: Int = 4096,
    @SerializedName("allow_legendary")
    @Doc("true = a generated Pokémon can be a legendary even when the offered one is not. (A legendary offer always gets a legendary.)")
    val allowLegendary: Boolean = false
)

class WonderBroadcast(
    @Doc("Tell everyone about every trade.")
    val trades: Boolean = false,
    @Doc("Tell everyone when a legendary is traded.")
    val legendaries: Boolean = true,
    @Doc("Tell everyone when a shiny is traded.")
    val shinies: Boolean = true
)

class WonderConfirmMenu(
    @Doc("Title. Placeholders: the Pokémon ones.")
    val title: String = "<light_purple><bold>Trade %name%?",
    val confirm: MenuButton = MenuButton(
        slots = listOf(11), hideWhenUnavailable = false,
        icon = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green><bold>Trade!", lore = listOf("<gray>You will get a mystery Pokémon", "<gray>and give up <white>%name%</white>."))
    ),
    val cancel: MenuButton = MenuButton(slots = listOf(15), hideWhenUnavailable = false, icon = ItemSpec("minecraft:red_stained_glass_pane", name = "<red><bold>Cancel")),
    @SerializedName("icon_slot")
    val iconSlot: Int = 13,
    val filler: ItemSpec = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true)
)

class WonderMessages(
    @Doc("To the trader. Placeholders: %gave% (the Pokémon they offered) %got% (the one they received), %level% (of the received one), %from% (who offered it, or \"the wild\").")
    val traded: String = "<light_purple>You traded <white>%gave%</white> and received <white>%got%</white> <gray>(level %level%, from %from%)",
    @Doc("Broadcast. Placeholders: %player% %gave% %got%.")
    val broadcast: String = "<light_purple>%player% made a Wonder Trade: <white>%gave%</white> <gray>⇄</gray> <white>%got%</white>",
    @SerializedName("broadcast_rare")
    @Doc("Broadcast for a legendary or shiny. Placeholders: %player% %got%.")
    val broadcastRare: String = "<gold><bold>%player%</bold> just received <white>%got%</white> <gold>in a Wonder Trade!",
    @SerializedName("webhook_rare")
    @Doc("Discord text (markdown) for a legendary or shiny trade. Placeholders: %player% %got% %gave%.")
    val webhookRare: String = "**%player%** received **%got%** in a Wonder Trade (gave %gave%).",
    @Doc("Placeholders: %time%.")
    val cooldown: String = "<red>You can trade again in <white>%time%</white>.",
    val blacklisted: String = "<red><white>%name%</white> cannot be traded.",
    @SerializedName("in_battle")
    val inBattle: String = "<red>You cannot trade during a battle.",
    @SerializedName("last_pokemon")
    val lastPokemon: String = "<red>You cannot trade your last Pokémon.",
    @SerializedName("already_gone")
    val alreadyGone: String = "<red>That Pokémon is no longer in your party or PC.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>Only players can use Wonder Trade.",
    @SerializedName("history_header")
    val historyHeader: String = "<light_purple>Your last Wonder Trades:",
    @SerializedName("history_line")
    @Doc("Placeholders: %gave% %got% %level% %ago%.")
    val historyLine: String = " <gray>%ago% ago: <white>%gave%</white> → <white>%got%</white> <gray>(level %level%)",
    @SerializedName("history_empty")
    val historyEmpty: String = "<gray>You have not made a Wonder Trade yet.",
    @SerializedName("admin_pool")
    @Doc("Placeholders: %size%.")
    val adminPool: String = "<gold>Wonder Trade pool: <white>%size%</white> Pokémon waiting.",
    @SerializedName("admin_cleared")
    @Doc("Placeholders: %count%.")
    val adminCleared: String = "<green>The Wonder Trade pool was emptied (<white>%count%</white> Pokémon dropped).",
    @SerializedName("admin_reset")
    val adminReset: String = "<green>The Wonder Trade cooldown of <white>%player%</white> was cleared.",
    @SerializedName("unknown_player")
    val unknownPlayer: String = "<red>Unknown player: <white>%player%",
    @SerializedName("from_wild")
    @Doc("What %from% shows for a generated Pokémon.")
    val fromWild: String = "the wild"
)
