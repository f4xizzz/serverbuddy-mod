package com.f4xizzz.serverbuddy.modules.blacklist

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Blacklist/config.json` */
@Doc(
    "Blacklist · banned moves in Cobblemon battles (needs Cobblemon)\n" +
        "\n" +
        "HOW IT WORKS: when a player picks a banned move, the choice is refused, they get a notice in the battle itself\n" +
        "and the move menu reopens so they can pick another. The battle continues normally.\n" +
        "It only applies to players: wild Pokémon and NPCs can use any move.\n" +
        "\n" +
        "CONTEXTS: each context is a list of banned moves that only applies in certain situations.\n" +
        "  \"default\"  applies to everyone, always (it is the only one that needs no condition).\n" +
        "  The others apply when the conditions of \"when\" (ALL must match) and of \"when_any\" (ONE is enough) match.\n" +
        "  With no condition, the context applies to whoever has the scoreboard tag with ITS NAME, as in the original mod\n" +
        "  (/tag <player> add mode_tower turns on the context \"mode_tower\").\n" +
        "\n" +
        "CONDITIONS (any of them accepts ! in front to negate):\n" +
        "  tag:torre                          the player has the scoreboard tag \"torre\"\n" +
        "  permission:meuserver.ranked        the player has this permission (LuckPerms)\n" +
        "  dimension:minecraft:the_nether     the player is in this dimension\n" +
        "  battle:pvp   battle:npc   battle:wild    the battle is against another player, an NPC or a wild Pokémon\n" +
        "  time:18:00-23:30   day:sat,sun   date:2026-12-24..2026-12-26    time of day, day of the week and server dates\n" +
        "\n" +
        "EXAMPLE: ban Sheer Cold and Fissure only in PvP, and Explosion only in PvP for whoever has the tag \"torneio\":\n" +
        "  \"pvp\":     { \"when\": [\"battle:pvp\"], \"moves\": [\"sheercold\", \"fissure\"] },\n" +
        "  \"torneio\": { \"when\": [\"battle:pvp\", \"tag:torneio\"], \"moves\": [\"explosion\"] }\n" +
        "\n" +
        "MOVES: write them as in Showdown, lowercase and without spaces (\"thunderbolt\", \"sheercold\", \"willowisp\"). Names with\n" +
        "spaces, hyphens or capital letters also work. Banning \"hiddenpower\" bans all of its variations.\n" +
        "\n" +
        "WITHOUT EDITING THE FILE: /blacklist add <context> <move>, /blacklist remove <context> <move> and /blacklist reload.\n" +
        "To test, /blacklist check <player> [move] shows which contexts apply to them and whether the move is banned.\n" +
        "Tip: if you used the Cobblemon Blacklist mod, your blacklist.json was imported here the first time."
)
class BlacklistConfig(
    @Doc("The contexts. The key is the context name (and the tag that turns it on, when there are no conditions).")
    val contexts: Map<String, BlacklistContext> = defaultContexts(),
    @Doc("Messages shown to players (MiniMessage).")
    val messages: BlacklistMessages = BlacklistMessages()
) {
    companion object {
        fun defaultContexts(): Map<String, BlacklistContext> = linkedMapOf(
            "default" to BlacklistContext(),
            "pvp" to BlacklistContext(conditions = listOf("battle:pvp")),
            "mode_tower" to BlacklistContext(),
            "mode_raid" to BlacklistContext()
        )
    }
}

class BlacklistContext(
    @SerializedName("when")
    @Doc("Conditions that must ALL match. Empty (with no \"when_any\") = the tag named after the context. Ignored in \"default\".")
    val conditions: List<String> = emptyList(),
    @SerializedName("when_any")
    @Doc("Conditions where ONE match is enough (combined with \"when\"). Empty = ignored.")
    val whenAny: List<String> = emptyList(),
    @Doc("Moves banned in this context, using Showdown names: [\"sheercold\", \"fissure\"].")
    val moves: List<String> = emptyList(),
    @Doc("Notice for this context only (null uses the general notice). %move% = move, %context% = context name.")
    val message: String? = null
)

class BlacklistMessages(
    @Doc("Battle notice when a move is rejected. %move% = localized move name, %context% = context that banned it.")
    val banned: String = "<red>[Blacklist] The move <yellow>%move%</yellow> is banned here! Choose another.",
    @SerializedName("list_header")
    @Doc("First line of /blacklist.")
    val listHeader: String = "<gold><bold>Banned moves</bold> <gray>for you right now:",
    @SerializedName("list_line")
    @Doc("One line per context in /blacklist. %context% = context, %note% = when it applies (e.g. PvP only), %moves% = moves.")
    val listLine: String = "<dark_gray>• <white>%context%</white><gray>%note%<dark_gray>: <white>%moves%",
    @SerializedName("list_empty")
    @Doc("When no moves are banned for the player.")
    val listEmpty: String = "<green>No moves are banned for you right now."
)
