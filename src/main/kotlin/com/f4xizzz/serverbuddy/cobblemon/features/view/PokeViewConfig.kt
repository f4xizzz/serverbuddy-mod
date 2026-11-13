package com.f4xizzz.serverbuddy.cobblemon.features.view

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.MenuFiller
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

@Doc(
    "/pokesee (party) and /compsee (PC) · read-only menus\n" +
        "Texts use MiniMessage; icons use the same format as the other menus. The Pokémon values that can go in the name and\n" +
        "in the description: %name% %species% %nickname% %level% %shiny% %gender% %nature% %ability% %held_item% %ball%\n" +
        "%ivs% %evs% %move1% a %move4% %health% %friendship% %trainer%."
)
class PokeViewConfig(
    @Doc("Party menu (6 Pokémon).")
    val party: PartyViewConfig = PartyViewConfig(),
    @Doc("The PC menu (one box per page).")
    val pc: PcViewConfig = PcViewConfig(),
    @SerializedName("pokemon_item")
    @Doc("How each Pokémon appears in both menus (name and description).")
    val pokemonItem: PokemonItemConfig = PokemonItemConfig(),
    @SerializedName("shiny_prefix")
    @Doc("Text that goes into %shiny% when the Pokémon is shiny.")
    val shinyPrefix: String = "<gold>★ </gold>",
    @Doc("Error messages of the commands.")
    val messages: PokeViewMessages = PokeViewMessages()
)

class PokemonItemConfig(
    val name: String = "%shiny%<yellow>%name% <gray>Lv.%level% %gender%",
    val lore: List<String> = listOf(
        "<gray>Natureza: <white>%nature%",
        "<gray>Habilidade: <white>%ability%",
        "<gray>Item: <white>%held_item%",
        "",
        "<gray>IVs: <white>%ivs%",
        "<gray>EVs: <white>%evs%",
        "",
        "<gray>Golpes:",
        " <white>%move1%",
        " <white>%move2%",
        " <white>%move3%",
        " <white>%move4%",
        "",
        "<dark_gray>Vida %health% · Amizade %friendship%"
    )
)

class PartyViewConfig(
    @Doc("Title. Value: %player% (owner of the party).")
    val title: String = "<dark_green><bold>Time de %player%",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x3,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the viewer's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: item that fills the bottom part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("pokemon_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("One slot for each party position (1 to 6).")
    val pokemonSlots: List<Int> = listOf(10, 11, 12, 13, 14, 15),
    @SerializedName("empty_icon")
    @Doc("Icon for a position without a Pokémon.")
    val emptyIcon: ItemSpec = ItemSpec(item = "minecraft:gray_stained_glass_pane", name = "<dark_gray>Vazio"),
    val fillers: List<MenuFiller> = listOf(
        MenuFiller(
            slots = (0..26).toList(),
            icon = ItemSpec(item = "minecraft:black_stained_glass_pane", name = " ", hideTooltip = true)
        )
    ),
    val close: MenuButton = MenuButton(
        slots = listOf(22),
        hideWhenUnavailable = false,
        icon = ItemSpec(item = "minecraft:barrier", name = "<red>Close")
    )
)

class PcViewConfig(
    @Doc("Title. Values: %player%, %box% (box number), %boxes% (total) and %name% (box name).")
    val title: String = "<dark_aqua><bold>PC de %player%</bold> <gray>- Caixa %box%/%boxes%",
    @SerializedName("type", alternate = ["size"])
    @Doc("Chest size: GENERIC_9x1 to GENERIC_9x6.")
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @SerializedName("show_player_inventory")
    @Doc("true = shows the viewer's inventory below. false = empties that part (Minecraft still draws the slots).")
    val showPlayerInventory: Boolean = true,
    @SerializedName("player_inventory_filler")
    @Doc("Only with show_player_inventory = false: item that fills the bottom part. null = empty.")
    val playerInventoryFiller: ItemSpec? = null,
    @SerializedName("pokemon_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("One slot for each box position (30 positions).")
    val pokemonSlots: List<Int> = listOf(
        1, 2, 3, 4, 5, 6,
        10, 11, 12, 13, 14, 15,
        19, 20, 21, 22, 23, 24,
        28, 29, 30, 31, 32, 33,
        37, 38, 39, 40, 41, 42
    ),
    @SerializedName("empty_icon")
    val emptyIcon: ItemSpec? = null,
    val fillers: List<MenuFiller> = listOf(
        MenuFiller(
            slots = (0..53).toList(),
            icon = ItemSpec(item = "minecraft:black_stained_glass_pane", name = " ", hideTooltip = true)
        )
    ),
    val previous: MenuButton = MenuButton(
        slots = listOf(45),
        icon = ItemSpec(item = "minecraft:arrow", name = "<yellow>Previous box", lore = listOf("<gray>Box <white>%box%</white> of <white>%boxes%"))
    ),
    val next: MenuButton = MenuButton(
        slots = listOf(53),
        icon = ItemSpec(item = "minecraft:arrow", name = "<yellow>Next box", lore = listOf("<gray>Box <white>%box%</white> of <white>%boxes%"))
    ),
    val info: MenuButton = MenuButton(
        slots = listOf(49),
        hideWhenUnavailable = false,
        icon = ItemSpec(item = "minecraft:ender_chest", name = "<aqua>%name%", lore = listOf("<gray>Box <white>%box%</white> of <white>%boxes%"))
    ),
    val close: MenuButton = MenuButton(
        slots = listOf(47),
        hideWhenUnavailable = false,
        icon = ItemSpec(item = "minecraft:barrier", name = "<red>Close")
    )
)

class PokeViewMessages(
    @SerializedName("not_found")
    val notFound: String = "<red>Player '%player%' not found (use the name of someone who has joined the server before, or the UUID).",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>This command can only be used by a player."
)

object PokeViewConfigManager {
    @Volatile
    var config: PokeViewConfig = PokeViewConfig()
        private set

    fun load() {
        config = ConfigFiles.load("PokeView", "config.json", PokeViewConfig::class.java) { PokeViewConfig() }
    }
}
