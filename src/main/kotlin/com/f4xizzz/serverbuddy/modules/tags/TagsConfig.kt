package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------------------------------------------
// Tags/config.json: how the system behaves, the menu and the messages.
// Tags/tags.json: the tag catalog (see TagsCatalog).
// ---------------------------------------------------------------------------------------------------------------

@Doc(
    "Tags · general config\n" +
        "A tag is a prefix the player picks in a menu (/tags) that shows up in chat, in the player list and above their\n" +
        "head. The tags themselves live in Tags/tags.json. This file holds the behavior, the menu and the messages.\n" +
        "Texts use MiniMessage (<red>, <gradient:#ff0000:#0000ff>, <bold>...)."
)
class TagsConfig(
    @Doc("How the tag reaches the player (LuckPerms, Minecraft team, placeholders).")
    val apply: TagsApplyConfig = TagsApplyConfig(),
    @Doc("Where the tags come from (ServerBuddy, GreatCosmetics or another mod) and the LuckPerms groups.")
    val integration: TagsIntegrationConfig = TagsIntegrationConfig(),
    @Doc("The chest menu of the tags.")
    val menu: TagsMenuConfig = TagsMenuConfig(),
    @Doc("Messages. Values: %tag% (tag name), %player%, %price%.")
    val messages: TagsMessages = TagsMessages()
)

class TagsApplyConfig(
    @SerializedName("luckperms")
    @Doc(
        "true = with LuckPerms installed, the equipped tag becomes the player's prefix in LuckPerms (and grants the tag's\n" +
            "permissions). That way it works with any chat plugin that shows the LuckPerms prefix."
    )
    val luckPerms: Boolean = true,
    @SerializedName("luckperms_weight")
    @Doc("Weight of the tag prefix in LuckPerms. It must be higher than the weight of the group prefixes for the tag to show up.")
    val luckPermsWeight: Int = 1000,
    @SerializedName("luckperms_format")
    @Doc(
        "How the prefix goes to LuckPerms: MINIMESSAGE (as you wrote it, with <gold>...: the chat plugin has to understand\n" +
            "MiniMessage) or LEGACY (§ color codes, which any chat plugin understands)."
    )
    val luckPermsFormat: Format = Format.MINIMESSAGE,
    @SerializedName("luckperms_persistent")
    @Doc("false (recommended) = the prefix only lasts while the player is online and is rebuilt when they join. true = saved in LuckPerms.")
    val luckPermsPersistent: Boolean = false,
    @SerializedName("team_prefix")
    @Doc(
        "true = also put the prefix on the player's scoreboard team: it shows in the player list, above the head and in the\n" +
            "normal Minecraft chat. Useful without LuckPerms. Careful: a player can only be in one team."
    )
    val teamPrefix: Boolean = false,
    @SerializedName("remove_on_lose")
    @Doc("true = if the player loses the right to the tag they are using (e.g. lost the permission), it is taken off automatically.")
    val removeOnLose: Boolean = true
) {
    enum class Format { MINIMESSAGE, LEGACY }
}

class TagsIntegrationConfig(
    @SerializedName(value = "source", alternate = ["greatcosmetics"])
    @Doc(
        "Where the tags come from:\n" +
            "AUTO = if GreatCosmetics is installed, the menu uses ITS tag catalog and ownership (so both mods show the same thing);\n" +
            "       without it, ServerBuddy's own tags (Tags/tags.json).\n" +
            "OWN = always ServerBuddy's own tags (and LuckPerms group tags), even when GreatCosmetics is installed.\n" +
            "GREATCOSMETICS = only GreatCosmetics' tags. ServerBuddy's own catalog and group tags are ignored; if GreatCosmetics is\n" +
            "       not installed, the tag system stays off.\n" +
            "DISABLED = ServerBuddy's tag system is completely off (no /tags command, no tag effects, no tag rewards), so another\n" +
            "       mod can provide /tags. (Turning the whole module off in modules.conf does the same.)"
    )
    val source: Source = Source.AUTO,
    @SerializedName("groups_as_tags")
    @Doc(
        "Every LuckPerms group becomes a tag (the group's prefix), which the player has while they are in the group. It lets\n" +
            "them pick the prefix of a lower rank. Only used with ServerBuddy's own catalog."
    )
    val groupsAsTags: GroupTagsConfig = GroupTagsConfig()
) {
    /** OFF is the old name of OWN (from when this option was "greatcosmetics": AUTO or OFF). */
    enum class Source { AUTO, OWN, GREATCOSMETICS, DISABLED, OFF }
}

class GroupTagsConfig(
    @Doc("Turns the group tags on.")
    val enabled: Boolean = false,
    @Doc("Groups that do NOT become a tag.")
    val blacklist: List<String> = listOf("default"),
    @Doc("Category of the group tags in the menu.")
    val category: String = "ranks",
    @SerializedName("show_prefix")
    @Doc(
        "true = in the menu a group tag is shown with the group's PREFIX (what players see in chat, e.g. [Admin]) instead of the\n" +
            "group's name (admin). If the group has no prefix, its name is shown."
    )
    val showPrefix: Boolean = true
)

class TagsMenuConfig(
    @Doc("Title. Values: %category% (open category), %page% and %pages%, %owned% (how many the player has) and %total%.")
    val title: String = "<dark_purple><bold>Tags</bold> <dark_gray>» <white>%category% <gray>(%page%/%pages%)",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 up to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the player's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: item that fills the bottom part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("confirm_purchase")
    @Doc("true = buying a tag takes two clicks (the first one only asks for confirmation), so nobody spends money by accident.")
    val confirmPurchase: Boolean = true,
    @SerializedName("tag_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the tags appear. More tags = more pages. List or range: [0, 1] or \"0-35\".")
    val tagSlots: List<Int> = (0..35).toList(),
    @SerializedName("category_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the category buttons go (the first is \"all\", then one per category). Few categories = few buttons.")
    val categorySlots: List<Int> = (36..44).toList(),
    @SerializedName("all_category")
    @Doc("The \"all\" button (icon and name).")
    val allCategory: CategoryButton = CategoryButton(name = "All", icon = ItemSpec(item = "minecraft:nether_star", name = "<yellow>All tags")),
    @Doc("Fixed buttons: previous/next (pages), close, and remove (takes off the tag the player is using).")
    val buttons: TagsButtons = TagsButtons(),
    @Doc("Menu background.")
    val fillers: List<MenuFiller> = listOf(MenuFiller(slots = listOf(46, 47, 50, 51, 52), icon = ItemSpec(item = "minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true))),
    @SerializedName("empty_icon")
    @Doc("Shown when there is no tag to show.")
    val emptyIcon: MenuButton = MenuButton(
        slots = listOf(22),
        icon = ItemSpec(item = "minecraft:barrier", name = "<red>No tags here", lore = listOf("<gray>Pick another category."))
    ),
    @SerializedName("locked_icon")
    @Doc("If not null, locked tags are shown with THIS item (the name and description stay the tag's). Good for hiding what the tag looks like.")
    val lockedIcon: String? = null,
    @Doc("Lines added to the tag's description, depending on its state. %hint% = how to get it; %price% = price.")
    val states: TagsStateText = TagsStateText()
)

class CategoryButton(
    @Doc("Category name (shown in the menu title as %category%).")
    val name: String = "",
    @Doc("Button icon.")
    val icon: ItemSpec = ItemSpec()
)

class TagsButtons(
    val previous: MenuButton = MenuButton(
        slots = listOf(45),
        icon = ItemSpec(item = "minecraft:arrow", name = "<yellow>Previous page", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))
    ),
    val next: MenuButton = MenuButton(
        slots = listOf(53),
        icon = ItemSpec(item = "minecraft:arrow", name = "<yellow>Next page", lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%"))
    ),
    val close: MenuButton = MenuButton(
        slots = listOf(49),
        hideWhenUnavailable = false,
        icon = ItemSpec(item = "minecraft:barrier", name = "<red>Close")
    ),
    @Doc("Takes off the tag the player is using. %tag% = its name.")
    val remove: MenuButton = MenuButton(
        slots = listOf(48),
        icon = ItemSpec(
            item = "minecraft:red_dye",
            name = "<red>Take off the tag",
            lore = listOf("<gray>Using now: <white>%tag%", "", "<yellow>Click to take it off")
        )
    )
)

class TagsStateText(
    @Doc("The tag the player is using right now.")
    val equipped: List<String> = listOf("", "<green><bold>✔ In use", "<gray>Click to take it off"),
    @Doc("A tag the player has and can equip.")
    val owned: List<String> = listOf("", "<yellow>Click to equip"),
    @Doc("A locked tag. %hint% comes from the tag's \"unlock.hint\" field.")
    val locked: List<String> = listOf("", "<red>✖ Locked", "%hint%"),
    @Doc("A tag for sale.")
    val purchasable: List<String> = listOf("", "<gold>Price: <yellow>%price%", "<yellow>Click to buy")
)

class TagsMessages(
    val equipped: String = "<green>You are now using the tag %tag%<green>!",
    val removed: String = "<yellow>You took off the tag %tag%<yellow>.",
    @SerializedName("not_owned")
    val notOwned: String = "<red>You do not have the tag %tag%<red> yet.",
    val bought: String = "<green>You bought the tag %tag%<green> for <yellow>%price%</yellow>!",
    @SerializedName("confirm_buy")
    val confirmBuy: String = "<yellow>Click the tag %tag%<yellow> again to confirm the purchase for <gold>%price%</gold>.",
    @SerializedName("not_enough_money")
    val notEnoughMoney: String = "<red>You need <yellow>%price%</yellow> to buy the tag %tag%<red>.",
    @SerializedName("no_economy")
    val noEconomy: String = "<red>There is no economy installed to buy tags.",
    val received: String = "<green>You received the tag %tag%<green>! Use <white>/tags</white> to equip it.",
    val lost: String = "<yellow>You no longer have the tag %tag%<yellow>; it was taken off.",
    @SerializedName("unknown_tag")
    val unknownTag: String = "<red>The tag '%tag%' does not exist.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>This command can only be used by a player."
)

// ---------------------------------------------------------------------------------------------------------------

/** Tags/tags.json */
@Doc(
    "Tags · catalog\n" +
        "Each tag has a key (its id, no spaces, used in commands). Minimal example:\n" +
        "  \"vip\": { \"name\": \"<gold>VIP\", \"prefix\": \"<gold>[VIP] \" }\n" +
        "HOW THE PLAYER GETS THE TAG (\"unlock\"):\n" +
        "  GIVEN       only when someone gives it (/serverbuddy tags give, missions, battle pass, shop...)  [default]\n" +
        "  FREE        everybody has it\n" +
        "  PERMISSION  while they have the permission in \"value\" (e.g. \"serverbuddy.tags.vip\")\n" +
        "  GROUP       while they are in the LuckPerms group in \"value\"\n" +
        "  PRICE       can be bought in the menu for \"price\" (uses the installed economy); after that it is theirs\n" +
        "After editing: /serverbuddy tags reload."
)
class TagsCatalog(
    @Doc("Categories (the menu tabs). The key is the id used in the tags' \"category\". May be empty: tags still show up in \"All\".")
    val categories: Map<String, CategoryButton> = linkedMapOf(
        "general" to CategoryButton("General", ItemSpec(item = "minecraft:name_tag", name = "<white>General")),
        "special" to CategoryButton("Special", ItemSpec(item = "minecraft:nether_star", name = "<gold>Special", glint = true)),
        "ranks" to CategoryButton("Ranks", ItemSpec(item = "minecraft:golden_helmet", name = "<aqua>Ranks"))
    ),
    @Doc("The tags.")
    val tags: Map<String, TagDef> = defaultTags()
) {
    companion object {
        fun defaultTags(): Map<String, TagDef> = linkedMapOf(
            "newbie" to TagDef(
                name = "<green>Newbie",
                description = listOf("<gray>Everybody starts here."),
                prefix = "<green>[Newbie] ",
                icon = ItemSpec(item = "minecraft:oak_sapling"),
                category = "general",
                unlock = TagUnlock(type = UnlockType.FREE)
            ),
            "champion" to TagDef(
                name = "<gradient:#f6d365:#fda085><bold>Champion</bold>",
                description = listOf("<gray>Given by the staff at events."),
                prefix = "<gradient:#f6d365:#fda085><bold>[Champion]</bold> ",
                icon = ItemSpec(item = "minecraft:gold_ingot", glint = true),
                category = "special",
                unlock = TagUnlock(type = UnlockType.GIVEN, hint = "<gray>Win a server event.")
            ),
            "patron" to TagDef(
                name = "<light_purple>Patron",
                description = listOf("<gray>For those who support the server."),
                prefix = "<light_purple>[Patron] ",
                icon = ItemSpec(item = "minecraft:amethyst_shard"),
                category = "special",
                unlock = TagUnlock(type = UnlockType.PERMISSION, value = "serverbuddy.tags.patron", hint = "<gray>Support the server to unlock it.")
            ),
            "veteran" to TagDef(
                name = "<aqua>Veteran",
                description = listOf("<gray>For those who have played for a long time."),
                prefix = "<aqua>[Veteran] ",
                icon = ItemSpec(item = "minecraft:diamond"),
                category = "general",
                unlock = TagUnlock(type = UnlockType.PRICE, price = 5000.0, hint = "<gray>Buy it in the menu.")
            )
        )
    }
}

enum class UnlockType { GIVEN, FREE, PERMISSION, GROUP, PRICE }

class TagDef(
    @Doc("The tag's name in the menu and in messages (MiniMessage).")
    val name: String = "",
    @Doc("Description in the menu (one line per list item).")
    val description: List<String> = emptyList(),
    @Doc("The prefix shown in chat and in the player list while the tag is equipped (MiniMessage). End it with a space.")
    val prefix: String = "",
    @Doc("Icon in the menu. If the icon has no name, the tag's name is used.")
    val icon: ItemSpec = ItemSpec(item = "minecraft:name_tag"),
    @Doc("Category (a key of \"categories\"). Empty = only shows up in \"All\".")
    val category: String = "",
    @Doc("How the player gets the tag.")
    val unlock: TagUnlock = TagUnlock(),
    @Doc("Permissions the player receives while the tag is equipped (needs LuckPerms). \"-node\" negates.")
    val permissions: List<String> = emptyList(),
    @SerializedName("minecraft_tag")
    @Doc("Minecraft scoreboard tag (/tag) the player receives while using this tag. Empty = none.")
    val minecraftTag: String = "",
    @Doc("Order in the menu (lower first).")
    val weight: Int = 0,
    @Doc("true = the tag only shows up in the menu for players who already have it.")
    val hidden: Boolean = false
)

class TagUnlock(
    @Doc("GIVEN, FREE, PERMISSION, GROUP or PRICE (see the top of the file).")
    val type: UnlockType = UnlockType.GIVEN,
    @Doc("PERMISSION: the permission node. GROUP: the LuckPerms group name.")
    val value: String = "",
    @Doc("PRICE: the price.")
    val price: Double = 0.0,
    @Doc("PRICE: economy name (impactor, cobbledollars, beconomy, pebbles). Empty = the first installed.")
    val currency: String = "",
    @Doc("Text shown on the locked tag explaining how to get it (MiniMessage).")
    val hint: String = ""
)
