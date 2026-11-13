package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardDocs
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Calendar/config.json`: the settings shared by the monthly calendar, the weekly calendar and the login streak. */
@Doc(
    "Calendar · three reward menus that share this file's settings:\n" +
        "  MONTHLY   monthly.json  one reward per day of the month (/calendar monthly)\n" +
        "  WEEKLY    weekly.json   one reward per weekday, starting over every week (/calendar weekly)\n" +
        "  STREAK    streak.json   rewards for logging in several days in a row (/streak)\n" +
        "Every menu, icon, message and reward is edited in those files. Progress is stored in the ServerBuddy database.\n" +
        "\n" +
        "Players use /calendar (permission serverbuddy.calendar.use, on by default). Staff (serverbuddy.calendar.admin, default level 2):\n" +
        "  /calendar admin reset <player> <monthly|weekly|streak|all>     forget a player's claims\n" +
        "  /calendar admin streak <player> <days>                         set a player's login streak\n" +
        "  /calendar admin clock <yyyy-mm-dd|reset>                       make the calendar believe it is another date (testing, until restart)\n" +
        "\n" +
        RewardDocs.TEXT
)
class CalendarConfig(
    @Doc("false = the module does nothing.")
    val enabled: Boolean = true,
    @SerializedName("time_zone")
    @Doc("The time zone that decides when a day starts: \"America/Sao_Paulo\", \"Europe/Lisbon\", \"UTC\"... Empty = the server's time zone.")
    val timeZone: String = "",
    @SerializedName("week_start")
    @Doc("First day of the weekly calendar: monday, tuesday... sunday.")
    val weekStart: String = "monday",
    @SerializedName("default_view")
    @Doc("What plain /calendar opens: monthly, weekly or streak.")
    val defaultView: String = "monthly",
    @Doc("Reminder after joining.")
    val reminder: CalendarReminder = CalendarReminder(),
    val messages: CalendarMessages = CalendarMessages()
)

class CalendarReminder(
    @SerializedName("on_join")
    @Doc("true = a player who joins with something to claim gets the \"reminder\" message.")
    val onJoin: Boolean = true,
    @SerializedName("delay_ticks")
    @Doc("How long after joining the reminder arrives (20 ticks = 1 second).")
    val delayTicks: Int = 100
)

class CalendarMessages(
    @SerializedName("reward_claimed")
    @Doc("MiniMessage. Placeholders: %day% %rewards%.")
    val rewardClaimed: String = "<green>Reward for day <white>%day%</white> claimed: <white>%rewards%",
    @SerializedName("all_claimed")
    @Doc("After \"claim all\". Placeholders: %count% (days claimed).")
    val allClaimed: String = "<green>You claimed <white>%count%</white> reward(s).",
    @SerializedName("already_claimed")
    val alreadyClaimed: String = "<yellow>You already claimed that reward.",
    @SerializedName("not_available")
    val notAvailable: String = "<red>That reward is not available yet.",
    val missed: String = "<red>That day has passed: its reward can no longer be claimed.",
    @SerializedName("no_reward")
    val noReward: String = "<gray>There is no reward on that day.",
    @SerializedName("nothing_to_claim")
    val nothingToClaim: String = "<gray>There is nothing to claim right now.",
    @Doc("Sent after joining when something can be claimed. Placeholders: %count%.")
    val reminder: String = "<gold>You have <white>%count%</white> calendar reward(s) waiting! <gray>/calendar",
    @SerializedName("streak_started")
    val streakStarted: String = "<gray>Your login streak starts today. Come back tomorrow!",
    @SerializedName("streak_continued")
    @Doc("Placeholders: %streak% %best%.")
    val streakContinued: String = "<gold>Login streak: <white>%streak%</white> days in a row!",
    @SerializedName("streak_lost")
    @Doc("Placeholders: %old% (the streak that was lost).")
    val streakLost: String = "<red>You missed a day: your login streak of <white>%old%</white> days started over.",
    @SerializedName("module_off")
    val moduleOff: String = "<red>That calendar is turned off.",
    @Doc("How each state reads in an icon's %status% placeholder.")
    val status: CalendarStatusTexts = CalendarStatusTexts(),
    @SerializedName("reward_line")
    @Doc("One line of the %rewards% placeholder. Placeholder: %reward%.")
    val rewardLine: String = " <gray>• <white>%reward%"
)

class CalendarStatusTexts(
    val claimed: String = "<green>✔ Claimed",
    val claimable: String = "<yellow>Click to claim!",
    val missed: String = "<red>✘ Missed",
    val locked: String = "<gray>Not available yet",
    val empty: String = "<dark_gray>No reward"
)

/** `monthly.json` and `weekly.json`: a calendar with a reward (and a look) for every day. */
@Doc(
    "Calendar rewards · one cell per day. Fill \"days\" with the days that have rewards:\n" +
        "  monthly.json   the keys are the day of the month: \"1\" ... \"31\"\n" +
        "  weekly.json    the keys are the weekday: \"monday\" ... \"sunday\"\n" +
        "Days that are not listed get \"default_rewards\" (empty = no reward that day).\n" +
        "\n" +
        "claim_mode\n" +
        "  \"today\"     a reward can only be claimed ON its day; unclaimed past days are MISSED\n" +
        "  \"catch_up\"  past days of the current month/week stay claimable until the period ends\n" +
        "The progress resets by itself when the month (or week) changes.\n" +
        "\n" +
        "Icons: a day shows the first item reward as its icon (use_reward_icon) with the name and description of the state icon in\n" +
        "\"menu.icons\"; set \"icon\" on a day to choose the item yourself. Icon texts accept %day% %date% %weekday% %month% %year% %label%\n" +
        "%rewards% (one line per reward) and %status%.\n" +
        "\n" +
        RewardDocs.TEXT
)
class CalendarFile(
    @Doc("false = this calendar is closed.")
    val enabled: Boolean = true,
    @SerializedName("claim_mode")
    @Doc("today | catch_up (see the header).")
    val claimMode: String = "today",
    @SerializedName("use_reward_icon")
    @Doc("true = the cell shows the item of its first reward. false = always the state icon.")
    val useRewardIcon: Boolean = true,
    val menu: CalendarMenu = CalendarMenu(),
    @Doc("Days with their own rewards, look and text.")
    val days: Map<String, DayConfig> = emptyMap(),
    @SerializedName("default_rewards")
    @Doc("Rewards of the days not listed in \"days\".")
    val defaultRewards: List<Reward> = emptyList()
) {
    companion object {
        fun monthly(): CalendarFile {
            val days = LinkedHashMap<String, DayConfig>()
            for (day in 1..31) {
                days[day.toString()] = when {
                    day % 7 == 0 -> DayConfig(
                        label = "Weekly bonus",
                        rewards = listOf(Reward.of("item", "item" to "minecraft:diamond", "amount" to day / 7), Reward.of("xp", "amount" to 2, "levels" to true)),
                        lore = listOf("<gold>A big one!")
                    )
                    else -> DayConfig(rewards = listOf(Reward.of("item", "item" to "minecraft:bread", "amount" to 2 + day % 5)))
                }
            }
            return CalendarFile(menu = CalendarMenu.monthly(), days = days)
        }

        fun weekly(): CalendarFile {
            val days = LinkedHashMap<String, DayConfig>()
            val names = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            for ((i, name) in names.withIndex()) {
                days[name] = if (name == "sunday") {
                    DayConfig(label = "Sunday treasure", rewards = listOf(Reward.of("item", "item" to "minecraft:emerald", "amount" to 5), Reward.of("xp", "amount" to 3, "levels" to true)))
                } else {
                    DayConfig(rewards = listOf(Reward.of("item", "item" to "minecraft:golden_carrot", "amount" to 3 + i)))
                }
            }
            return CalendarFile(menu = CalendarMenu.weekly(), days = days)
        }
    }
}

/** A day of the calendar (or of the streak). */
class DayConfig(
    @Doc("Rewards given when the day is claimed.")
    val rewards: List<Reward> = emptyList(),
    @Doc("A name for the day, available as %label% in icon texts.")
    val label: String? = null,
    @Doc("Optional: the item shown for this day instead of the state's icon (name and description still come from the state icon).")
    val icon: ItemSpec? = null,
    @Doc("Extra description lines added under the state's description.")
    val lore: List<String> = emptyList()
)

/** `streak.json`: rewards for logging in several days in a row. */
@Doc(
    "Login streak · every day a player joins counts once. Joining on consecutive days grows the streak; missing more than\n" +
        "\"grace_days\" days starts it over. Each streak day has a reward in \"days\" (keys \"1\", \"2\"...).\n" +
        "\n" +
        "  cycle        true = after the last listed day the rewards start again from day 1 (day 8 gives the day-1 reward when 7 days are\n" +
        "               listed) and the menu shows the current cycle. false = days beyond the list give nothing.\n" +
        "  grace_days   days a player may skip without losing the streak (0 = they must join every day).\n" +
        "  auto_claim   true = the reward is given as soon as the streak grows, no click needed.\n" +
        "  open_on_join true = the menu opens by itself when a player joins with something to claim.\n" +
        "A reward can be claimed while its day is in the menu's current cycle; older unclaimed days are lost.\n" +
        "\n" +
        RewardDocs.TEXT
)
class StreakFile(
    val enabled: Boolean = true,
    val cycle: Boolean = true,
    @SerializedName("grace_days")
    val graceDays: Int = 0,
    @SerializedName("auto_claim")
    val autoClaim: Boolean = false,
    @SerializedName("open_on_join")
    val openOnJoin: Boolean = false,
    @SerializedName("use_reward_icon")
    val useRewardIcon: Boolean = true,
    val menu: CalendarMenu = CalendarMenu.streak(),
    @Doc("Rewards by streak day (\"1\" ... \"7\"). Texts and icons as in the calendars.")
    val days: Map<String, DayConfig> = defaultDays()
) {
    companion object {
        fun defaultDays(): Map<String, DayConfig> {
            val days = LinkedHashMap<String, DayConfig>()
            for (day in 1..6) days[day.toString()] = DayConfig(rewards = listOf(Reward.of("item", "item" to "minecraft:bread", "amount" to 2 + day)))
            days["7"] = DayConfig(
                label = "Streak bonus",
                rewards = listOf(Reward.of("item", "item" to "minecraft:diamond", "amount" to 2), Reward.of("xp", "amount" to 5, "levels" to true)),
                lore = listOf("<gold>Seven days in a row!")
            )
            return days
        }
    }
}

class CalendarMenu(
    @Doc("Title. Placeholders: %month% %year% %week% %streak% %best%.")
    val title: String = "<gold><bold>Calendar</bold> <dark_gray>» <white>%month% %year%",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 ... GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("show_player_inventory")
    @Doc("true = the player's inventory shows below. false = that part is emptied (the client still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: the item filling the lower part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("day_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the days appear, in order (day 1 first). A range works: \"9-39\".")
    val daySlots: List<Int> = (9..39).toList(),
    val fillers: List<MenuFiller> = listOf(MenuFiller(slots = (0..8).toList() + (40..53).toList(), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
    val buttons: CalendarButtons = CalendarButtons(),
    @Doc("The look of a day by state.")
    val icons: CalendarIcons = CalendarIcons()
) {
    companion object {
        private fun tabs(monthly: Boolean, weekly: Boolean, streak: Boolean, slots: Triple<Int, Int, Int>, close: Int, claimAll: Int, info: Int) = CalendarButtons(
            close = MenuButton(slots = listOf(close), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red>Close")),
            claimAll = MenuButton(
                slots = listOf(claimAll),
                icon = ItemSpec("minecraft:chest", name = "<green>Claim all", lore = listOf("<gray>Takes every reward that is ready.", "", "<white>%claimable%</white> ready"), glint = true)
            ),
            info = MenuButton(
                slots = listOf(info), hideWhenUnavailable = false,
                icon = ItemSpec("minecraft:clock", name = "<aqua>%title%", lore = listOf("<gray>Today: <white>%date%", "<gray>Ready to claim: <white>%claimable%", "<gray>Login streak: <white>%streak%</white> days (best <white>%best%</white>)"))
            ),
            monthly = MenuButton(enabled = monthly, slots = listOf(slots.first), hideWhenUnavailable = false, icon = ItemSpec("minecraft:book", name = "<yellow>Monthly calendar", lore = listOf("<gray>Click to open"))),
            weekly = MenuButton(enabled = weekly, slots = listOf(slots.second), hideWhenUnavailable = false, icon = ItemSpec("minecraft:writable_book", name = "<yellow>Weekly calendar", lore = listOf("<gray>Click to open"))),
            streak = MenuButton(enabled = streak, slots = listOf(slots.third), hideWhenUnavailable = false, icon = ItemSpec("minecraft:blaze_powder", name = "<yellow>Login streak", lore = listOf("<gray>Click to open")))
        )

        fun monthly() = CalendarMenu(
            title = "<gold><bold>Calendar</bold> <dark_gray>» <white>%month% %year%",
            buttons = tabs(monthly = false, weekly = true, streak = true, slots = Triple(45, 46, 47), close = 49, claimAll = 51, info = 4)
        )

        fun weekly() = CalendarMenu(
            title = "<gold><bold>Weekly calendar</bold> <dark_gray>» <white>week %week%",
            type = InventoryType.GENERIC_9x3,
            daySlots = (10..16).toList(),
            fillers = listOf(MenuFiller(slots = (0..9).toList() + (17..26).toList(), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
            buttons = tabs(monthly = true, weekly = false, streak = true, slots = Triple(18, 19, 20), close = 22, claimAll = 24, info = 4)
        )

        fun streak() = CalendarMenu(
            title = "<gold><bold>Login streak</bold> <dark_gray>» <white>%streak% days",
            type = InventoryType.GENERIC_9x3,
            daySlots = (10..16).toList(),
            fillers = listOf(MenuFiller(slots = (0..9).toList() + (17..26).toList(), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
            buttons = tabs(monthly = true, weekly = true, streak = false, slots = Triple(18, 19, 20), close = 22, claimAll = 24, info = 4)
        )
    }
}

class CalendarButtons(
    val close: MenuButton = MenuButton(),
    @SerializedName("claim_all")
    @Doc("Takes every ready reward. Placeholder: %claimable%.")
    val claimAll: MenuButton = MenuButton(),
    @Doc("Shows today's date and your progress (no click). Placeholders: %title% %date% %claimable% %streak% %best%.")
    val info: MenuButton = MenuButton(),
    @Doc("Tab: opens the monthly calendar (enabled = false hides it).")
    val monthly: MenuButton = MenuButton(),
    @Doc("Tab: opens the weekly calendar.")
    val weekly: MenuButton = MenuButton(),
    @Doc("Tab: opens the login streak.")
    val streak: MenuButton = MenuButton()
)

class CalendarIcons(
    @Doc("Already claimed. The day number is the item count.")
    val claimed: ItemSpec = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green>Day %day%", lore = listOf("%status%")),
    @Doc("Ready to claim (gets the enchanted glint).")
    val claimable: ItemSpec = ItemSpec("minecraft:chest", name = "<yellow><bold>Day %day%", lore = listOf("<gray>%label%", "%rewards%", "", "%status%")),
    @Doc("A day that was not claimed in time.")
    val missed: ItemSpec = ItemSpec("minecraft:red_stained_glass_pane", name = "<red>Day %day%", lore = listOf("%rewards%", "", "%status%")),
    @Doc("A day that has not arrived yet.")
    val locked: ItemSpec = ItemSpec("minecraft:gray_dye", name = "<gray>Day %day%", lore = listOf("<gray>%label%", "%rewards%", "", "%status%")),
    @Doc("A day without rewards.")
    val empty: ItemSpec = ItemSpec("minecraft:black_stained_glass_pane", name = "<dark_gray>Day %day%", lore = listOf("%status%"))
)
