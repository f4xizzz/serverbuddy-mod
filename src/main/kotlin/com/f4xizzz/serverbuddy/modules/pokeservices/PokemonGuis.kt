package com.f4xizzz.serverbuddy.modules.pokeservices

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.SlotsAdapter
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import kotlin.math.max

/** The look of the "choose a Pokémon" menu (party first, then the PC box by box) that every Pokémon service shares. */
class PickerSettings(
    @Doc("Title while showing the party.")
    val title: String = "<dark_gray><bold>Choose a Pokémon",
    @SerializedName("pc_title")
    @Doc("Title while showing the PC. Placeholders: %box% %boxes% %name%.")
    val pcTitle: String = "<dark_gray><bold>PC <gray>» box %box%/%boxes%",
    @SerializedName("party_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the 6 party Pokémon go.")
    val partySlots: List<Int> = listOf(10, 11, 12, 14, 15, 16),
    @SerializedName("pc_slots")
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Where the 30 Pokémon of a PC box go.")
    val pcSlots: List<Int> = (0..5).toList() + (9..14) + (18..23) + (27..32) + (36..41),
    @Doc("Name of a Pokémon in the menu (MiniMessage; accepts the Pokémon placeholders and the ones the service adds).")
    val name: String = "%shiny%<aqua>%name% %gender%",
    @Doc("Description of a Pokémon in the menu.")
    val lore: List<String> = listOf("<gray>Level <white>%level%", "<gray>Nature <white>%nature%</white> · Ability <white>%ability%", "<gray>IVs <white>%ivs%"),
    @SerializedName("shiny_prefix")
    val shinyPrefix: String = "<gold>★ </gold>",
    @Doc("Shown instead of an empty slot (null = nothing).")
    val empty: ItemSpec? = null,
    @Doc("Fills every slot nothing else uses.")
    val filler: ItemSpec = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true),
    val pc: MenuButton = MenuButton(slots = listOf(31), hideWhenUnavailable = true, icon = ItemSpec("cobblemon:pc", name = "<aqua><bold>Open the PC", lore = listOf("<gray>Pick a Pokémon from your PC."))),
    val party: MenuButton = MenuButton(slots = listOf(40), hideWhenUnavailable = true, icon = ItemSpec("minecraft:chest", name = "<aqua><bold>Back to the party")),
    val previous: MenuButton = MenuButton(slots = listOf(7), icon = ItemSpec("minecraft:arrow", name = "<yellow>Previous box")),
    val next: MenuButton = MenuButton(slots = listOf(16), icon = ItemSpec("minecraft:arrow", name = "<yellow>Next box")),
    val close: MenuButton = MenuButton(slots = listOf(49), hideWhenUnavailable = false, icon = ItemSpec("minecraft:barrier", name = "<red><bold>Close"))
)

/** The look of a yes/no menu. */
class ConfirmSettings(
    val confirm: MenuButton = MenuButton(slots = listOf(11), hideWhenUnavailable = false, icon = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green><bold>Confirm")),
    val cancel: MenuButton = MenuButton(slots = listOf(15), hideWhenUnavailable = false, icon = ItemSpec("minecraft:red_stained_glass_pane", name = "<red><bold>Cancel")),
    @SerializedName("icon_slot")
    @Doc("Where the Pokémon (or item) being confirmed is shown.")
    val iconSlot: Int = 13,
    val filler: ItemSpec = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true)
)

object PokemonDisplay {
    /** The 3D Pokémon item with the configured name and description. [extra] adds placeholders (a price, a time...). */
    fun item(pokemon: Pokemon, settings: PickerSettings, extra: Map<String, Any?> = emptyMap(), extraLore: List<String> = emptyList()): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val vars = PokemonVars.of(pokemon, settings.shinyPrefix) + extra.mapValues { it.value?.toString() ?: "" }
        stack.set(DataComponents.CUSTOM_NAME, Texts.render(settings.name, vars))
        stack.set(DataComponents.LORE, ItemLore((settings.lore + extraLore).flatMap { Texts.fill(it, vars).split("\n") }.map { Texts.parse(it) }))
        return stack
    }
}

/**
 * Party and PC of the player, to pick one Pokémon. [refuse] returns the reason a Pokémon cannot be picked (shown in chat) or
 * null; [onPick] runs when a valid Pokémon is clicked. [extra] adds placeholders to each Pokémon's text (e.g. the price).
 */
class PokemonPickerGui(
    player: ServerPlayer,
    private val settings: PickerSettings,
    private val titleVars: Map<String, Any?> = emptyMap(),
    private val refuse: (Pokemon) -> String? = { null },
    private val extra: (Pokemon) -> Map<String, Any?> = { emptyMap() },
    private val extraLore: List<String> = emptyList(),
    private val onPick: (Pokemon) -> Unit
) : SimpleGui(MenuType.GENERIC_9x6, player, false) {
    private var pcMode = false
    private var box = 0

    init {
        refresh()
    }

    private fun refresh() {
        for (slot in 0 until virtualSize) clearSlot(slot)
        val used = HashSet<Int>()
        var boxes = 1
        var boxName = ""
        if (!pcMode) {
            val party = Cobblemon.storage.getParty(player)
            for ((index, slot) in settings.partySlots.withIndex()) {
                put(slot, party.get(index), used)
            }
        } else {
            val boxList = Cobblemon.storage.getPC(player).boxes
            boxes = max(1, boxList.size)
            box = box.coerceIn(0, boxes - 1)
            val current = boxList.getOrNull(box)
            boxName = current?.name ?: ""
            for ((index, slot) in settings.pcSlots.withIndex()) put(slot, current?.get(index), used)
        }
        val vars = titleVars + mapOf("box" to box + 1, "boxes" to boxes, "name" to Texts.escape(boxName))
        putButton(settings.pc, !pcMode, vars) { pcMode = true; box = 0; refresh() }
        putButton(settings.party, pcMode, vars) { pcMode = false; refresh() }
        putButton(settings.previous, pcMode && box > 0, vars) { box--; refresh() }
        putButton(settings.next, pcMode && box < boxes - 1, vars) { box++; refresh() }
        putButton(settings.close, true, vars) { close() }
        val filler = settings.filler.build()
        if (!filler.isEmpty) for (slot in 0 until virtualSize) if (getSlot(slot) == null) setSlot(slot, GuiElementBuilder.from(filler).build())
        setTitle(Texts.render(if (pcMode) settings.pcTitle else settings.title, vars))
    }

    private fun put(slot: Int, pokemon: Pokemon?, used: MutableSet<Int>) {
        used.add(slot)
        if (pokemon == null) {
            val empty = settings.empty?.build() ?: return
            if (!empty.isEmpty) putSafe(slot, GuiElementBuilder.from(empty).build())
            return
        }
        val stack = PokemonDisplay.item(pokemon, settings, extra(pokemon), extraLore)
        putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> clicked(pokemon) }.build())
    }

    private fun clicked(pokemon: Pokemon) {
        val reason = refuse(pokemon)
        if (reason != null) {
            Texts.send(player, reason, PokemonVars.of(pokemon, settings.shinyPrefix))
            return
        }
        onPick(pokemon)
    }
}

/** A yes/no menu showing [icon]; [lore] is the description under it. */
class ConfirmGui(
    player: ServerPlayer,
    private val settings: ConfirmSettings,
    title: String,
    private val icon: ItemStack,
    private val vars: Map<String, Any?> = emptyMap(),
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit = {}
) : SimpleGui(MenuType.GENERIC_9x3, player, false) {
    init {
        val filler = settings.filler.build()
        if (!filler.isEmpty) for (slot in 0 until virtualSize) setSlot(slot, GuiElementBuilder.from(filler).build())
        putSafe(settings.iconSlot, GuiElementBuilder.from(icon).build())
        putButton(settings.confirm, true, vars) { onConfirm() }
        putButton(settings.cancel, true, vars) { onCancel() }
        setTitle(Texts.render(title, vars))
    }
}
