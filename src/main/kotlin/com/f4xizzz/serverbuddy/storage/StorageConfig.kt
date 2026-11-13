package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.GenericClickType
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * Config do /storage (`config/ServerBuddy/Storage/config.json`). Todos os textos são MiniMessage e todos os ícones usam
 * a mesma definição de item dos outros menus (item, name, lore, custom_model_data, hide_tooltip...).
 *
 * Textos do título aceitam %tab%, %page%, %pages%, %amount% (itens da aba) e %total% (tudo que o jogador tem).
 */
@Doc(
    "/storage · reward chest\n" +
        "Every player has their own. /redeem (and other modules) store items and commands here, and the player claims them\n" +
        "by clicking. Texts use MiniMessage (<red>, <gradient:red:blue>, <bold>...) and the icons are editable items:\n" +
        "  { \"item\": \"minecraft:chest\", \"name\": \"<yellow>Name\", \"lore\": [\"<gray>line\"], \"custom_model_data\": null,\n" +
        "    \"stackable\": true, \"hide_tooltip\": false, \"hide_additional_tooltip\": false, \"glint\": null }"
)
class StorageConfig(
    @Doc("Menu title. Values: %tab% (tab), %page% and %pages% (page), %amount% (items in the tab), %total% (everything).")
    val title: String = "<dark_purple><bold>Storage</bold> <dark_gray>» <white>%tab% <gray>(%page%/%pages%)",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 up to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("show_player_inventory")
    @Doc(
        "true = shows the player's inventory below the chest (normal).\n" +
            "false = empties that part and blocks clicks on it. (Minecraft always draws the inventory slots;\n" +
            "only a resource pack removes them. What disappears are the items.)"
    )
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: the item that fills the lower part (e.g. a glass pane). null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("redeem_to_storage")
    @Doc("true = when a player redeems a code (/redeem), the rewards go to /storage. false = delivered right away.")
    val redeemToStorage: Boolean = true,
    @SerializedName("notify_on_join")
    @Doc("Tells the player in chat when they join with rewards waiting.")
    val notifyOnJoin: Boolean = true,
    @SerializedName("notify_on_receive")
    @Doc("Tells the player in chat when a new reward arrives.")
    val notifyOnReceive: Boolean = true,
    @SerializedName("close_on_claim")
    @Doc("Closes the menu after claiming a reward.")
    val closeOnClaim: Boolean = false,
    @SerializedName("default_tab")
    @Doc("Tab that opens first: \"items\" ou \"commands\".")
    val defaultTab: String = "items",
    @Doc("Which clicks claim the reward (ANY_LEFT_CLICK, ANY_RIGHT_CLICK, LEFT, RIGHT, SHIFT_LEFT...).")
    val click: ClickOptions = ClickOptions(),
    @SerializedName("entry_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Positions (0 is the top left corner) where the rewards appear. More rewards = more pages.\nAccepts a list or a range: [0, 1, 2] or \"0-44\".")
    val entrySlots: List<Int> = (0..44).toList(),
    @Doc("The tabs. The key is the type (\"items\" ou \"commands\"); slots = where the button is; icon and selected_icon = normal icon and the open tab's icon (%amount% = how many rewards).")
    val tabs: Map<String, StorageTab> = defaultTabs(),
    @Doc("Fixed buttons: previous/next (pages), close and claim_all. hide_when_unavailable hides the button when it doesn't apply.")
    val buttons: StorageButtons = StorageButtons(),
    @Doc("Menu background: each item is repeated in the listed slots.")
    val fillers: List<MenuFiller> = listOf(defaultFiller()),
    @SerializedName("empty_icon")
    @Doc("Shown when the tab is empty.")
    val emptyIcon: MenuButton = MenuButton(
        slots = listOf(22),
        icon = ItemSpec(
            item = "minecraft:barrier",
            name = "<red>Nothing here",
            lore = listOf("<gray>When you earn rewards,", "<gray>they show up in this tab.")
        )
    ),
    @SerializedName("item_entry")
    @Doc("How an item reward looks: extra_lore is added to the item's description (%source% = where it came from).")
    val itemEntry: ItemEntryConfig = ItemEntryConfig(),
    @SerializedName("command_entry")
    @Doc("How a command reward looks: icon (with %title%, %lore% and %source%) and the default title.")
    val commandEntry: CommandEntryConfig = CommandEntryConfig(),
    @Doc("All the messages of /storage.")
    val messages: StorageMessages = StorageMessages()
) {
    class ClickOptions(
        val claim: List<GenericClickType> = listOf(GenericClickType.ANY_LEFT_CLICK)
    )

    companion object {
        fun defaultTabs(): Map<String, StorageTab> = linkedMapOf(
            "items" to StorageTab(
                slots = listOf(47),
                title = "Items",
                icon = ItemSpec(
                    item = "minecraft:chest",
                    name = "<yellow>Items <gray>(%amount%)",
                    lore = listOf("<gray>Click to see the items", "<gray>you haven't claimed yet.")
                ),
                selectedIcon = ItemSpec(
                    item = "minecraft:chest",
                    name = "<green><bold>Items <gray>(%amount%)",
                    lore = listOf("<gray>You are looking at this tab."),
                    glint = true
                )
            ),
            "commands" to StorageTab(
                slots = listOf(48),
                title = "Commands",
                icon = ItemSpec(
                    item = "minecraft:command_block",
                    name = "<yellow>Commands <gray>(%amount%)",
                    lore = listOf("<gray>Click to see the rewards", "<gray>that run commands.")
                ),
                selectedIcon = ItemSpec(
                    item = "minecraft:command_block",
                    name = "<green><bold>Commands <gray>(%amount%)",
                    lore = listOf("<gray>You are looking at this tab."),
                    glint = true
                )
            )
        )

        fun defaultFiller(): MenuFiller = MenuFiller(
            slots = listOf(46, 50, 52),
            icon = ItemSpec(item = "minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true)
        )
    }
}

class StorageTab(
    @JsonAdapter(SlotsAdapter::class)
    val slots: List<Int> = emptyList(),
    val title: String = "Itens",
    val icon: ItemSpec = ItemSpec(),
    @SerializedName("selected_icon")
    val selectedIcon: ItemSpec? = null
)

class StorageButtons(
    val previous: MenuButton = MenuButton(
        slots = listOf(45),
        icon = ItemSpec(
            item = "minecraft:arrow",
            name = "<yellow>Previous page",
            lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%")
        )
    ),
    val next: MenuButton = MenuButton(
        slots = listOf(53),
        icon = ItemSpec(
            item = "minecraft:arrow",
            name = "<yellow>Next page",
            lore = listOf("<gray>Page <white>%page%</white> of <white>%pages%")
        )
    ),
    val close: MenuButton = MenuButton(
        slots = listOf(49),
        hideWhenUnavailable = false,
        icon = ItemSpec(item = "minecraft:barrier", name = "<red>Close")
    ),
    @SerializedName("claim_all")
    val claimAll: MenuButton = MenuButton(
        slots = listOf(51),
        icon = ItemSpec(
            item = "minecraft:hopper",
            name = "<green><bold>Claim all",
            lore = listOf("<gray>Claims everything in the current tab.", "", "<yellow>Click to claim")
        )
    )
)

class ItemEntryConfig(
    /** Lines added to the item's description in the menu. Lines with %source% disappear when the reward has no source. */
    @SerializedName("extra_lore")
    val extraLore: List<String> = listOf("", "<gray>Source: <white>%source%", "<green>Click to claim")
)

class CommandEntryConfig(
    /** Icon of command rewards. %title% and %lore% come from the reward itself. */
    val icon: ItemSpec = ItemSpec(
        item = "minecraft:command_block",
        name = "%title%",
        lore = listOf("%lore%", "", "<gray>Source: <white>%source%", "<green>Click to claim")
    ),
    @SerializedName("default_title")
    val defaultTitle: String = "<yellow>Reward"
)

class StorageMessages(
    val received: String = "<green>You received <yellow>%amount%</yellow> reward(s) in <gold>/storage</gold>.",
    val pending: String = "<green>You have <yellow>%amount%</yellow> reward(s) waiting in <gold>/storage</gold>.",
    @SerializedName("claimed_item")
    val claimedItem: String = "<green>You claimed <yellow>%amount%x</yellow> %item%<green>.",
    @SerializedName("claimed_command")
    val claimedCommand: String = "<green>You claimed: %title%",
    @SerializedName("claimed_all")
    val claimedAll: String = "<green>You claimed <yellow>%amount%</yellow> reward(s).",
    @SerializedName("claimed_none")
    val claimedNone: String = "<red>There was nothing to claim in this tab.",
    @SerializedName("inventory_full")
    val inventoryFull: String = "<red>Your inventory is full! Free some space and try again.",
    @SerializedName("partial_claim")
    val partialClaim: String = "<yellow>Your inventory filled up: you claimed %given% of %total%. The rest stays in /storage.",
    @SerializedName("entry_missing")
    val entryMissing: String = "<red>That reward was already claimed.",
    val error: String = "<red>It couldn't be claimed right now. Tell the administration.",
    /** Title of command rewards created by a /redeem code (%code% = the code's name). */
    @SerializedName("redeem_title")
    val redeemTitle: String = "<gold>Code <yellow>%code%"
)

object StorageConfigManager {
    @Volatile
    var config: StorageConfig = StorageConfig()
        private set

    fun load() {
        config = ConfigFiles.load("Storage", "config.json", StorageConfig::class.java) { StorageConfig() }
    }
}
