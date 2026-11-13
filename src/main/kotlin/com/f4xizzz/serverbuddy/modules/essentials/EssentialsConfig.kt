package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** `config/ServerBuddy/Essentials/config.json` */
@Doc(
    "Essentials · homes, warps, teleports, private messages, moderation and utilities (what Solstice does)\n" +
        "\n" +
        "Each command group is turned on or off in \"groups\". Every text players see lives in Essentials/messages.json\n" +
        "(MiniMessage). Permissions are serverbuddy.essentials.<command> (e.g. serverbuddy.essentials.home); player commands are\n" +
        "open to everyone and staff commands need level 2 or 3, as in LuckPerms. To skip the teleport warmup or the cooldown, give\n" +
        "serverbuddy.essentials.bypass.warmup and serverbuddy.essentials.bypass.cooldown.\n" +
        "\n" +
        "GROUPS:\n" +
        "  teleport   /home /sethome /delhome /homes /homeother /warp /warps /setwarp /delwarp /spawn /setspawn /firstspawn /setfirstspawn\n" +
        "             /back /tpa /tpahere /tpaall /tpaccept /tpdeny /tpcancel /tptoggle /rtp /top /tphere /tppos /tpoffline\n" +
        "  chat       /msg /reply /ignore /unignore /ignorelist /mute /unmute /nick /realname /broadcast /plainbroadcast /staffchat /helpop\n" +
        "             /socialspy /mail /notifications\n" +
        "  player     /god /fly /heal /feed /speed /hat /near /seen /playtime /activetime /afk /suicide /ping /smite /extinguish /ignite\n" +
        "             /effects /sleep /serverstat (/tps) /kittycannon /rocket /nudge\n" +
        "  staff      /invsee /enderchest /tempban /tempbanip /jail /unjail /setjail /jails /checkjail /notes /sudo /doas /restart /commandspy\n" +
        "             /powertool /cooldown /timebar\n" +
        "  utilities  /workbench /anvil /grindstone /stonecutter /loom /smithing /cartography /enchanting /trash /item /repair /skull\n" +
        "  info       /rules /info /motd\n" +
        "\n" +
        "Outside the groups: \"cooldowns\" (a wait between uses of any command) and \"styling\" (chat format, join and leave messages,\n" +
        "player list, mentions and signs) have their own on/off switches.\n" +
        "If the Solstice mod is installed, this module turns itself off so they don't fight."
)
class EssentialsConfig(
    @Doc("Turns each command group on or off.")
    val groups: EssentialsGroups = EssentialsGroups(),
    @Doc("Teleportes, casas, warps, spawn e /rtp.")
    val teleport: TeleportSettings = TeleportSettings(),
    @Doc("Idle players (AFK).")
    val afk: AfkSettings = AfkSettings(),
    @Doc("Private messages, muting, mail and the staff chat.")
    val chat: ChatSettings = ChatSettings(),
    @Doc("Cadeia (jail).")
    val jail: JailSettings = JailSettings(),
    @Doc("/restart with a countdown.")
    val restart: RestartSettings = RestartSettings(),
    @Doc("Texts of /motd, /rules and /info.")
    val info: InfoSettings = InfoSettings(),
    @Doc("A wait between uses of a command (any command, not just ServerBuddy's).")
    val cooldowns: CooldownSettings = CooldownSettings(),
    @Doc("Chat format, join and leave messages, player list (Tab), mentions and signs. Everything is off by default except mentions and signs.")
    val styling: StylingSettings = StylingSettings()
)

class EssentialsGroups(
    val teleport: Boolean = true,
    val chat: Boolean = true,
    val player: Boolean = true,
    val staff: Boolean = true,
    val utilities: Boolean = true,
    val info: Boolean = true
)

class TeleportSettings(
    @SerializedName("warmup_seconds")
    @Doc("Seconds to wait before teleporting (0 = instantly). Players with serverbuddy.essentials.bypass.warmup skip the wait.")
    val warmupSeconds: Int = 3,
    @SerializedName("cancel_on_move")
    @Doc("true = if the player moves during the wait, the teleport is cancelled.")
    val cancelOnMove: Boolean = true,
    @SerializedName("cooldown_seconds")
    @Doc("Wait between one teleport and the next, in seconds (0 = none).")
    val cooldownSeconds: Int = 5,
    @SerializedName("tpa_timeout_seconds")
    @Doc("How long a /tpa request stays valid.")
    val tpaTimeoutSeconds: Int = 60,
    @SerializedName("back_on_death")
    @Doc("true = /back also returns to where the player died (needs the permission serverbuddy.essentials.back.death).")
    val backOnDeath: Boolean = true,
    @Doc("Casas.")
    val homes: HomesSettings = HomesSettings(),
    @Doc("Warps.")
    val warps: WarpsSettings = WarpsSettings(),
    @Doc("Spawn.")
    val spawn: SpawnSettings = SpawnSettings(),
    @Doc("Random teleport.")
    val rtp: RtpSettings = RtpSettings()
)

class HomesSettings(
    @SerializedName("default_limit")
    @Doc("How many homes each player can have.")
    val defaultLimit: Int = 3,
    @Doc("Bigger limits by permission: { \"vip\": 10 } gives 10 homes to whoever has serverbuddy.essentials.homes.vip. The biggest limit wins.")
    val limits: Map<String, Int> = linkedMapOf("vip" to 8, "mvp" to 15)
)

class WarpsSettings(
    @SerializedName("permission_per_warp")
    @Doc("true = each warp needs the permission serverbuddy.essentials.warp.<name> (staff always can).")
    val permissionPerWarp: Boolean = false
)

class SpawnSettings(
    @SerializedName("on_first_join")
    @Doc("true = a player joining for the first time goes to the first-join spawn (/setfirstspawn), if there is one, or to the one set with /setspawn.")
    val onFirstJoin: Boolean = true,
    @SerializedName("on_join")
    @Doc("true = everyone goes to the spawn when they join.")
    val onJoin: Boolean = false
)

class RtpSettings(
    val enabled: Boolean = true,
    @SerializedName("min_radius")
    @Doc("Minimum distance from the center, in blocks.")
    val minRadius: Int = 300,
    @SerializedName("max_radius")
    @Doc("Maximum distance from the center, in blocks.")
    val maxRadius: Int = 3000,
    @Doc("Where the distance is counted from: \"spawn\" (the world spawn) or \"player\" (where the player is).")
    val center: String = "spawn",
    @SerializedName("cooldown_seconds")
    @Doc("Cooldown of /rtp (bypass.cooldown skips it).")
    val cooldownSeconds: Int = 300,
    @Doc("Dimensions where /rtp works.")
    val dimensions: List<String> = listOf("minecraft:overworld")
)

class AfkSettings(
    val enabled: Boolean = true,
    @SerializedName("after_minutes")
    @Doc("Minutes idle until AFK (0 = only with the /afk command).")
    val afterMinutes: Int = 5,
    @SerializedName("kick_after_minutes")
    @Doc("Minutes AFK until kicked (0 = never). Players with serverbuddy.essentials.afk.bypass are not kicked.")
    val kickAfterMinutes: Int = 0,
    @SerializedName("entity_tag")
    @Doc(
        "The AFK tag: a vanilla entity tag that a player carries while AFK and loses when they come back, so selectors and datapacks can\n" +
            "use it (/tag, @a[tag=afk], /execute if entity @a[tag=afk]). Empty = no tag. It is removed when the player leaves."
    )
    val entityTag: String = "afk",
    @Doc(
        "The AFK label that %afk% turns into in the chat format, in the Tab list format and in the %serverbuddy:afk% placeholder\n" +
            "(MiniMessage; keep the space at the end if the name comes after it). Empty = no label."
    )
    val label: String = "<gray>[AFK]</gray> ",
    @Doc("Tell the whole server when someone goes AFK or comes back (the afk_on and afk_off messages).")
    val announce: Boolean = true
)

class ChatSettings(
    @SerializedName("msg_format")
    @Doc("Private message. %from%, %to% and %message%.")
    val msgFormat: String = "<dark_gray>[<light_purple>%from% <gray>→ <light_purple>%to%<dark_gray>] <white>%message%",
    @SerializedName("staffchat_format")
    @Doc("Staff chat. %player% and %message%.")
    val staffChatFormat: String = "<dark_gray>[<red>Equipe<dark_gray>] <white>%player%<gray>: <yellow>%message%",
    @SerializedName("helpop_format")
    @Doc("Help request sent to the staff. %player% and %message%.")
    val helpopFormat: String = "<dark_gray>[<gold>Ajuda<dark_gray>] <white>%player%<gray>: <yellow>%message%",
    @SerializedName("broadcast_format")
    @Doc("/broadcast. %message%.")
    val broadcastFormat: String = "<gradient:#a855f7:#ec4899><bold>AVISO</bold></gradient> <gray>» <white>%message%",
    @SerializedName("socialspy_format")
    @Doc("What the staff sees with /socialspy. %from%, %to% and %message%.")
    val socialSpyFormat: String = "<dark_gray>[<gray>spy<dark_gray>] <gray>%from% → %to%: %message%",
    @SerializedName("nick_max_length")
    @Doc("Maximum length of a nickname (not counting colors).")
    val nickMaxLength: Int = 16
)

class JailSettings(
    @Doc("Maximum distance from the jail. Past that, the prisoner is brought back.")
    val radius: Int = 8,
    @SerializedName("allowed_commands")
    @Doc("Commands a prisoner can still use (without the slash).")
    val allowedCommands: List<String> = listOf("msg", "tell", "w", "reply", "r", "helpop")
)

class RestartSettings(
    @Doc("Warnings, in seconds before restarting.")
    val warnings: List<Int> = listOf(600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1),
    @SerializedName("kick_message")
    @Doc("Message for whoever is online when the server shuts down.")
    val kickMessage: String = "<gold><bold>The server is restarting</bold>\n<gray>Come back in a moment!"
)

class CooldownSettings(
    @Doc("false = no cooldown applies (the groups stay saved).")
    val enabled: Boolean = true,
    @Doc(
        "Command groups with a wait between one use and the next. The key is just a name (it goes in the exemption permission).\n" +
            "\"commands\" is written as in the command blocker: without the slash, \"home\" matches /home and its aliases, \"home set\" only that form.\n" +
            "Players with serverbuddy.essentials.cooldown.exempt.<group> (default: level 3) don't wait. The cooldown counts from the use\n" +
            "(even if the command fails). See and clear someone's wait with /cooldown check|clear <player> <group>."
    )
    val groups: Map<String, CooldownGroup> = linkedMapOf(
        "exemplo" to CooldownGroup(enabled = false, commands = listOf("heal", "feed"), seconds = 60)
    )
)

class CooldownGroup(
    @Doc("false = this group stays saved but does not apply.")
    val enabled: Boolean = true,
    @Doc("The group's commands, without the slash. They all share the same wait.")
    val commands: List<String> = emptyList(),
    @Doc("Seconds to wait.")
    val seconds: Int = 30,
    @SerializedName("when")
    @Doc("Conditions that ALL must match for the cooldown to apply to the player (empty = applies to everyone). E.g.: [\"!permission:serverbuddy.vip\"]. See the command blocker.")
    val conditions: List<String> = emptyList(),
    @SerializedName("when_any")
    @Doc("Conditions where ANY ONE is enough (together with \"when\").")
    val whenAny: List<String> = emptyList(),
    @Doc("A warning just for this group (uses the general one in messages.json if null). %time% = time left, %command% = the command.")
    val message: String? = null
)

class InfoSettings(
    @Doc("Lines of /motd (the message of the day).")
    val motd: List<String> = listOf(
        "<gradient:#a855f7:#ec4899><bold>Bem-vindo!</bold></gradient>",
        "<gray>Use <white>/rules</white> to read the rules and <white>/info</white> for server information."
    ),
    @SerializedName("motd_on_join")
    @Doc("true = shows /motd to every player who joins the server.")
    val motdOnJoin: Boolean = false,
    @Doc("Lines of /rules.")
    val rules: List<String> = listOf(
        "<gradient:#a855f7:#ec4899><bold>Server rules</bold></gradient>",
        "<gray>1. <white>Respect every player.",
        "<gray>2. <white>No cheating, bugs or dupes.",
        "<gray>3. <white>No spam or advertising.",
        "<gray>4. <white>Follow the staff's instructions."
    ),
    @Doc("Lines of /info.")
    val info: List<String> = listOf(
        "<gradient:#a855f7:#ec4899><bold>Information</bold></gradient>",
        "<gray>Use <white>/help</white> to see the commands and <white>/rules</white> to read the rules.",
        "<gray>Problems? Call the staff with <white>/helpop <message></white>."
    )
)

/** Os textos que os jogadores veem (Essentials/messages.json). Um texto que faltar no arquivo usa o padrão daqui. */
class EssentialsMessages(val messages: Map<String, String> = Msg.DEFAULTS)

object Msg {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Essentials")
    private val warned = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var custom: Map<String, String> = emptyMap()

    val DEFAULTS: Map<String, String> = linkedMapOf(
        // teleporte
        "tp_warmup" to "<yellow>Teleporting in <white>%seconds%s</white>... <gray>don't move!",
        "tp_cancelled_move" to "<red>Teleport cancelled: you moved.",
        "tp_cooldown" to "<red>Wait <white>%seconds%s</white> before teleporting again.",
        "tp_done" to "<green>Teleported!",
        "tp_pending" to "<red>You already have a teleport in progress.",
        "home_set" to "<green>Home <white>%name%</white> set.",
        "home_moved" to "<green>Home <white>%name%</white> updated.",
        "home_limit" to "<red>You already have <white>%limit%</white> home(s), the maximum. Delete one with <white>/delhome</white>.",
        "home_deleted" to "<green>Home <white>%name%</white> deleted.",
        "home_unknown" to "<red>You don't have a home called <white>%name%</white>. Your homes: <white>%homes%",
        "home_none" to "<red>You don't have any homes yet. Use <white>/sethome [name]</white>.",
        "home_pick" to "<gray>You have several homes. Use <white>/home <name></white>: %homes%",
        "home_invalid_name" to "<red>Invalid name. Use letters, numbers and _ (up to 24).",
        "homes_list" to "<gold><bold>Your homes</bold> <gray>(%count%/%limit%): %homes%",
        "warp_set" to "<green>Warp <white>%name%</white> set.",
        "warp_deleted" to "<green>Warp <white>%name%</white> deleted.",
        "warp_unknown" to "<red>There is no warp called <white>%name%</white>.",
        "warp_no_permission" to "<red>You can't use the warp <white>%name%</white>.",
        "warps_list" to "<gold><bold>Warps</bold> <gray>(%count%): %warps%",
        "warps_none" to "<gray>No warps have been created yet.",
        "spawn_set" to "<green>Spawn set here.",
        "spawn_none" to "<red>The spawn has not been set yet (use /setspawn).",
        "back_none" to "<red>There is nowhere to go back to.",
        "back_going" to "<gray>Going back...",
        "tpa_sent" to "<green>Teleport request sent to <white>%player%</white>.",
        "tpa_sent_here" to "<green>Request for <white>%player%</white> to come to you sent.",
        "tpa_received" to "<yellow>%player% wants to teleport to you. <click:run_command:'/tpaccept %player%'><green>[Accept]</green></click> <click:run_command:'/tpdeny %player%'><red>[Deny]</red></click>",
        "tpa_received_here" to "<yellow>%player% wants you to teleport to them. <click:run_command:'/tpaccept %player%'><green>[Accept]</green></click> <click:run_command:'/tpdeny %player%'><red>[Deny]</red></click>",
        "tpa_none" to "<red>You have no teleport requests.",
        "tpa_expired" to "<red>The teleport request expired.",
        "tpa_accepted" to "<green>Request accepted.",
        "tpa_denied" to "<gray>Request denied.",
        "tpa_cancelled" to "<gray>Request cancelled.",
        "tpa_off" to "<red>%player% is not accepting teleport requests.",
        "tpa_toggle_on" to "<green>You now receive teleport requests.",
        "tpa_toggle_off" to "<gray>You no longer receive teleport requests.",
        "tpa_self" to "<red>You can't send a request to yourself.",
        "tpa_ignored" to "<red>The request could not be sent.",
        "rtp_searching" to "<gray>Looking for a safe spot...",
        "rtp_failed" to "<red>I couldn't find a safe spot. Try again.",
        "rtp_disabled" to "<red>Random teleport is turned off.",
        "rtp_world" to "<red>/rtp doesn't work in this dimension.",
        "top_done" to "<green>You went to the top.",
        // chat
        "msg_self" to "<red>You can't message yourself.",
        "msg_nobody" to "<red>Nobody to reply to.",
        "msg_ignored" to "<red>That player is not receiving your messages.",
        "ignore_added" to "<green>You are now ignoring <white>%player%</white>.",
        "ignore_removed" to "<green>You stopped ignoring <white>%player%</white>.",
        "ignore_list" to "<gray>You ignore: <white>%players%",
        "ignore_none" to "<gray>You are not ignoring anyone.",
        "ignore_staff" to "<red>You can't ignore the staff.",
        "muted_you" to "<red>You are muted%reason%. <gray>%time%",
        "mute_done" to "<green><white>%player%</white> was muted %time%.",
        "unmute_done" to "<green><white>%player%</white> can talk again.",
        "unmuted_you" to "<green>You can talk again.",
        "mute_not_muted" to "<red>%player% is not muted.",
        "nick_set" to "<green>Your nickname is now <white>%nick%</white>.",
        "nick_removed" to "<green>Nickname removed.",
        "nick_too_long" to "<red>A nickname can have at most %max% letters.",
        "nick_taken" to "<red>That nickname is already in use.",
        "nick_colors" to "<red>You can't use colors in your nickname.",
        "realname" to "<gray>%nick% is <white>%player%</white>.",
        "realname_none" to "<red>I couldn't find anyone with the nickname %nick%.",
        "staffchat_on" to "<green>Staff chat on: what you write now goes only to the staff.",
        "staffchat_off" to "<gray>Staff chat off.",
        "helpop_sent" to "<green>Help request sent to the staff.",
        "helpop_nobody" to "<yellow>There is no staff online right now, but the request was recorded.",
        "socialspy_on" to "<green>Message spy on.",
        "socialspy_off" to "<gray>Message spy off.",
        "mail_sent" to "<green>Message saved for <white>%player%</white>.",
        "mail_new" to "<gray>You have <green>%count%</green> new message(s). <white>/mail read",
        "mail_none" to "<gray>Your mailbox is empty.",
        "mail_line" to "<dark_gray>[%id%] <white>%from%<gray>: %message% <dark_gray>(%when%)",
        "mail_cleared" to "<green>Mailbox cleared.",
        "mail_unknown_player" to "<red>That player has never joined the server.",
        // jogador
        "god_on" to "<green>God mode <bold>on</bold>.",
        "god_off" to "<gray>God mode off.",
        "fly_on" to "<green>Flight <bold>on</bold>.",
        "fly_off" to "<gray>Flight off.",
        "healed" to "<green>Health restored.",
        "fed" to "<green>Hunger satisfied.",
        "speed_set" to "<green>%kind% speed is now <white>%speed%</white>.",
        "hat_done" to "<green>Done! The item is on your head.",
        "hat_empty" to "<red>Hold an item in your hand.",
        "near_none" to "<gray>Nobody nearby (%radius% blocks).",
        "near_line" to "<gray>Nearby (%radius% blocks): %players%",
        "seen_online" to "<white>%player%</white> <green>has been online</green> for <white>%time%</white>.",
        "seen_offline" to "<white>%player%</white> <red>is offline</red>. <gray>Last seen <white>%time%</white> ago.",
        "seen_never" to "<red>I've never seen %player% here.",
        "playtime" to "<white>%player%</white> <gray>has played for <white>%time%</white>.",
        "afk_on" to "<gray>* <white>%player%</white> is now AFK.",
        "afk_off" to "<gray>* <white>%player%</white> is back.",
        "afk_kick" to "<red>You were AFK for too long.",
        "suicide" to "<gray>%player% gave up.",
        "ping" to "<gray>Ping of <white>%player%</white>: <white>%ping%ms",
        "smite_done" to "<yellow>A lightning bolt struck <white>%player%</white>!",
        "extinguished" to "<green>Fire put out.",
        // staff
        "invsee_self" to "<red>Use your own inventory.",
        "tempban_done" to "<green><white>%player%</white> was banned for <white>%time%</white>: <gray>%reason%",
        "tempban_screen" to "<red><bold>You have been banned</bold>\n<gray>%reason%\n<white>Back in %time%",
        "tempban_unknown" to "<red>I couldn't find the player %player%.",
        "tempbanip_done" to "<green>The address <white>%ip%</white> was banned for <white>%time%</white>: <gray>%reason%",
        "tempbanip_unknown" to "<red>I couldn't find an online player or an IP address in <white>%target%</white>.",
        "jail_set" to "<green>Jail <white>%name%</white> set.",
        "jail_deleted" to "<green>Jail <white>%name%</white> deleted.",
        "jail_unknown" to "<red>There is no jail called <white>%name%</white>.",
        "jail_none" to "<red>No jails have been created (use /setjail <name>).",
        "jail_done" to "<green><white>%player%</white> was jailed in <white>%jail%</white> %time%.",
        "jail_you" to "<red>You have been jailed%reason%. <gray>%time%",
        "unjail_done" to "<green><white>%player%</white> was released.",
        "unjail_you" to "<green>You have been released.",
        "jail_not_jailed" to "<red>%player% is not jailed.",
        "jail_command" to "<red>Prisoners can't use that command.",
        "jails_list" to "<gold><bold>Jails</bold> <gray>(%count%): %jails%",
        "notes_none" to "<gray>No notes about <white>%player%</white>.",
        "notes_added" to "<green>Note saved about <white>%player%</white>.",
        "notes_line" to "<dark_gray>[%id%] <gray>%when% <white>%author%<gray>: %text%",
        "notes_cleared" to "<green>Notes about <white>%player%</white> deleted.",
        "sudo_done" to "<green>Command run as <white>%player%</white>.",
        "restart_started" to "<gold><bold>The server will restart in %time%.</bold>",
        "restart_warning" to "<gold>The server restarts in <white>%time%</white>.",
        "restart_cancelled" to "<green>Restart cancelled.",
        "restart_none" to "<red>There is no restart scheduled.",
        "commandspy_on" to "<green>Command spy on.",
        "commandspy_off" to "<gray>Command spy off.",
        "commandspy_line" to "<dark_gray>[<gray>cmd<dark_gray>] <gray>%player%: <white>/%command%",
        // utilidades
        "item_none" to "<red>Hold an item in your hand.",
        "item_named" to "<green>Item renamed.",
        "item_lore" to "<green>Item description updated.",
        "item_more" to "<green>Full stack.",
        "item_repaired" to "<green>Item repaired.",
        "item_not_damaged" to "<gray>That item is not damaged.",
        "repair_all" to "<green>%count% item(s) repaired.",
        "skull_given" to "<green>Head of <white>%player%</white> given.",
        "skull_copied" to "<green>Copied the head you are looking at into your inventory.",
        "skull_copy_none" to "<red>Look at a placed head (or at someone wearing one) to copy it.",
        // teleportes extras
        "homeother_going" to "<gray>Going to <white>%player%</white>'s home <white>%name%</white>...",
        "homeother_none" to "<red>%player% has no homes.",
        "homeother_unknown" to "<red>%player% doesn't have a home called <white>%name%</white>. Homes: <white>%homes%",
        "homeother_pick" to "<gray>%player% has several homes. Use <white>/homeother %player% <home></white>: %homes%",
        "tpaall_sent" to "<green>Teleport request sent to <white>%count%</white> player(s).",
        "tpaall_none" to "<red>There is nobody to call right now.",
        "tppos_done" to "<green>Teleported to <white>%x% %y% %z%</white>.",
        "tppos_invalid" to "<red>That position is outside the world.",
        "tpoffline_none" to "<red>I don't know where <white>%player%</white> was when they left.",
        "tpoffline_done" to "<gray>Going to where <white>%player%</white> is (or left)...",
        "firstspawn_set" to "<green>First-join spawn set here.",
        "firstspawn_deleted" to "<green>First-join spawn deleted (new players use /setspawn).",
        "firstspawn_none" to "<red>The first-join spawn has not been set yet (use /setfirstspawn).",
        "firstspawn_sent" to "<green><white>%player%</white> was sent to the first-join spawn.",
        // jogador extras
        "ignite_done" to "<gold><white>%player%</white> is on fire for <white>%seconds%s</white>!",
        "effects_none" to "<gray><white>%player%</white> has no active effects.",
        "effects_header" to "<gold><bold>Effects of %player%</bold> <gray>(%count%)",
        "effects_line" to "<gray>• <white>%effect% %level%</white> <dark_gray>(%time%)",
        "stat_title" to "<gold><bold>Server status</bold>",
        "stat_tps" to "<gray>TPS: %tps% <dark_gray>· <gray>MSPT: <white>%mspt%ms",
        "stat_uptime" to "<gray>Up for <white>%time%",
        "stat_memory" to "<gray>Memory: <white>%used%MB</white> used of <white>%allocated%MB</white> allocated <dark_gray>(max %max%MB)",
        "stat_world" to "<gray>Players: <white>%players%/%max%</white> <dark_gray>· <gray>Entities: <white>%entities%</white> <dark_gray>· <gray>Chunks: <white>%chunks%",
        "activetime_self" to "<gray>Your active time (not AFK): <white>%time%",
        "activetime_other" to "<gray>Active time of <white>%player%</white>: <white>%time%",
        "activetime_never" to "<red><white>%player%</white> has no active time recorded yet.",
        "activetime_set" to "<green>The active time of <white>%player%</white> is now <white>%time%</white>.",
        "activetime_top_header" to "<gold><bold>Most active time</bold> <gray>(top %count%)",
        "activetime_top_line" to "<gray>%index%. <white>%player%</white> <dark_gray>— <yellow>%time%",
        "activetime_top_empty" to "<gray>No active time has been recorded yet.",
        "bad_time" to "<red>Invalid time. Use 30s, 5m, 2h, 1d...",
        // equipe extras
        "checkjail_not" to "<gray><white>%player%</white> is not jailed.",
        "checkjail_title" to "<gold><bold>%player%</bold> <red>is jailed</red>",
        "checkjail_cell" to "<gray>Jail: <white>%jail%",
        "checkjail_by" to "<gray>Jailed by <white>%by%</white> on <white>%when%",
        "checkjail_time" to "<gray>Sentence: <white>%time%",
        "checkjail_reason" to "<gray>Reason: <white>%reason%",
        "doas_done" to "<green>Running <white>/%command%</white> as <white>%player%</white> (with your permission).",
        "powertool_none" to "<gray>You have no powertools. Hold an item and use <white>/powertool set <action> <command></white>.",
        "powertool_header" to "<gold><bold>Your powertools</bold>",
        "powertool_line" to "<gray>• <white>%item%</white> <dark_gray>[%action%]</dark_gray> <gray>runs <white>/%command%",
        "powertool_set" to "<green>Done: with <white>%item%</white>, the <white>%action%</white> gesture runs <white>/%command%</white>.",
        "powertool_removed" to "<green>Powertool of <white>%item%</white> deleted.",
        "powertool_nothing" to "<red><white>%item%</white> has no powertool.",
        "powertool_no_item" to "<red>Hold an item in your hand.",
        "powertool_bad_action" to "<red>Invalid gesture or command (<white>%action%</white>). Gestures: <white>%actions%",
        "powertool_toggle_on" to "<green>Powertools on.",
        "powertool_toggle_off" to "<gray>Powertools off (they stay saved).",
        "cooldown_wait" to "<red>Wait <white>%time%</white> before using <white>/%command%</white> again.",
        "cooldown_list_empty" to "<gray>There are no cooldown groups in the config (Essentials/config.json, \"cooldowns\").",
        "cooldown_list_header" to "<gold><bold>Command cooldowns</bold> <gray>(%state%<gray>)",
        "cooldown_list_line" to "%state% <white>%group%</white> <gray>— %seconds%s <dark_gray>(%commands%)",
        "cooldown_unknown_group" to "<red>There is no group called <white>%group%</white>. Groups: <white>%groups%",
        "cooldown_check_line" to "<gray><white>%player%</white> waits <white>%time%</white> in <white>%group%</white>.",
        "cooldown_check_none" to "<gray><white>%player%</white> has no cooldown to wait.",
        "cooldown_free" to "<gray><white>%player%</white> is free in <white>%group%</white>.",
        "cooldown_cleared" to "<green>Cooldown of <white>%player%</white> cleared (%count% group(s)).",
        "timebar_started" to "<green>Bar <white>#%id%</white> created, lasts <white>%time%</white>.",
        "timebar_none" to "<gray>There are no time bars running.",
        "timebar_list_header" to "<gold><bold>Time bars</bold> <gray>(%count%)",
        "timebar_list_line" to "<gray>#%id% <dark_gray>— <white>%left% left</white> <dark_gray>· at the end: <gray>%command%",
        "timebar_unknown" to "<red>There is no bar <white>#%id%</white>.",
        "timebar_cancelled" to "<green>Bar <white>#%id%</white> cancelled.",
        "timebar_cancelled_all" to "<green>%count% bar(s) cancelled.",
        "timebar_bad_option" to "<red>Invalid value: <white>%option%</white>. Use: <white>%values%",
        // notificações (menções)
        "notifications_title" to "<gold><bold>Notifications</bold> <gray>(a sound when someone mentions you)",
        "notifications_line" to "<gray>• %label%: <white>%value%",
        "notifications_on" to "<green>Notifications on.",
        "notifications_off" to "<gray>Notifications off.",
        "notifications_tested" to "<gray>Playing the sound...",
        "notifications_reset" to "<green>Notifications are back to the server default.",
        "notifications_value_set" to "<green>%label%: <white>%value%</white>.",
        // geral
        "player_unknown" to "<red>I couldn't find the player <white>%player%</white>.",
        "players_only" to "<red>Only players can use this command.",
        "no_number" to "<red>Invalid number."
    )

    fun load() {
        warned.clear()
        custom = ConfigFiles.load("Essentials", "messages.json", EssentialsMessages::class.java) { EssentialsMessages() }.messages
    }

    /** O texto da mensagem [key] com os %valores% trocados. */
    fun text(key: String, vars: Map<String, Any?> = emptyMap()): String {
        val template = custom[key] ?: DEFAULTS[key]
        if (template == null) {
            if (warned.add(key)) LOGGER.warn("Message without text: '{}'", key)
            return key
        }
        return Texts.fill(template, vars)
    }

    fun send(player: ServerPlayer, key: String, vars: Map<String, Any?> = emptyMap()) {
        val text = text(key, vars)
        if (text.isNotBlank()) player.sendSystemMessage(Texts.render(text))
    }

    fun reply(source: CommandSourceStack, key: String, vars: Map<String, Any?> = emptyMap(), broadcast: Boolean = false) {
        val text = text(key, vars)
        if (text.isNotBlank()) source.sendSuccess({ Texts.render(text) }, broadcast)
    }

    fun fail(source: CommandSourceStack, key: String, vars: Map<String, Any?> = emptyMap()) {
        val text = text(key, vars)
        if (text.isNotBlank()) source.sendFailure(Texts.render(text))
    }
}
