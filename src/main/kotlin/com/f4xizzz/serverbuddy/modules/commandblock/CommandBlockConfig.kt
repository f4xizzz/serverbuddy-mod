package com.f4xizzz.serverbuddy.modules.commandblock

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/CommandBlock/config.json` */
@Doc(
    "Command blocker · who can NOT use certain commands (/back, /home, /warp, any of them)\n" +
        "\n" +
        "HOW IT WORKS: each rule has a list of commands and the conditions in which they are blocked. Whoever falls under the rule gets\n" +
        "the warning and the command doesn't run. It only applies to players (the console and command blocks are not blocked).\n" +
        "Whoever has the permission serverbuddy.commandblock.bypass (default: operators level 4) is never blocked.\n" +
        "\n" +
        "COMMANDS (without the slash):\n" +
        "  \"back\"        blocks /back and its aliases (/return); if you renamed the command in modules.conf, the new name counts too\n" +
        "  \"home set\"    blocks only /home set ... (the rest of /home stays free)\n" +
        "  \"tp\"          blocks every form of /tp;  \"minecraft:tp\" blocks only the prefixed form\n" +
        "  \"*\"           blocks ALL commands (combine with \"except\" to let some through)\n" +
        "Commands from other mods and from Minecraft work the same as ServerBuddy's. Only what the player types counts (a command\n" +
        "run from inside /execute or a command block does not pass through here).\n" +
        "\n" +
        "CONDITIONS: in \"when\" ALL must match; in \"when_any\" ONE is enough; any of them accepts ! in front to negate.\n" +
        "  tag:no_back                        the player has the tag (a scoreboard tag, given with /tag, or from the /tags menu)\n" +
        "  equipped:vip                       the vip tag of /tags is equipped right now (tag:vip also counts whoever only owns it)\n" +
        "  permission:myserver.no_tp          the player has that permission (LuckPerms or given by a Flan claim)\n" +
        "  dimension:minecraft:the_nether     the player is in that dimension\n" +
        "  time:18:00-23:30   day:sat,sun   date:2026-12-24..2026-12-26    server time, weekday and dates\n" +
        "The command is blocked when the rule's conditions match AND none of \"except\" matches.\n" +
        "\n" +
        "EXAMPLE: nobody uses /home or /warp in the arena (the in_arena tag can come from a Flan claim, in the \"On enter: give tag\" button),\n" +
        "but the staff with the permission serverbuddy.arena.staff can:\n" +
        "  \"arena\": { \"commands\": [\"home\", \"warp\"], \"when_any\": [\"tag:in_arena\"], \"except\": [\"permission:serverbuddy.arena.staff\"] }\n" +
        "\n" +
        "WITHOUT EDITING THE FILE: /commandblock list, /commandblock check <player> <command> and /commandblock reload."
)
class CommandBlockConfig(
    @Doc("false = no rule applies (they stay saved in the file).")
    val enabled: Boolean = true,
    @Doc("The rules. The key is just a name for you (it shows in /commandblock list, in the warnings and in the log).")
    val rules: Map<String, CommandBlockRule> = defaultRules(),
    @Doc("Textos (MiniMessage).")
    val messages: CommandBlockMessages = CommandBlockMessages()
) {
    companion object {
        fun defaultRules(): Map<String, CommandBlockRule> = linkedMapOf(
            "no_back" to CommandBlockRule(
                commands = listOf("back"),
                whenAny = listOf("tag:no_back"),
                message = "<red>You can't use <white>/back</white> right now."
            ),
            "arena" to CommandBlockRule(
                commands = listOf("home", "warp", "spawn", "back", "tpa", "tpahere", "rtp"),
                whenAny = listOf("tag:in_arena"),
                message = "<red>You can't use <white>/%command%</white> inside the arena."
            )
        )
    }
}

class CommandBlockRule(
    @Doc("false = the rule stays saved, but doesn't apply.")
    val enabled: Boolean = true,
    @Doc("The blocked commands, without the slash: [\"back\"], [\"home set\"], [\"tp\"], [\"*\"]. See the examples above.")
    val commands: List<String> = emptyList(),
    @SerializedName("when")
    @Doc("Conditions where ALL must match. E.g.: [\"dimension:minecraft:the_nether\"].")
    val conditions: List<String> = emptyList(),
    @SerializedName("when_any")
    @Doc("Conditions where ONE is enough (used together with \"when\"). E.g.: [\"tag:no_back\"]. The rule needs at least one condition.")
    val whenAny: List<String> = emptyList(),
    @Doc("Exceptions: if ONE of these matches, the command is allowed. E.g.: [\"permission:serverbuddy.arena.staff\"].")
    val except: List<String> = emptyList(),
    @Doc("A warning just for this rule (uses the general one if null). %command% = what the player typed, %rule% = the rule's name.")
    val message: String? = null
)

class CommandBlockMessages(
    @Doc("General warning for whoever tried a blocked command. %command% = what the player typed, %rule% = the rule's name.")
    val blocked: String = "<red>You can't use <white>/%command%</white> right now.",
    @SerializedName("list_header")
    @Doc("First line of /commandblock list.")
    val listHeader: String = "<gold><bold>Command blocker</bold> <gray>(%count% rules)",
    @SerializedName("list_line")
    @Doc("One line per rule. %rule% = name, %state% = on or off, %commands% = commands, %conditions% = when it blocks.")
    val listLine: String = "<dark_gray>• <white>%rule%</white> %state%<dark_gray>: <white>%commands%</white> <gray>when <white>%conditions%",
    @SerializedName("list_empty")
    @Doc("When there are no rules.")
    val listEmpty: String = "<gray>No blocking rule is configured.",
    @SerializedName("check_blocked")
    @Doc("Result of /commandblock check when the command is blocked. %player% %command% %rule%")
    val checkBlocked: String = "<red>%player% can NOT use <white>/%command%</white>: rule <white>%rule%</white>.",
    @SerializedName("check_free")
    @Doc("Result of /commandblock check when it is allowed. %player% %command%")
    val checkFree: String = "<green>%player% can use <white>/%command%</white>.",
    @SerializedName("check_bypass")
    @Doc("Result of /commandblock check when the person has the bypass permission. %player%")
    val checkBypass: String = "<green>%player% has the permission <white>serverbuddy.commandblock.bypass</white>: no rule applies to them."
)
