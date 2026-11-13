package com.f4xizzz.serverbuddy.modules.flan

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Flan/config.json` */
@Doc(
    "Flan · who can enter a claim and what they get on entering (needs Flan)\n" +
        "\n" +
        "The Flan claim menu gets FOUR new buttons:\n" +
        "  WHO CAN ENTER\n" +
        "  • Entry by permission: only players with one of the chosen permissions get in (for example serverbuddy.flan.vip, given by LuckPerms).\n" +
        "  • Entry by tag: only players with one of the chosen tags get in (the /tags tags, or scoreboard tags given with /tag).\n" +
        "  If a list is empty, that rule does not apply. With both filled in, whoever passes ANY of them gets in.\n" +
        "  The claim owner, whoever has group access in Flan with the \"can_stay\" permission, and operators always get in.\n" +
        "  Whoever can't enter is taken out by Flan itself, as with its \"can_stay\" permission.\n" +
        "  WHAT THEY GET ON ENTERING\n" +
        "  • On enter: give tag: whoever enters the claim gets these scoreboard tags (as if you ran /tag <player> add <tag>).\n" +
        "  • On enter: give permission: whoever enters gets these permissions, valid for everything that uses the Fabric permissions API\n" +
        "    (ServerBuddy's commands and those of mods that respect LuckPerms). It does not change the operator level.\n" +
        "  By default everything is taken away when the person leaves the claim or the server (only what the mod gave: a tag they already had is\n" +
        "  not removed); with \"remove_on_leave\": false in \"grants\" the given tag stays with them forever.\n" +
        "  Tip: combine with the command blocker (CommandBlock): a claim that gives the tag in_arena and a rule that blocks /back and /home\n" +
        "  for whoever has that tag.\n" +
        "  Who can use the \"On enter\" buttons: only whoever has the permission serverbuddy.flan.grants (default: operators level 3) AND\n" +
        "  can edit the claim. (If any owner could type permissions, they would give themselves powers.)\n" +
        "\n" +
        "WITHOUT THE MENU: /claimaccess (in the claim) opens the same screens, /claimaccess grant shows and changes what the claim gives, and\n" +
        "/claimaccess info shows everything about the claim you are in.\n" +
        "The rules of each claim live in Flan/restrictions.json (the mod writes it; you can edit it with the server stopped).\n" +
        "Permissions of this module: serverbuddy.flan.edit (use the entry buttons; default: everyone who can edit the claim),\n" +
        "serverbuddy.flan.grants (use the \"On enter\" buttons; default: operators level 3) and\n" +
        "serverbuddy.flan.bypass (enters any restricted claim; default: operators level 3)."
)
class FlanConfig(
    @Doc("Rule by permission.")
    val permissions: FlanPermissionRule = FlanPermissionRule(),
    @Doc("Regra por tag.")
    val tags: FlanTagRule = FlanTagRule(),
    @Doc("What the claim gives to whoever enters (tags and permissions).")
    val grants: FlanGrantsConfig = FlanGrantsConfig(),
    @Doc("The chest menu of the rules and the buttons in the Flan menu.")
    val menu: FlanMenu = FlanMenu(),
    @Doc("Textos (MiniMessage).")
    val messages: FlanMessages = FlanMessages()
)

class FlanPermissionRule(
    @SerializedName("allow_custom")
    @Doc("true = the owner types any permission. false = only those in the \"presets\" list (good for the server to control which exist).")
    val allowCustom: Boolean = true,
    @Doc("Ready-made permissions, shown as buttons in the menu (the player just clicks).")
    val presets: List<String> = listOf("serverbuddy.flan.vip", "serverbuddy.flan.staff"),
    @SerializedName("max_entries")
    @Doc("How many permissions each claim can ask for.")
    val maxEntries: Int = 5
)

class FlanTagRule(
    @SerializedName("scoreboard_tags")
    @Doc("true = also counts scoreboard tags (/tag <player> add name) besides the /tags ones.")
    val scoreboardTags: Boolean = true,
    @SerializedName("max_entries")
    @Doc("Maximum required tags per claim.")
    val maxEntries: Int = 5
)

class FlanGrantsConfig(
    @SerializedName("remove_on_leave")
    @Doc(
        "true = the given tag disappears when the person leaves the claim (or the server); only the one the mod gave goes away, never one they already had.\n" +
            "false = the given tag stays with the person forever (it only goes away with /tag <player> remove <tag>).\n" +
            "Given permissions only apply while the person is inside the claim, in both options."
    )
    val removeOnLeave: Boolean = true,
    @SerializedName("check_interval_ticks")
    @Doc("Every how many ticks the mod checks who entered or left a claim (20 ticks = 1 second). Lower = reacts faster.")
    val checkIntervalTicks: Int = 10,
    @SerializedName("tag_presets")
    @Doc("Ready-made tags shown as buttons on the \"On enter: give tag\" screen (the person just clicks). E.g.: [\"in_arena\", \"pvp_zone\"].")
    val tagPresets: List<String> = listOf("in_arena"),
    @SerializedName("allow_custom_tags")
    @Doc("true = whoever can edit types any tag name. false = only those in \"tag_presets\".")
    val allowCustomTags: Boolean = true,
    @SerializedName("max_tags")
    @Doc("Maximum granted tags per claim.")
    val maxTags: Int = 5,
    @SerializedName("permission_presets")
    @Doc("Ready-made permissions shown as buttons on the \"On enter: give permission\" screen. E.g.: [\"myserver.fly\", \"essentials.fly\"].")
    val permissionPresets: List<String> = emptyList(),
    @SerializedName("allow_custom_permissions")
    @Doc("true = whoever can edit types any permission. false = only those in \"permission_presets\" (the safest).")
    val allowCustomPermissions: Boolean = true,
    @SerializedName("max_permissions")
    @Doc("How many permissions each claim can give.")
    val maxPermissions: Int = 5
)

class FlanMenu(
    @SerializedName("permission_slot")
    @Doc("Position of the \"Entry by permission\" button in the Flan menu (the menu has 18 slots; 12 to 15 were empty).")
    val permissionSlot: Int = 12,
    @SerializedName("tag_slot")
    @Doc("Position of the \"Entry by tag\" button in the Flan menu.")
    val tagSlot: Int = 13,
    @SerializedName("grant_permission_slot")
    @Doc("Position of the \"On enter: give permission\" button in the Flan menu.")
    val grantPermissionSlot: Int = 14,
    @SerializedName("grant_tag_slot")
    @Doc("Position of the \"On enter: give tag\" button in the Flan menu.")
    val grantTagSlot: Int = 15,
    @SerializedName("permission_button")
    @Doc("Icon of the permissions button. %count% = how many, %entries% = the list.")
    val permissionButton: ItemSpec = ItemSpec(
        "minecraft:name_tag", name = "<gold>Entry by permission",
        lore = listOf("<gray>Only players with one of the", "<gray>chosen permissions enter the claim.", "", "%entries%", "", "<yellow>Click to edit")
    ),
    @SerializedName("tag_button")
    @Doc("Icon of the tags button. %count% = how many, %entries% = the list.")
    val tagButton: ItemSpec = ItemSpec(
        "minecraft:paper", name = "<gold>Entry by tag",
        lore = listOf("<gray>Only players with one of the", "<gray>chosen tags enter the claim.", "", "%entries%", "", "<yellow>Click to edit")
    ),
    @SerializedName("grant_permission_button")
    @Doc("Icon of the \"On enter: give permission\" button. %count% = how many, %entries% = the list.")
    val grantPermissionButton: ItemSpec = ItemSpec(
        "minecraft:golden_carrot", name = "<aqua>On enter: give permission",
        lore = listOf("<gray>Whoever enters the claim gets these", "<gray>permissions while they are inside.", "", "%entries%", "", "<yellow>Click to edit <dark_gray>(staff only)")
    ),
    @SerializedName("grant_tag_button")
    @Doc("Icon of the \"On enter: give tag\" button. %count% = how many, %entries% = the list.")
    val grantTagButton: ItemSpec = ItemSpec(
        "minecraft:experience_bottle", name = "<aqua>On enter: give tag",
        lore = listOf("<gray>Whoever enters the claim gets these", "<gray>tags while they are inside.", "", "%entries%", "", "<yellow>Click to edit <dark_gray>(staff only)")
    ),
    @SerializedName("type")
    @Doc("Chest size of the edit screens: GENERIC_9x3 up to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x4,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the player's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: the item that fills the lower part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("entry_icon")
    @Doc("Item of each rule in the list (click to remove).")
    val entryIcon: ItemSpec = ItemSpec("minecraft:lime_dye", name = "<green>%entry%", lore = listOf("<red>Click to remove")),
    @SerializedName("add_icon")
    val addIcon: ItemSpec = ItemSpec("minecraft:emerald", name = "<green>Add", lore = listOf("<gray>Pick or type a new rule.")),
    @SerializedName("clear_icon")
    val clearIcon: ItemSpec = ItemSpec("minecraft:tnt", name = "<red>Clear all", lore = listOf("<gray>Deletes every rule in this list.", "<gray>(anyone can enter again)")),
    @SerializedName("back_icon")
    val backIcon: ItemSpec = ItemSpec("minecraft:arrow", name = "<yellow>Back"),
    @SerializedName("close_icon")
    val closeIcon: ItemSpec = ItemSpec("minecraft:barrier", name = "<red>Close")
)

class FlanMessages(
    @SerializedName("permissions_title")
    val permissionsTitle: String = "<dark_purple>Claim <dark_gray>» <white>Entry by permission",
    @SerializedName("tags_title")
    val tagsTitle: String = "<dark_purple>Claim <dark_gray>» <white>Entry by tag",
    @SerializedName("grant_permissions_title")
    val grantPermissionsTitle: String = "<dark_purple>Claim <dark_gray>» <white>On enter: give permission",
    @SerializedName("grant_tags_title")
    val grantTagsTitle: String = "<dark_purple>Claim <dark_gray>» <white>On enter: give tag",
    @SerializedName("pick_tag_title")
    val pickTagTitle: String = "<dark_purple>Claim <dark_gray>» <white>Pick a tag",
    @SerializedName("type_permission_title")
    val typePermissionTitle: String = "Type the permission",
    @SerializedName("type_tag_title")
    val typeTagTitle: String = "Type the tag name",
    @SerializedName("none")
    @Doc("Shown on the buttons when the list is empty.")
    val none: String = "<gray>No rules: anyone can enter.",
    @SerializedName("grant_none")
    @Doc("Shown on the \"On enter\" buttons when the list is empty.")
    val grantNone: String = "<gray>It gives nothing to whoever enters.",
    @SerializedName("grants_not_allowed")
    @Doc("For whoever clicks the \"On enter\" buttons without the permission serverbuddy.flan.grants.")
    val grantsNotAllowed: String = "<red>Only the staff can choose what the claim gives to whoever enters.",
    @SerializedName("invalid")
    @Doc("A value that doesn't work (empty, with a space or, for tags given on entering, with characters Minecraft's /tag doesn't accept).")
    val invalid: String = "<red>Invalid value. Use letters, numbers and _ . + - (no spaces).",
    @Doc("Message (above the hotbar) for whoever is stopped from entering. %rule% = what the claim asks for.")
    val denied: String = "<red>This claim only accepts whoever has: <white>%rule%",
    @SerializedName("added")
    val added: String = "<green>Rule <white>%entry%</white> added.",
    @SerializedName("removed")
    val removed: String = "<gray>Rule <white>%entry%</white> removed.",
    @SerializedName("cleared")
    val cleared: String = "<gray>List cleared.",
    @SerializedName("duplicate")
    val duplicate: String = "<red>That rule is already in the list.",
    @SerializedName("limit")
    val limit: String = "<red>This claim already has the maximum of %max% rules in this list.",
    @SerializedName("not_allowed")
    val notAllowed: String = "<red>You can't edit this claim.",
    @SerializedName("custom_blocked")
    val customBlocked: String = "<red>This server only accepts the ready-made permissions in the list.",
    @SerializedName("no_claim")
    val noClaim: String = "<red>You are not inside a claim.",
    @SerializedName("info_header")
    val infoHeader: String = "<gold><bold>Claim entry</bold> <gray>(%owner%)",
    @SerializedName("info_none")
    val infoNone: String = "<gray>No rules: anyone can enter (Flan's normal rules apply)."
)
