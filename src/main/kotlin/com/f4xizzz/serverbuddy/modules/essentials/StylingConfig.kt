package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/**
 * Formatos de chat, avisos de entrada/saída/morte/conquista, lista de jogadores (Tab), menções e placas (`styling` em
 * Essentials/config.json). Tudo vem desligado, menos as menções e as placas, porque mexer no chat de todo mundo é uma escolha do dono.
 */
class StylingSettings(
    @SerializedName("chat_format")
    @Doc("Chat line: prefix Name: message, by LuckPerms group.")
    val chatFormat: ChatFormatSettings = ChatFormatSettings(),
    @Doc("Join, leave, first join, death, advancement and /me messages.")
    val events: EventFormatSettings = EventFormatSettings(),
    @SerializedName("tab_list")
    @Doc("Header, footer and player names in the player list (Tab).")
    val tabList: TabListSettings = TabListSettings(),
    @Doc("Sound notification when a player is mentioned in chat (each player configures it with /notifications).")
    val mentions: MentionSettings = MentionSettings(),
    @Doc("Colors and formatting on signs.")
    val signs: SignSettings = SignSettings()
)

class ChatFormatSettings(
    @Doc("true = ServerBuddy formats chat using formats. false = vanilla Minecraft chat. Messages are sent as server messages and cannot be reported through the game menu. Other mods still receive chat events. Leave false if another chat mod (such as Styled Chat) already handles formatting.")
    val enabled: Boolean = false,
    @Doc("Formats by player type. Keys are LuckPerms groups (permission group.<name>) or conditions such as tag:vip, permission:my.node or !tag:newcomer. The first match in file order wins; default applies when none match. MiniMessage values: %player% (nickname or name), %name% (real name), %message%, %tag% (equipped /tags prefix), %afk% ([AFK] while idle), %world%. PlaceholderAPI placeholders also work, including %luckperms:prefix%, %luckperms:suffix% and %serverbuddy:nick%.")
    val formats: Map<String, String> = linkedMapOf(
        "default" to "%tag%<white>%player%</white><gray>:</gray> %message%",
        "admin" to "<red>[Admin]</red> %tag%<white>%player%</white><gray>:</gray> %message%"
    ),
    @SerializedName("message_formatting_permission")
    @Doc("Players with this permission (default: level 3) may use colors and formatting: &c, &l, &#ff8800 and MiniMessage tags such as <red>, <bold>, <gradient:...> and <rainbow>. Click, hover and command tags are not allowed. Other players send plain text.")
    val messageFormattingPermission: String = "serverbuddy.essentials.chat.format",
    @Doc("Replaces text fragments in chat. Keys are what players type (for example :shrug:).")
    val replacements: Map<String, String> = linkedMapOf(":shrug:" to "¯\\_(ツ)_/¯", ":coracao:" to "❤"),
    @SerializedName("respect_ignore")
    @Doc("true = players do not see chat messages from players they /ignore.")
    val respectIgnore: Boolean = true
)

class EventFormatSettings(
    @Doc("true = replace vanilla join, leave, death, advancement and /me messages. An empty string hides a message. Values: %player% (nickname or name), %name%, %tag%, %afk%, %world% and PlaceholderAPI placeholders. Leave false if another mod handles these messages.")
    val enabled: Boolean = false,
    @Doc("When a player joins. Values: %player% and %name%.")
    val join: String = "<green>+</green> <white>%player%</white> <yellow>joined!</yellow>",
    @Doc("When a player leaves. Values: %player% and %name%.")
    val leave: String = "<red>-</red> <white>%player%</white> <yellow>left!</yellow>",
    @SerializedName("first_join")
    @Doc("Broadcast when someone joins for the FIRST time. Empty text disables it. Values: %player% and %name%.")
    val firstJoin: String = "<light_purple>Welcome <white>%player%</white> to the server!</light_purple>",
    @Doc("Death message. Values: %player%, %name%, %message% (the vanilla Minecraft death message).")
    val death: String = "<gray>☠ %message%</gray>",
    @SerializedName("advancement_task")
    @Doc("Task advancement. Values: %player%, %title%, %description%.")
    val advancementTask: String = "<green>✔</green> <white>%player%</white> <gray>completed</gray> <green>%title%</green>",
    @SerializedName("advancement_goal")
    @Doc("Goal advancement. Values: %player%, %title%, %description%.")
    val advancementGoal: String = "<aqua>★</aqua> <white>%player%</white> <gray>reached the goal</gray> <aqua>%title%</aqua>",
    @SerializedName("advancement_challenge")
    @Doc("Challenge advancement. Values: %player%, %title%, %description%.")
    val advancementChallenge: String = "<light_purple>✦</light_purple> <white>%player%</white> <gray>completed the challenge</gray> <light_purple>%title%</light_purple>",
    @Doc("/me message. Values: %player% and %message%.")
    val emote: String = "<gray>👤 <white>%player%</white> <italic>%message%</italic></gray>"
)

class TabListSettings(
    val enabled: Boolean = false,
    @SerializedName("update_ticks")
    @Doc("Player list update interval in ticks (20 ticks = 1 second).")
    val updateTicks: Int = 40,
    @Doc("Header lines (MiniMessage; accepts %player%, %online%, %max%, %tps%, %ping% and PlaceholderAPI placeholders).")
    val header: List<String> = listOf("<gradient:#a855f7:#ec4899><bold>My Server</bold></gradient>"),
    @Doc("Footer lines (same values as the header).")
    val footer: List<String> = listOf("<gray>Players online: <white>%online%/%max%</white> <dark_gray>· <gray>TPS %tps%"),
    @SerializedName("player_format")
    @Doc("Player names in the list. Empty = vanilla display. Values: %player% (nickname or name), %name%, %tag%, %afk% ([AFK] while idle), %world% and PlaceholderAPI placeholders such as %luckperms:prefix%.")
    val playerFormat: String = "%afk%%player%"
)

class MentionSettings(
    val enabled: Boolean = true,
    @Doc("Default notification sound (players can change theirs with /notifications sound).")
    val sound: String = "minecraft:block.note_block.bell",
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    @SerializedName("afk_only")
    @Doc("true = only play the sound while AFK (player default; configurable with /notifications afkonly).")
    val afkOnly: Boolean = false
)

class SignSettings(
    @Doc("true = players with the permission below can format signs using &c codes and safe MiniMessage tags; click and command tags are not allowed.")
    val enabled: Boolean = true,
    @Doc("Permission to format signs (default: level 2).")
    val permission: String = "serverbuddy.essentials.sign.format"
)
