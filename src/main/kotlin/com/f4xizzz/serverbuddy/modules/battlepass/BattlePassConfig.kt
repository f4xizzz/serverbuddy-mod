package com.f4xizzz.serverbuddy.modules.battlepass

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

/** `config/ServerBuddy/BattlePass/config.json` */
@Doc(
    "Battle Pass · seasons with levels and rewards (free track and premium track)\n" +
        "\n" +
        "HOW IT WORKS: during the season players earn pass XP (catching and battling Pokémon, playing, completing\n" +
        "missions or trivia...) and level up. Each level unlocks a free reward and a premium reward, which the player claims in the menu\n" +
        "(/battlepass or /bp). The rewards of each level live in BattlePass/tiers.json.\n" +
        "\n" +
        "XP SOURCES: \"xp\" (catch, defeat, evolve, hatch and trade Pokémon; needs Cobblemon; 0 turns a source off),\n" +
        "\"online\" (XP for time played, for servers without Cobblemon) and the \"battlepass_xp\" reward (used by missions, trivia, crates...).\n" +
        "\n" +
        "XP BOOSTS (\"boosts\"): multiply the XP earned. Each boost applies if ALL the conditions in \"when\" match (time:18:00-23:00,\n" +
        "day:sat,sun, date:2026-12-24..2026-12-26) and, if it has one, the player has the \"permission\". The biggest matching boost wins. Example:\n" +
        "  { \"when\": [\"day:sat,sun\"], \"multiplier\": 2.0 }          double XP on weekends\n" +
        "  { \"permission\": \"server.vip\", \"multiplier\": 1.5 }       VIPs earn 50% more\n" +
        "\n" +
        "PREMIUM: whoever has the premium pass also claims the premium track rewards. It comes from: buying with /battlepass premium (if\n" +
        "\"price\" > 0), having the \"premium.permission\" permission (e.g. via LuckPerms) or the command /battlepass premium give <player>.\n" +
        "\n" +
        "SEASONS: the first one starts by itself (\"auto_start\") and lasts \"days\" days. To switch seasons: /battlepass season start [days].\n" +
        "The progress of each season is separate.\n" +
        "\n" +
        "If you used the Great BattlePass mod, your config, your rewards and (with /battlepass import) the players' progress come along.\n" +
        "\n" +
        RewardDocs.TEXT
)
class BattlePassConfig(
    @Doc("How many levels the pass has (the rewards of each level live in tiers.json).")
    val levels: Int = 30,
    @Doc("Where the XP comes from.")
    val xp: BpXpConfig = BpXpConfig(),
    @Doc("Temporadas.")
    val season: BpSeasonConfig = BpSeasonConfig(),
    @Doc("Trilha premium.")
    val premium: BpPremiumConfig = BpPremiumConfig(),
    @SerializedName("auto_claim")
    @Doc("true = level rewards are given by themselves as soon as the player reaches them (no need to click in the menu).")
    val autoClaim: Boolean = false,
    @Doc("XP boosts (see the top of the file).")
    val boosts: List<BpBoost> = emptyList(),
    @Doc("Messages when leveling up.")
    val notify: BpNotify = BpNotify(),
    @Doc("The chest menu.")
    val menu: BpMenu = BpMenu(),
    @Doc("Textos (MiniMessage).")
    val messages: BpMessages = BpMessages()
)

class BpXpConfig(
    @Doc("XP per Pokémon caught (0 = disabled).")
    val catch: Int = 100,
    @Doc("XP per wild Pokémon defeated in battle.")
    val defeat: Int = 50,
    @Doc("XP per evolution.")
    val evolve: Int = 75,
    @Doc("XP por ovo chocado.")
    val hatch: Int = 50,
    @Doc("XP per Pokémon trade between players.")
    val trade: Int = 25,
    @Doc("XP just for playing (good for servers without Cobblemon).")
    val online: BpOnlineXp = BpOnlineXp(),
    @Doc("How much XP each level asks for.")
    val curve: BpCurve = BpCurve()
)

class BpOnlineXp(
    @SerializedName("every_minutes")
    @Doc("Every how many minutes played the player earns XP (0 = off).")
    val everyMinutes: Int = 0,
    @Doc("Quanto XP ganha a cada vez.")
    val xp: Int = 20
)

enum class CurveMode { FORMULA, MANUAL }

class BpCurve(
    @Doc("FORMULA = the XP of level N is xp_per_level × multiplier^(N-1). MANUAL = you write the XP of each level in \"manual\".")
    val mode: CurveMode = CurveMode.FORMULA,
    @SerializedName("xp_per_level")
    @Doc("FORMULA: XP to leave level 1.")
    val xpPerLevel: Int = 1000,
    @Doc("FORMULA: how much the requirement grows each level (1.0 = always the same; 1.1 = 10% more per level).")
    val multiplier: Double = 1.1,
    @Doc("MANUAL: the XP to REACH each level, e.g.: { \"2\": 1000, \"3\": 1200 }. A level that is missing uses the last value defined.")
    val manual: Map<String, Int> = emptyMap()
)

class BpSeasonConfig(
    @Doc("Length of a season, in days.")
    val days: Int = 60,
    @SerializedName("auto_start")
    @Doc("true = the first season starts by itself when the server starts.")
    val autoStart: Boolean = true,
    @SerializedName("auto_renew")
    @Doc("true = when a season ends, the next one starts by itself (fresh progress for everyone).")
    val autoRenew: Boolean = false
)

class BpPremiumConfig(
    @Doc("false = there is no premium track (only the free rewards).")
    val enabled: Boolean = true,
    @Doc("Price of the premium pass, to buy with /battlepass premium. 0 = can't be bought (only by permission or command).")
    val price: Double = 0.0,
    @Doc("Economy used for the purchase: \"\" (the first installed), \"impactor\" or \"impactor:currency\".")
    val economy: String = "",
    @Doc("Whoever has this permission has premium without buying. Empty = nobody.")
    val permission: String = "serverbuddy.battlepass.premium"
)

class BpBoost(
    @SerializedName("when")
    @Doc("Time conditions (all must match). Empty = always.")
    val conditions: List<String> = emptyList(),
    @Doc("Permission the player needs (null = anyone).")
    val permission: String? = null,
    @Doc("XP multiplier (2.0 = double).")
    val multiplier: Double = 1.0
)

class BpNotify(
    @Doc("Chat message when leveling up.")
    val chat: Boolean = true,
    @Doc("Big title on screen.")
    val title: Boolean = true,
    @Doc("Sound (null = no sound).")
    val sound: SoundSpec? = SoundSpec("minecraft:entity.player.levelup", 1f, 1.1f)
)

class BpMenu(
    @Doc("Title. Values: %level%, %xp%, %needed%, %season%.")
    val title: String = "<dark_purple><bold>Battle Pass</bold> <dark_gray>» <white>Level %level%",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 up to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x4,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the player's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: the item that fills the lower part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("premium_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the premium rewards appear (one slot per level on the page).")
    val premiumSlots: List<Int> = (0..8).toList(),
    @SerializedName("level_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the level markers appear (the same number of slots as the other two).")
    val levelSlots: List<Int> = (9..17).toList(),
    @SerializedName("free_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the free rewards appear.")
    val freeSlots: List<Int> = (18..26).toList(),
    @Doc("Buttons.")
    val buttons: BpButtons = BpButtons(),
    @Doc("Background items.")
    val fillers: List<MenuFiller> = listOf(MenuFiller(slots = listOf(28, 29, 33), icon = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
    @SerializedName("level_reached")
    @Doc("Marker of a level the player has already reached. The level number shows as the item count.")
    val levelReached: ItemSpec = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green>Level %level%", lore = listOf("<gray>Reached")),
    @SerializedName("level_current")
    @Doc("Marker of the current level.")
    val levelCurrent: ItemSpec = ItemSpec("minecraft:yellow_stained_glass_pane", name = "<yellow><bold>Level %level%", lore = listOf("<gray>You are here", "<white>%xp%</white><gray>/</gray><white>%needed%</white> <gray>XP"), glint = true),
    @SerializedName("level_locked")
    @Doc("Marker of a level that has not been reached yet.")
    val levelLocked: ItemSpec = ItemSpec("minecraft:red_stained_glass_pane", name = "<red>Level %level%", lore = listOf("<gray>Not reached yet")),
    @Doc("Lines at the end of each reward's description, depending on its state.")
    val states: BpStates = BpStates()
)

class BpButtons(
    val previous: MenuButton = MenuButton(slots = listOf(27), icon = ItemSpec("minecraft:arrow", name = "<yellow>Previous levels", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))),
    val next: MenuButton = MenuButton(slots = listOf(35), icon = ItemSpec("minecraft:arrow", name = "<yellow>Next levels", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))),
    val close: MenuButton = MenuButton(slots = listOf(34), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red>Close")),
    @SerializedName("claim_all")
    val claimAll: MenuButton = MenuButton(
        slots = listOf(30),
        icon = ItemSpec("minecraft:chest", name = "<green>Claim all", lore = listOf("<gray>Takes every reward that is unlocked.", "", "<white>%claimable%</white> ready"), glint = true)
    ),
    @Doc("Shows the progress (no click). Values: %level%, %xp%, %needed%, %season%, %time% (time left in the season).")
    val info: MenuButton = MenuButton(
        slots = listOf(31), hideWhenUnavailable = false,
        icon = ItemSpec(
            "minecraft:experience_bottle", name = "<aqua>Your progress",
            lore = listOf("<gray>Level: <white>%level%</white>/<white>%max%", "<gray>XP: <white>%xp%</white>/<white>%needed%", "<gray>Season <white>%season%</white> · ends in <white>%time%")
        )
    ),
    @Doc("Shows whether you have premium and, if the pass has a price, buys it when clicked.")
    val premium: MenuButton = MenuButton(
        slots = listOf(32), hideWhenUnavailable = false,
        icon = ItemSpec("minecraft:nether_star", name = "<gold>Premium pass", lore = listOf("%premium_status%"))
    )
)

class BpStates(
    val claimed: List<String> = listOf("", "<dark_green>✔ Claimed"),
    val claimable: List<String> = listOf("", "<green><bold>Click to claim!"),
    @SerializedName("locked_level")
    val lockedLevel: List<String> = listOf("", "<red>Reach level %level% to unlock"),
    @SerializedName("locked_premium")
    val lockedPremium: List<String> = listOf("", "<gold>Needs the premium pass"),
    @SerializedName("premium_active")
    @Doc("Text of the premium button when the player has premium.")
    val premiumActive: String = "<green>You have the premium pass!",
    @SerializedName("premium_buyable")
    @Doc("Text of the premium button when it can be bought. %price% = price.")
    val premiumBuyable: String = "<gray>Click to buy for <white>%price%",
    @SerializedName("premium_unavailable")
    @Doc("Text of the premium button when it can only be obtained by permission or command.")
    val premiumUnavailable: String = "<gray>Ask the server for the premium pass."
)

class BpMessages(
    @SerializedName("level_up")
    @Doc("When leveling up. %level% = the new level.")
    val levelUp: String = "<green><bold>Battle Pass:</bold> you reached <white>level %level%</white>! <gray>Open <white>/battlepass</white> to claim.",
    @SerializedName("level_up_title")
    val levelUpTitle: String = "<gold>Level %level%!",
    @SerializedName("level_up_subtitle")
    val levelUpSubtitle: String = "<gray>Battle Pass",
    @SerializedName("xp_gain")
    @Doc("Message for each XP earned (above the hotbar). null = no message. %xp% = how much was earned.")
    val xpGain: String? = null,
    @Doc("When claiming a reward. %reward% = what was earned, %level% = the level.")
    val claimed: String = "<green>Reward of level <white>%level%</white> claimed: <white>%reward%",
    @SerializedName("nothing_to_claim")
    val nothingToClaim: String = "<gray>You have no pass rewards to claim right now.",
    @SerializedName("not_reached")
    val notReached: String = "<red>You haven't reached level %level% yet.",
    @SerializedName("already_claimed")
    val alreadyClaimed: String = "<gray>You already claimed that reward.",
    @SerializedName("premium_required")
    val premiumRequired: String = "<gold>That reward is on the premium track. <gray>See <white>/battlepass premium</white>.",
    @SerializedName("premium_bought")
    val premiumBought: String = "<gold><bold>Premium pass activated!</bold> <gray>Have a great season!",
    @SerializedName("premium_already")
    val premiumAlready: String = "<gray>You already have the premium pass this season.",
    @SerializedName("premium_not_for_sale")
    val premiumNotForSale: String = "<gray>The premium pass is not for sale here. Ask the server staff.",
    @SerializedName("no_economy")
    val noEconomy: String = "<red>No economy is installed to charge for the pass.",
    @SerializedName("not_enough_money")
    val notEnoughMoney: String = "<red>You need %price% to buy the premium pass.",
    @SerializedName("season_inactive")
    val seasonInactive: String = "<gray>There is no Battle Pass season in progress.",
    @SerializedName("join_reminder")
    @Doc("When joining, if there are unlocked rewards (null = no message). %claimable% = how many.")
    val joinReminder: String? = "<gray>You have <green>%claimable%</green> Battle Pass reward(s) to claim! <white>/battlepass"
)

/** Os prêmios de um nível. */
class BpTier(
    val level: Int = 1,
    val free: List<Reward> = emptyList(),
    val premium: List<Reward> = emptyList()
)

/** `config/ServerBuddy/BattlePass/tiers.json` */
@Doc(
    "Battle Pass rewards, one per level. Each level has the free track (\"free\") and the premium one (\"premium\"), and each track is a\n" +
        "list of rewards (there can be several). Rewards are explained in config.json. A level that is not here gives no reward.\n" +
        "To change things quickly: copy a block, change the \"level\" and the rewards."
)
class BpTiersFile(
    val tiers: List<BpTier> = emptyList()
)

object BattlePassDefaults {
    private fun item(id: String, amount: Int) = Reward.of("item", "item" to id, "amount" to amount)
    private fun xp(levels: Int) = Reward.of("xp", "amount" to levels, "levels" to true)

    /** 30 níveis: o grátis dá itens simples, o premium dá o dobro e coisas melhores; níveis de múltiplo de 5 são mais generosos. */
    fun tiers(levels: Int = 30): BpTiersFile {
        val free = listOf("minecraft:bread", "minecraft:iron_ingot", "minecraft:cooked_beef", "minecraft:gold_ingot", "minecraft:experience_bottle")
        val premium = listOf("minecraft:golden_carrot", "minecraft:diamond", "minecraft:golden_apple", "minecraft:emerald", "minecraft:experience_bottle")
        val list = (1..levels).map { level ->
            val big = level % 5 == 0
            val index = (level - 1) % free.size
            BpTier(
                level,
                free = if (big) listOf(item("minecraft:iron_block", 1), xp(2)) else listOf(item(free[index], 4 + level / 3)),
                premium = if (big) listOf(item("minecraft:diamond_block", 1), xp(5)) else listOf(item(premium[index], 2 + level / 5))
            )
        }
        return BpTiersFile(list)
    }
}
