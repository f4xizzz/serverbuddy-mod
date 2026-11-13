package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.SoundSpec
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardDocs
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Missions/config.json` */
@Doc(
    "Missions · Minecraft goals (and Cobblemon ones, as a bonus) with rewards\n" +
        "\n" +
        "HOW IT WORKS: missions live in Missions/categories/*.json (one file per category: beginner, daily, weekly...).\n" +
        "Each player opens the menu with /missions, follows their progress and claims the reward when a mission is done.\n" +
        "Minecraft progress comes from the game's own statistics (blocks broken, steps, mobs killed...) and only counts what\n" +
        "happens AFTER the mission starts for the player. Cobblemon missions (catches, battles...) are an extra bonus.\n" +
        "\n" +
        "OBJECTIVES, written as \"verb target amount\" (the target only for some verbs):\n" +
        "  mine minecraft:stone 100      break blocks (\"#minecraft:logs\" adds up a tag; \"any\" adds up all)\n" +
        "  craft minecraft:bread 20      craft      use minecraft:cooked_beef 10  use/eat/place    pickup / drop / break_item\n" +
        "  kill minecraft:zombie 20      kill (\"kill any 20\" = any mob)\n" +
        "  walk 5000   sprint   swim   fly   climb   boat   horse   fall      distance in blocks\n" +
        "  jump 100   deaths 3   damage 500   fish 5   breed 4   trade 3   enchant 2   sleep 3   open_chest 10   raid 1   player_kills 3\n" +
        "  play_time 30                  minutes played\n" +
        "  level 30                      reach an XP level (the current level counts)\n" +
        "  has minecraft:diamond 5       have items in the inventory (what you have right now counts)\n" +
        "  capture shiny 1   capture type:fire 5   capture pikachu 3   capture any 10   (Cobblemon; the Pokémon target is \"any\", the species,\n" +
        "                                \"shiny\", \"legendary\" or \"type:fire\"; defeat, battle_win, evolve and hatch work too)\n" +
        "  fossil any 1                  revive fossils        scan pikachu 3     scan Pokémon with the Pokédex     gained any 5   gain Pokémon\n" +
        "  berry oran 10                 harvest berries (\"any\" = any)      apricorn red 5   harvest apricorns (the color, or \"any\")\n" +
        "  candy cobblemon:rare_candy 3  use experience candy (\"any\" = any)      held_item cobblemon:leftovers 1   give an item to a Pokémon\n" +
        "  level_up any 5                Pokémon leveling up (5 times)      level_up pikachu@50 1   a Pikachu reaching level 50 or more\n" +
        "  pokefish any 3                fish Pokémon with the Cobblemon rod\n" +
        "  manual visit_spawn 1          progress reported by command: /missions progress <player> visit_spawn 1 (great for NPCs and command blocks)\n" +
        "\n" +
        "GLOBAL MISSIONS: a category with \"global\": true holds missions that apply to the whole server, one at a time, as a race: the\n" +
        "first player to finish gets the reward and the mission ends for everyone else. Turn it on in \"global_missions\" below (off by\n" +
        "default). Players see the active mission in the menu (/missions) and in /missions global; staff use /missions global start|stop.\n" +
        "\n" +
        "COMMANDS: /missions (menu), /missions list, /missions claim [mission], /missions global. Staff: /missions progress, complete, reset,\n" +
        "reload and global start|stop.\n" +
        "\n" +
        RewardDocs.TEXT
)
class MissionsConfig(
    @Doc("When the daily and weekly missions rotate.")
    val reset: MissionsReset = MissionsReset(),
    @SerializedName("auto_claim")
    @Doc("true = the reward is given the moment the mission is done (no click needed). Each mission can change this.")
    val autoClaim: Boolean = false,
    @Doc("Message when a mission is completed.")
    val notify: MissionsNotify = MissionsNotify(),
    @SerializedName("global_missions")
    @Doc("Global missions: one mission for the whole server at a time; whoever finishes first wins.")
    val global: GlobalMissionsSettings = GlobalMissionsSettings(),
    @Doc("The chest menu.")
    val menu: MissionsMenu = MissionsMenu(),
    @Doc("Textos (MiniMessage).")
    val messages: MissionsMessages = MissionsMessages()
)

class GlobalMissionsSettings(
    @Doc(
        "true = turns on global missions. From time to time the server picks a mission from the categories with \"global\": true, tells\n" +
            "everyone and starts the race: the first to finish gets the reward and the mission ends for the others. Off by default."
    )
    val enabled: Boolean = false,
    @Doc("How long passes between the end of one global mission and the start of the next (30m, 1h, 2h, 1d...). The first one starts after this time.")
    val interval: String = "1h",
    @SerializedName("time_limit")
    @Doc("How long a global mission lasts. When it passes, nobody wins and it ends. \"0\" = no limit.")
    val timeLimit: String = "30m",
    @Doc("Who takes part: \"online\" = whoever was online when the mission started; \"all\" = everyone, including those who join later.")
    val target: String = "online",
    @Doc(
        "How a player joins the mission: \"auto\" = joins by themselves and it counts right away; \"ui\" = they choose to take part by clicking the mission in\n" +
            "the menu (/missions) or with /missions global join, and progress only counts from then on."
    )
    val assignment: String = "auto",
    @Doc("Texts of the global missions (MiniMessage).")
    val messages: GlobalMissionsMessages = GlobalMissionsMessages()
)

class GlobalMissionsMessages(
    @Doc("To everyone, when a global mission starts (assignment \"auto\"). %mission%, %time% (how long it lasts; \"no limit\" if there is none).")
    val started: String = "<gold><bold>Global mission!</bold> <white>%mission%</white> <gray>— the first to finish gets the reward. Time: <white>%time%</white>. <white>/missions",
    @SerializedName("started_ui")
    @Doc("The same, when assignment is \"ui\" (each player chooses to take part).")
    val startedUi: String = "<gold><bold>Global mission!</bold> <white>%mission%</white> <gray>— the first to finish gets the reward. To take part use <white>/missions global join</white> or click it in <white>/missions</white>. Time: <white>%time%",
    @SerializedName("started_title")
    val startedTitle: String = "<gold>Global mission!",
    @SerializedName("started_subtitle")
    val startedSubtitle: String = "<white>%mission%",
    @Doc("To everyone, when someone finishes. %player%, %mission%.")
    val won: String = "<green><bold>%player%</bold> completed the global mission <white>%mission%</white> and won the reward! <gray>It is over for everyone else.",
    @SerializedName("won_title")
    @Doc("Title just for the winner.")
    val wonTitle: String = "<green>You won!",
    @SerializedName("won_subtitle")
    val wonSubtitle: String = "<white>%mission%",
    @Doc("To everyone, when time runs out with no winner. %mission%.")
    val expired: String = "<red>The global mission <white>%mission%</white> is over: nobody finished in time.",
    @Doc("To everyone, when the staff ends the mission with /missions global stop. %mission%.")
    val stopped: String = "<gray>The global mission <white>%mission%</white> was ended by the staff.",
    @Doc("When joining, if a global mission is active. %mission%, %time%. Empty text = no message.")
    val reminder: String = "<gold>Global mission active: <white>%mission%</white> <gray>— <white>%time%</white> left. <white>/missions",
    @Doc("When someone uses /missions global with no active mission. %time% = time until the next one.")
    val none: String = "<gray>There is no global mission right now. The next one starts in <white>%time%</white>.",
    @SerializedName("none_scheduled")
    val noneScheduled: String = "<gray>There is no global mission right now.",
    @Doc("When the player joins the global mission (assignment \"ui\").")
    val joined: String = "<green>You joined the global mission <white>%mission%</white>! Progress counts from now on.",
    @SerializedName("already_joined")
    val alreadyJoined: String = "<gray>You are already in the global mission.",
    @SerializedName("not_eligible")
    @Doc("When the player can't take part (was not online when it started, with target \"online\").")
    val notEligible: String = "<red>You can't take part in this global mission: it only counts for players who were online when it started.",
    @SerializedName("no_join_needed")
    val noJoinNeeded: String = "<gray>No need: with this setup you take part by yourself.",
    @Doc("Lines of /missions global. %mission%, %time%, %top% (who is ahead), %top_progress% and %progress% (yours).")
    val status: List<String> = listOf(
        "<gold><bold>Global mission</bold> <white>%mission%",
        "<gray>Ends in: <white>%time%",
        "<gray>Ahead: <white>%top% <gray>(%top_progress%)",
        "<gray>You: <white>%progress%"
    )
)

class MissionsReset(
    @Doc("Hour of the day (0 to 23, server time) when the daily missions rotate. The weekly ones rotate at that hour on the \"week_start\".")
    val hour: Int = 4,
    @SerializedName("week_start")
    @Doc("Day the weekly missions rotate: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY or SUNDAY.")
    val weekStart: String = "MONDAY"
)

class MissionsNotify(
    @Doc("Chat message.")
    val chat: Boolean = true,
    @Doc("Big title on screen.")
    val title: Boolean = true,
    @Doc("Sound (null = no sound).")
    val sound: SoundSpec? = SoundSpec("minecraft:entity.experience_orb.pickup", 1f, 1.2f)
)

class MissionsMenu(
    @Doc("Title. Values: %category% (open category), %page% and %pages%, %ready% (missions ready to claim).")
    val title: String = "<dark_purple><bold>Missions</bold> <dark_gray>» <white>%category% <gray>(%page%/%pages%)",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 up to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the player's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: the item that fills the lower part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("mission_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the missions appear (in order). Accepts a list or a range.")
    val missionSlots: List<Int> = (9..44).toList(),
    @SerializedName("category_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the category tabs appear (in order).")
    val categorySlots: List<Int> = (0..8).toList(),
    @Doc("Buttons.")
    val buttons: MissionsButtons = MissionsButtons(),
    @Doc("Background items.")
    val fillers: List<MenuFiller> = listOf(MenuFiller(slots = listOf(45, 46, 47, 50, 51, 52), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
    @Doc("Shown in place of the missions when the category is empty.")
    val empty: ItemSpec = ItemSpec("minecraft:barrier", name = "<gray>No missions here for now", lore = listOf("<dark_gray>Come back later!")),
    @SerializedName("empty_slot")
    val emptySlot: Int = 22,
    @Doc("Lines added at the end of each mission's description, depending on its state.")
    val states: MissionStates = MissionStates()
)

class MissionsButtons(
    val previous: MenuButton = MenuButton(slots = listOf(45), icon = ItemSpec("minecraft:arrow", name = "<yellow>Previous page", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))),
    val next: MenuButton = MenuButton(slots = listOf(53), icon = ItemSpec("minecraft:arrow", name = "<yellow>Next page", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))),
    val close: MenuButton = MenuButton(slots = listOf(49), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red>Close")),
    @SerializedName("claim_all")
    val claimAll: MenuButton = MenuButton(
        slots = listOf(48),
        icon = ItemSpec("minecraft:chest", name = "<green>Claim all", lore = listOf("<gray>Takes the rewards of every mission", "<gray>that is ready in this category.", "", "<white>%ready%</white> ready"), glint = true)
    )
)

class MissionStates(
    val active: List<String> = listOf("", "<yellow>In progress"),
    val ready: List<String> = listOf("", "<green><bold>Ready!</bold> <gray>Click to claim."),
    val claimed: List<String> = listOf("", "<dark_green>Completed"),
    val cooldown: List<String> = listOf("", "<gray>Available again in <white>%time%"),
    val locked: List<String> = listOf("", "<red>Locked: <gray>%requirement%"),
    @Doc("A global mission the player has not accepted yet (assignment \"ui\").")
    val offer: List<String> = listOf("", "<aqua><bold>Click to take part!</bold>"),
    @Doc("Extra lines of every global mission. %time% = time left, %top% = who is ahead, %top_progress% = their progress.")
    val global: List<String> = listOf("", "<gold><bold>Global mission</bold> <gray>— the first to finish gets the reward!", "<gray>Ends in <white>%time%", "<gray>Ahead: <white>%top% <gray>(%top_progress%)"),
    @Doc("Description headers. %objectives% and %rewards% are the lists.")
    val objectivesHeader: String = "<white>Objetivos:",
    @SerializedName("rewards_header")
    val rewardsHeader: String = "<white>Reward:",
    @SerializedName("objective_line")
    @Doc("One line per objective. %done% = ✔ or ▪, %name%, %current%, %amount% and %bar%.")
    val objectiveLine: String = " %done% <gray>%name%: <white>%current%</white>/<white>%amount% %bar%"
)

class MissionsMessages(
    @Doc("When a mission is done. %mission% = the mission's name.")
    val completed: String = "<green><bold>Mission complete!</bold> <white>%mission%</white> <gray>— open <white>/missions</white> to claim.",
    @SerializedName("completed_auto")
    @Doc("When it is done with the reward given right away (auto_claim).")
    val completedAuto: String = "<green><bold>Mission complete!</bold> <white>%mission%</white> <gray>— reward delivered.",
    @SerializedName("completed_title")
    val completedTitle: String = "<green>Mission complete!",
    @SerializedName("completed_subtitle")
    val completedSubtitle: String = "<white>%mission%",
    @Doc("When the player claims. %reward% = what they got.")
    val claimed: String = "<green>Reward claimed: <white>%reward%",
    @SerializedName("nothing_to_claim")
    val nothingToClaim: String = "<gray>You have no missions ready to claim.",
    @SerializedName("not_ready")
    val notReady: String = "<red>That mission is not ready yet.",
    @SerializedName("unknown_mission")
    val unknownMission: String = "<red>There is no mission '%mission%'.",
    @SerializedName("join_reminder")
    @Doc("When joining, if there are missions ready (null = no message). %ready% = how many.")
    val joinReminder: String? = "<gray>You have <green>%ready%</green> mission(s) ready to claim! <white>/missions"
)

enum class MissionPeriod { NONE, DAILY, WEEKLY }

/** Um arquivo de `Missions/categories/`. */
@Doc(
    "Mission category: the file name is the category id.\n" +
        "\n" +
        "PERIOD: \"none\" = the missions are always available; \"daily\" = they rotate every day; \"weekly\" = they rotate every week.\n" +
        "With \"pick\" above 0, each period only N missions of the list appear (the same for every player).\n" +
        "Each mission has \"objectives\" (see config.json), \"rewards\" and optionally:\n" +
        "  \"repeatable\": true + \"cooldown\": \"24h\"   can be done again after that time (30m, 2h, 7d...)\n" +
        "  \"auto_claim\": true                          gives the reward as soon as it is done\n" +
        "  \"requires\": { \"missions\": [\"other_mission\"], \"permission\": \"server.vip\" }   only appears after other missions / with a permission\n" +
        "  \"icon\": \"minecraft:iron_pickaxe\"           item shown in the menu"
)
class MissionCategory(
    @Doc("false = the category does not appear.")
    val enabled: Boolean = true,
    @Doc("Name of the tab in the menu (MiniMessage).")
    val name: String = "",
    @Doc("Item of the tab.")
    val icon: String = "minecraft:book",
    @Doc("Description of the tab.")
    val description: List<String> = emptyList(),
    @SerializedName("requires_mod")
    @Doc("Only appears if this mod is installed (e.g. \"cobblemon\"). null = always.")
    val requiresMod: String? = null,
    @Doc("none, daily ou weekly.")
    val period: MissionPeriod = MissionPeriod.NONE,
    @Doc(
        "true = GLOBAL MISSIONS category: its missions are only active when the server picks one (see \"global_missions\" in\n" +
            "config.json), apply to everyone at once and the first to finish gets the reward. \"period\" and \"pick\" don't apply here."
    )
    val global: Boolean = false,
    @Doc("How many missions of the list appear per period (0 = all).")
    val pick: Int = 0,
    @Doc("The missions: the key is the mission id.")
    val missions: Map<String, MissionDef> = emptyMap()
)

class MissionDef(
    val enabled: Boolean = true,
    val name: String = "",
    val description: List<String> = emptyList(),
    val icon: String = "minecraft:paper",
    val objectives: List<String> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val repeatable: Boolean = false,
    val cooldown: String? = null,
    @SerializedName("auto_claim")
    val autoClaim: Boolean? = null,
    val requires: MissionRequires? = null
)

class MissionRequires(
    val missions: List<String> = emptyList(),
    val permission: String? = null
)

/** As categorias que vêm prontas na primeira vez. */
object MissionDefaults {
    private fun m(
        name: String, description: String, icon: String, objectives: List<String>, rewards: List<Reward>,
        requires: List<String> = emptyList(), repeatable: Boolean = false, cooldown: String? = null
    ) = MissionDef(
        name = name, description = listOf(description), icon = icon, objectives = objectives, rewards = rewards,
        repeatable = repeatable, cooldown = cooldown, requires = if (requires.isEmpty()) null else MissionRequires(missions = requires)
    )

    private fun item(id: String, amount: Int) = Reward.of("item", "item" to id, "amount" to amount)
    private fun xp(levels: Int) = Reward.of("xp", "amount" to levels, "levels" to true)

    fun beginner() = MissionCategory(
        name = "<green>Beginner", icon = "minecraft:oak_sapling", description = listOf("<gray>Your first missions on the server."),
        missions = linkedMapOf(
            "first_steps" to m("<yellow>First steps", "Walk around the world a little.", "minecraft:leather_boots", listOf("walk 300"), listOf(xp(2), item("minecraft:bread", 8))),
            "lumberjack" to m("<yellow>Lumberjack", "Chop trees to gather wood.", "minecraft:iron_axe", listOf("mine #minecraft:logs 20"), listOf(item("minecraft:iron_axe", 1), xp(3)), requires = listOf("first_steps")),
            "miner" to m("<yellow>Miner", "Break stone and find coal.", "minecraft:stone_pickaxe", listOf("mine #minecraft:base_stone_overworld 64", "mine #minecraft:coal_ores 8"), listOf(item("minecraft:iron_pickaxe", 1), item("minecraft:torch", 32)), requires = listOf("lumberjack")),
            "hunter" to m("<yellow>Hunter", "Defeat hostile monsters and animals.", "minecraft:iron_sword", listOf("kill any 10"), listOf(item("minecraft:cooked_beef", 16), xp(4)), requires = listOf("miner"))
        )
    )

    fun daily() = MissionCategory(
        name = "<gold>Daily", icon = "minecraft:clock", description = listOf("<gray>Three new missions every day."),
        period = MissionPeriod.DAILY, pick = 3,
        missions = linkedMapOf(
            "daily_mining" to m("<yellow>Mining of the day", "Break 100 stone blocks.", "minecraft:stone_pickaxe", listOf("mine #minecraft:base_stone_overworld 100"), listOf(xp(4), item("minecraft:coal", 16))),
            "daily_hunt" to m("<yellow>Hunt of the day", "Defeat 15 monsters.", "minecraft:iron_sword", listOf("kill any 15"), listOf(xp(4), item("minecraft:golden_carrot", 8))),
            "fishing" to m("<yellow>Fishing", "Fish 3 times.", "minecraft:fishing_rod", listOf("fish 3"), listOf(xp(3), item("minecraft:cooked_salmon", 6))),
            "wanderer" to m("<yellow>Wanderer", "Walk 3000 blocks.", "minecraft:leather_boots", listOf("walk 3000"), listOf(xp(3), item("minecraft:bread", 12))),
            "farmer" to m("<yellow>Farmer", "Breed 4 animals.", "minecraft:wheat", listOf("breed 4"), listOf(xp(3), item("minecraft:hay_block", 2))),
            "cook" to m("<yellow>Cook", "Craft 10 bread.", "minecraft:bread", listOf("craft minecraft:bread 10"), listOf(xp(2), item("minecraft:cake", 1))),
            "trader" to m("<yellow>Trader", "Make 2 trades with villagers.", "minecraft:emerald", listOf("trade 2"), listOf(xp(3), item("minecraft:emerald", 2))),
            "explorer" to m("<yellow>Explorer", "Play for 20 minutes.", "minecraft:compass", listOf("play_time 20"), listOf(xp(2), item("minecraft:experience_bottle", 4)))
        )
    )

    fun weekly() = MissionCategory(
        name = "<light_purple>Weekly", icon = "minecraft:nether_star", description = listOf("<gray>Bigger challenges, bigger rewards."),
        period = MissionPeriod.WEEKLY, pick = 2,
        missions = linkedMapOf(
            "big_mining" to m("<yellow>Big mining", "Break 1500 stone blocks.", "minecraft:diamond_pickaxe", listOf("mine #minecraft:base_stone_overworld 1500"), listOf(xp(15), item("minecraft:diamond", 3))),
            "exterminator" to m("<yellow>Exterminator", "Defeat 150 monsters.", "minecraft:diamond_sword", listOf("kill any 150"), listOf(xp(15), item("minecraft:diamond", 3))),
            "marathon" to m("<yellow>Marathon", "Walk 30000 blocks.", "minecraft:diamond_boots", listOf("walk 30000"), listOf(xp(12), item("minecraft:golden_apple", 4))),
            "no_breaks" to m("<yellow>Non-stop", "Play for 5 hours.", "minecraft:clock", listOf("play_time 300"), listOf(xp(10), item("minecraft:experience_bottle", 16))),
            "trade_master" to m("<yellow>Trade master", "Make 20 trades with villagers.", "minecraft:emerald_block", listOf("trade 20"), listOf(xp(10), item("minecraft:emerald", 12)))
        )
    )

    /** Examples of global missions (they only run if "global_missions" is turned on). */
    fun globals() = MissionCategory(
        name = "<red>Global", icon = "minecraft:beacon", description = listOf("<gray>Races for the whole server:", "<gray>whoever finishes first wins."),
        global = true,
        missions = linkedMapOf(
            "coal_race" to m("<yellow>Coal race", "Be the first to break 40 coal ores.", "minecraft:coal", listOf("mine #minecraft:coal_ores 40"), listOf(item("minecraft:diamond", 4), xp(10))),
            "lightning_marathon" to m("<yellow>Lightning marathon", "Be the first to walk 2000 blocks.", "minecraft:diamond_boots", listOf("walk 2000"), listOf(item("minecraft:golden_apple", 6), xp(10))),
            "lightning_hunt" to m("<yellow>Lightning hunt", "Be the first to defeat 30 monsters.", "minecraft:diamond_sword", listOf("kill any 30"), listOf(item("minecraft:emerald", 12), xp(10)))
        )
    )

    fun globalPokemon() = MissionCategory(
        name = "<red>Global Pokémon", icon = "cobblemon:master_ball", description = listOf("<gray>Pokémon races for the whole server."),
        requiresMod = "cobblemon", global = true,
        missions = linkedMapOf(
            "capture_race" to m("<yellow>Capture race", "Be the first to capture 5 Pokémon.", "cobblemon:poke_ball", listOf("capture any 5"), listOf(item("cobblemon:ultra_ball", 10), xp(10))),
            "battle_race" to m("<yellow>Battle race", "Be the first to defeat 8 wild Pokémon.", "cobblemon:great_ball", listOf("defeat any 8"), listOf(item("cobblemon:rare_candy", 3), xp(10)))
        )
    )

    fun pokemon() = MissionCategory(
        name = "<aqua>Pokémon", icon = "cobblemon:poke_ball", description = listOf("<gray>Extra rewards for Pokémon trainers."),
        requiresMod = "cobblemon",
        missions = linkedMapOf(
            "catcher" to m("<yellow>Catcher", "Capture 5 Pokémon.", "cobblemon:poke_ball", listOf("capture any 5"), listOf(item("cobblemon:poke_ball", 10), xp(3)), repeatable = true, cooldown = "24h"),
            "shiny_hunter" to m("<yellow>Eagle eye", "Capture 1 shiny Pokémon.", "cobblemon:ultra_ball", listOf("capture shiny 1"), listOf(item("cobblemon:master_ball", 1))),
            "trainer" to m("<yellow>Trainer", "Win 5 battles against wild Pokémon.", "cobblemon:great_ball", listOf("defeat any 5"), listOf(item("cobblemon:great_ball", 8), xp(3)), repeatable = true, cooldown = "24h"),
            "duelist" to m("<yellow>Duelist", "Win 3 battles against players or trainers.", "cobblemon:ultra_ball", listOf("battle_win 3"), listOf(item("cobblemon:rare_candy", 2)), repeatable = true, cooldown = "48h"),
            "evolution" to m("<yellow>Evolution", "Evolve 1 Pokémon.", "cobblemon:link_cable", listOf("evolve 1"), listOf(item("cobblemon:rare_candy", 1)), repeatable = true, cooldown = "24h"),
            "hatcher" to m("<yellow>Hatcher", "Hatch 1 egg.", "cobblemon:pokemon_egg", listOf("hatch 1"), listOf(item("cobblemon:exp_candy_m", 3)), repeatable = true, cooldown = "24h")
        )
    )
}
