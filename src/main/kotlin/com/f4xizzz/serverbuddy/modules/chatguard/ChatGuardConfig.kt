package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/ChatGuard/config.json` */
@Doc(
    "Chat guard · whoever repeats a message is blocked, warned and, if they insist, loses the right to talk in chat\n" +
        "\n" +
        "HOW IT WORKS\n" +
        "  1. A message equal (or nearly equal) to one the player just sent is BLOCKED: nobody sees it.\n" +
        "  2. Each blocked message counts as a WARNING. After the warnings of \"warnings.max_warnings\" (default 3), the next mistake becomes a\n" +
        "     CHAT BAN: three warnings and the fourth mistake is a ban.\n" +
        "  3. A chat ban has a duration, and it grows with what the person has already done:\n" +
        "         time = ban.durations[ban number] + ban.extra_per_warning × accumulated warnings      (at most ban.max)\n" +
        "     With the defaults: 1st ban = 5m + 3 warnings × 1m = 8m; 2nd ban = 30m + 6 × 1m = 36m; 3rd ban = 2h + 9m = 2h 9m...\n" +
        "     (past the last value of the list, the last one repeats.)\n" +
        "  4. With no mistakes for a while, the current warnings disappear (warnings.reset_after). With no bans or warnings for a long time, the history\n" +
        "     of bans and accumulated warnings also resets (ban.memory_days).\n" +
        "\n" +
        "WHAT COUNTS AS \"EQUAL\": the text is cleaned before comparing (capitals, accents, punctuation, spaces and repeated letters don't\n" +
        "matter): \"Hi, everyone!!!\" = \"hi everyone\". With \"duplicates.similarity\" below 1.0, very similar texts count too.\n" +
        "\n" +
        "DURING A CHAT BAN the person can't talk in chat or use the commands in \"ban.blocked_commands\" (/msg, /me...).\n" +
        "Whoever has the permission serverbuddy.chatguard.bypass (default: operators level 3) is never blocked.\n" +
        "\n" +
        "STAFF (permission serverbuddy.chatguard.admin, default level 2): /chatguard status [player], list, history <player>,\n" +
        "ban <player> [time] [reason], unban <player>, reset <player> (clears everything) and reload. Without a time, the ban uses the ladder above.\n" +
        "Whoever has serverbuddy.chatguard.notify (default: level 2) sees the warnings and bans in chat. The states live in the ServerBuddy database."
)
class ChatGuardConfig(
    @Doc("false = the chat guard is off (what was already saved about players stays there).")
    val enabled: Boolean = true,
    @Doc("Repeated messages.")
    val duplicates: DuplicateSettings = DuplicateSettings(),
    @Doc("Messages sent too fast (off by default).")
    val flood: FloodSettings = FloodSettings(),
    @Doc("Avisos.")
    val warnings: WarningSettings = WarningSettings(),
    @Doc("Chat ban.")
    val ban: ChatBanSettings = ChatBanSettings(),
    @Doc("What the staff sees.")
    val staff: ChatGuardStaff = ChatGuardStaff(),
    @Doc("Textos (MiniMessage).")
    val messages: ChatGuardMessages = ChatGuardMessages()
)

class DuplicateSettings(
    val enabled: Boolean = true,
    @SerializedName("window_seconds")
    @Doc("For how many seconds a message counts as \"just sent\". While the person insists, the window renews.")
    val windowSeconds: Int = 60,
    @SerializedName("max_repeats")
    @Doc("How many times the same message can appear within the window. 1 = the second time is already blocked.")
    val maxRepeats: Int = 1,
    @SerializedName("min_length")
    @Doc("Messages with fewer letters than this (after cleaning) are never blocked: \"hi\", \"gg\", \"lol\".")
    val minLength: Int = 4,
    @Doc("1.0 = only equal messages; 0.85 also catches small variations. From 0.5 to 1.0.")
    val similarity: Double = 0.9,
    @SerializedName("history_size")
    @Doc("How many recent messages of each player are compared with the new one.")
    val historySize: Int = 8
)

class FloodSettings(
    val enabled: Boolean = false,
    @Doc("At most this many messages...")
    val messages: Int = 6,
    @Doc("...every this many seconds. Past that, the message is blocked and counts as a warning.")
    val seconds: Int = 5
)

class WarningSettings(
    @SerializedName("max_warnings")
    @Doc("Warnings before the chat ban: the mistake after the last warning becomes a ban. 3 = three warnings and the fourth mistake is a ban. 0 = ban on the first mistake.")
    val maxWarnings: Int = 3,
    @SerializedName("reset_after")
    @Doc("With no new mistakes for this long, the current warnings disappear (\"10m\", \"1h\"). Empty or 0 = they never disappear by themselves.")
    val resetAfter: String = "10m",
    @SerializedName("show_title")
    @Doc("true = the warning also shows big in the middle of the screen.")
    val showTitle: Boolean = true
)

class ChatBanSettings(
    @Doc("The duration of each chat ban, from the 1st on (\"30s\", \"5m\", \"2h\", \"1d\"). After the last one, the last one repeats.")
    val durations: List<String> = listOf("5m", "30m", "2h", "12h", "1d", "3d", "7d"),
    @SerializedName("extra_per_warning")
    @Doc("Each accumulated warning adds this to the ban's duration (\"0\" turns it off).")
    val extraPerWarning: String = "1m",
    @Doc("A ban never goes past this. Empty or 0 = no limit.")
    val max: String = "30d",
    @SerializedName("memory_days")
    @Doc("With no bans or warnings for this many days, the history of bans and accumulated warnings resets. 0 = never resets.")
    val memoryDays: Int = 30,
    @SerializedName("blocked_commands")
    @Doc("Commands that someone banned from chat can't use (written as in the command blocker; aliases are included automatically).")
    val blockedCommands: List<String> = listOf("msg", "tell", "w", "whisper", "pm", "reply", "r", "me", "say", "helpop", "mail send")
)

class ChatGuardStaff(
    @SerializedName("notify_warnings")
    @Doc("true = the staff (serverbuddy.chatguard.notify) sees each warning in chat.")
    val notifyWarnings: Boolean = true,
    @SerializedName("notify_bans")
    @Doc("true = the staff sees each chat ban.")
    val notifyBans: Boolean = true,
    @SerializedName("announce_bans")
    @Doc("true = everybody sees in chat when someone gets a chat ban.")
    val announceBans: Boolean = false,
    @SerializedName("log_to_console")
    @Doc("true = warnings and bans also go to the server console.")
    val logToConsole: Boolean = true
)

class ChatGuardMessages(
    @Doc("Regular warning. %count% = current warning, %max% = total warnings, %reason% = the reason.")
    val warning: String = "<red>⚠ Don't repeat the same message! <gray>Warning <white>%count%/%max%</white>.",
    @SerializedName("warning_last")
    @Doc("The last warning (the next mistake becomes a ban).")
    val warningLast: String = "<red>⚠ Don't repeat the same message! <gray>Warning <white>%count%/%max%</white> — <red>on the next mistake you won't be able to talk in chat.</red>",
    @SerializedName("warning_flood")
    @Doc("Warning for messages sent too fast.")
    val warningFlood: String = "<red>⚠ Slow down! You are sending messages too fast. <gray>Warning <white>%count%/%max%</white>.",
    @SerializedName("warning_title")
    @Doc("Big title (if warnings.show_title).")
    val warningTitle: String = "<red><bold>⚠ Warning %count%/%max%",
    @SerializedName("warning_subtitle")
    @Doc("Subtitle of the warning. %reason% = the reason.")
    val warningSubtitle: String = "<gray>%reason%",
    @SerializedName("reason_duplicate")
    val reasonDuplicate: String = "repeated message",
    @SerializedName("reason_flood")
    val reasonFlood: String = "messages sent too fast",
    @SerializedName("banned_now")
    @Doc("For whoever just got the ban. %time% = ban duration, %bans% = how many chat bans they have had, %warnings% = accumulated warnings, %reason%.")
    val bannedNow: String = "<red><bold>You have been banned from chat for %time%.</bold></red> <gray>Reason: %reason%. This is offense #%bans%.",
    @SerializedName("banned_attempt")
    @Doc("For whoever tries to talk during the ban. %time% = time left.")
    val bannedAttempt: String = "<red>You are banned from chat. <gray><white>%time%</white> left.",
    @SerializedName("unbanned_you")
    @Doc("When the staff lifts the ban.")
    val unbannedYou: String = "<green>You can talk in chat again.",
    @SerializedName("announce_ban")
    @Doc("To everyone, if staff.announce_bans.")
    val announceBan: String = "<gray><white>%player%</white> was banned from chat for spam.",
    @SerializedName("staff_warning")
    @Doc("What the staff sees on a warning. %text% = the blocked message.")
    val staffWarning: String = "<dark_gray>[<gold>ChatGuard<dark_gray>] <white>%player%</white> <gray>got warning <white>%count%/%max%</white> (%reason%): <dark_gray>%text%",
    @SerializedName("staff_ban")
    @Doc("What the staff sees on a ban. %by% = who applied it (\"automatic\" or the staff member's name).")
    val staffBan: String = "<dark_gray>[<gold>ChatGuard<dark_gray>] <red>%player% was banned from chat for %time%</red> <gray>(ban #%bans%, %warnings% warnings in total, %by%)",
    @SerializedName("status_self")
    val statusSelf: String = "<gold><bold>Your chat</bold> <gray>· warnings <white>%warnings%/%max%</white> · chat bans <white>%bans%</white>%ban%",
    @SerializedName("status_other")
    val statusOther: String = "<gold><bold>Chat of %player%</bold> <gray>· warnings <white>%warnings%/%max%</white> · chat bans <white>%bans%</white> · accumulated warnings <white>%total%</white>%ban%",
    @SerializedName("status_banned")
    @Doc("Appended to the status of someone who is banned. %time%.")
    val statusBanned: String = " <gray>· <red>banned, %time% left</red>",
    @SerializedName("list_header")
    val listHeader: String = "<gold><bold>Banned from chat</bold> <gray>(%count%)",
    @SerializedName("list_line")
    val listLine: String = "<dark_gray>• <white>%player%</white> <gray><white>%time%</white> left",
    @SerializedName("list_empty")
    val listEmpty: String = "<gray>Nobody is banned from chat right now.",
    @SerializedName("history_header")
    val historyHeader: String = "<gold><bold>History of %player%</bold>",
    @SerializedName("history_line")
    @Doc("%when%, %kind% (WARNING, BAN, UNBAN, RESET), %detail% and %time%.")
    val historyLine: String = "<dark_gray>%when% <gray>%kind% <white>%detail%<gray>%time%",
    @SerializedName("history_empty")
    val historyEmpty: String = "<gray>Nothing recorded about %player%.",
    @SerializedName("ban_done")
    @Doc("Reply of /chatguard ban.")
    val banDone: String = "<green>%player% was banned from chat for <white>%time%</white>.",
    @SerializedName("unban_done")
    val unbanDone: String = "<green>%player% can talk in chat again.",
    @SerializedName("unban_not_banned")
    val unbanNotBanned: String = "<red>%player% is not banned from chat.",
    @SerializedName("reset_done")
    val resetDone: String = "<green>Warnings and chat bans of %player% cleared <gray>(the /chatguard history log stays saved).",
    @SerializedName("player_unknown")
    val playerUnknown: String = "<red>I couldn't find the player <white>%player%</white>.",
    @SerializedName("bad_time")
    val badTime: String = "<red>Invalid time. Use something like 30s, 15m, 2h or 1d."
)
