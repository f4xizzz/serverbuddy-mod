package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Motd/config.json`. */
@Doc(
    "MOTD · the message shown in the server list (Multiplayer menu)\n" +
        "\n" +
        "HOW IT WORKS: ServerBuddy looks at the \"profiles\" from top to bottom and uses the FIRST one whose conditions\n" +
        "match. So keep the special cases (maintenance, whitelist, full...) at the top and the \"default\" profile\n" +
        "(no conditions) last. Inside a profile, \"motds\" is a list: one is picked on every refresh.\n" +
        "\n" +
        "TEXTS: MiniMessage (<red>, <bold>, <gradient:#ff0000:#0000ff>, <rainbow>...). Values that work in the lines:\n" +
        "%online% (players), %max% (maximum), %time% (server time) and %date% (date). <online_players> and\n" +
        "<max_players> work too, as in MiniMOTD.\n" +
        "\n" +
        "CONDITIONS (in \"when\" all must match; in \"when_any\" one is enough). Put ! in front to negate:\n" +
        "  maintenance                    maintenance mode on (/serverbuddy motd maintenance on)\n" +
        "  whitelist                      the server whitelist is on (/whitelist on)\n" +
        "  full                           server full (online >= maximum)\n" +
        "  players>=10                    players online (accepts >=, <=, >, <, ==)\n" +
        "  time:18:00-23:30               server time within the range (can cross midnight: 22:00-06:00)\n" +
        "  day:sat,sun                    day of the week (mon, tue, wed, thu, fri, sat, sun)\n" +
        "  date:2026-12-24..2026-12-26    date range (inclusive)\n" +
        "  event                          some event of the Events module is active (event:<id> for a specific one)\n" +
        "Example, weekend evening profile:  \"when\": [\"day:sat,sun\", \"time:19:00-23:59\"]\n" +
        "\n" +
        "ICONS: put 64x64 PNG images in the Motd/icons folder. In each MOTD, \"icon\" is the file name without .png,\n" +
        "\"random\" (picks one from the folder) or \"server\" (the server's normal server-icon.png).\n" +
        "\n" +
        "After editing: /serverbuddy motd reload (or /serverbuddy reload). To test without leaving the game:\n" +
        "/serverbuddy motd preview [profile]."
)
class MotdConfig(
    @Doc("Turns the MOTD switching on (true) or off (false). When off, the motd in server.properties applies.")
    val enabled: Boolean = true,
    @Doc("The profiles, in the order they are tested. The key is just a name so you can find it.")
    val profiles: Map<String, MotdProfile> = defaultProfiles(),
    @Doc("Maintenance mode: only whoever you allow can join. Turn it on and off with /serverbuddy motd maintenance on|off.")
    val maintenance: MaintenanceConfig = MaintenanceConfig(),
    @Doc("Refusal messages when joining.")
    val login: LoginConfig = LoginConfig()
) {
    companion object {
        fun defaultProfiles(): Map<String, MotdProfile> = linkedMapOf(
            "maintenance" to MotdProfile(
                conditions = listOf("maintenance"),
                motds = listOf(
                    MotdEntry(
                        line1 = "<gradient:#ff5555:#ffaa00><bold>⚙ Under maintenance</bold>",
                        line2 = "<gray>We'll be back soon! <dark_gray>• <yellow>follow our Discord"
                    )
                ),
                players = PlayersConfig(
                    fake = "=0",
                    hoverLines = listOf("<red><bold>Server under maintenance", "<gray>We'll be right back!")
                )
            ),
            "whitelist" to MotdProfile(
                conditions = listOf("whitelist"),
                motds = listOf(
                    MotdEntry(
                        line1 = "<gradient:#a18cd1:#fbc2eb><bold>Whitelisted server</bold> <gray>• %online%/%max%",
                        line2 = "<gray>Only whoever is on the list gets in. <yellow>Ask for your access!"
                    )
                )
            ),
            "full" to MotdProfile(
                conditions = listOf("full"),
                motds = listOf(
                    MotdEntry(
                        line1 = "<gradient:#f6d365:#fda085><bold>Server full!</bold> <gray>• %online%/%max%",
                        line2 = "<gray>Try again in a little while."
                    )
                )
            ),
            "default" to MotdProfile(
                conditions = emptyList(),
                motds = listOf(
                    MotdEntry(
                        line1 = "<gradient:#a18cd1:#fbc2eb><bold>My Server</bold> <gray>• <white>%online%<gray>/<white>%max% <gray>online",
                        line2 = "<gray>Cobblemon • Adventure • Community"
                    ),
                    MotdEntry(
                        line1 = "<gradient:#a18cd1:#fbc2eb><bold>My Server</bold>",
                        line2 = "<yellow>Come catch, evolve and explore!"
                    )
                )
            )
        )
    }
}

enum class Rotation {
    /** Sorteia um MOTD a cada atualização da lista. */
    RANDOM,

    /** Mostra um MOTD depois do outro, na ordem da lista. */
    SEQUENTIAL
}

class MotdProfile(
    @SerializedName("when")
    @Doc("Conditions (ALL must match). Empty list = the profile always applies. See the list of conditions at the top of the file.")
    val conditions: List<String> = emptyList(),
    @SerializedName("when_any")
    @Doc("Conditions where ONE is enough (used together with \"when\"). Empty = ignored.")
    val whenAny: List<String> = emptyList(),
    @Doc("How to pick the MOTD from the list: RANDOM (draws) or SEQUENTIAL (one after another).")
    val mode: Rotation = Rotation.RANDOM,
    @Doc("The MOTDs of this profile (two lines each).")
    val motds: List<MotdEntry> = listOf(MotdEntry()),
    @Doc("Player count of this profile.")
    val players: PlayersConfig = PlayersConfig()
)

class MotdEntry(
    @Doc("Primeira linha.")
    val line1: String = "<gradient:#a18cd1:#fbc2eb><bold>My Server",
    @Doc("Segunda linha.")
    val line2: String = "<gray>Bem-vindo!",
    @Doc("Icon: file name in Motd/icons (without .png), \"random\" or \"server\".")
    val icon: String = "random"
)

class PlayersConfig(
    @Doc("Maximum shown. null = the server's real maximum.")
    val max: Int? = null,
    @SerializedName("just_x_more")
    @Doc("Shows the maximum as \"online + X\" (e.g.: 3 → 16/19). null = off. Takes priority over \"max\".")
    val justXMore: Int? = null,
    @Doc(
        "FAKE online players in the count. Formats: \"+3\" adds 3; \"=50\" always 50; \"10+\" at least 10;\n" +
            "\"3:6\" adds a number between 3 and 6; \"25%\" adds 25%. null = shows the real number."
    )
    val fake: String? = null,
    @SerializedName("allow_exceeding_max")
    @Doc("false = the count shown never goes past the maximum shown.")
    val allowExceedingMax: Boolean = false,
    @SerializedName("hide_count")
    @Doc("true = hides the player count (shows ???).")
    val hideCount: Boolean = false,
    @SerializedName("hide_hover")
    @Doc("true = hides the list of players shown on hover.")
    val hideHover: Boolean = false,
    @SerializedName("hover_lines")
    @Doc("Lines (MiniMessage) shown when hovering the count, in place of the player list. Empty = the normal list.")
    val hoverLines: List<String> = emptyList()
)

class MaintenanceConfig(
    @SerializedName("kick_message")
    @Doc("Message for whoever tries to join during maintenance (and for whoever is kicked when it turns on).")
    val kickMessage: String = "<red><bold>Server under maintenance</bold>\n<gray>We'll be back soon!",
    @Doc("Who can join during maintenance, besides operators (name or UUID).")
    val allowed: List<String> = emptyList(),
    @SerializedName("kick_online_when_enabled")
    @Doc("true = when maintenance turns on, kicks whoever is online and doesn't have permission to stay.")
    val kickOnlineWhenEnabled: Boolean = true
)

class LoginConfig(
    @SerializedName("whitelist_message")
    @Doc("Message for whoever tries to join without being on the whitelist. null = Minecraft's normal message.")
    val whitelistMessage: String? = "<red><bold>You are not on the whitelist.</bold>\n<gray>Ask the server staff for access!"
)
