package com.f4xizzz.serverbuddy.cobblemon.features.view

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Targets
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import kotlin.math.max

/** O Pokémon como item do menu (o modelo 3D do Cobblemon) com o nome e a descrição da config. */
object PokemonDisplay {
    fun item(pokemon: Pokemon, config: PokeViewConfig): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val vars = PokemonVars.of(pokemon, config.shinyPrefix)
        stack.set(DataComponents.CUSTOM_NAME, Texts.render(config.pokemonItem.name, vars))
        stack.set(DataComponents.LORE, ItemLore(config.pokemonItem.lore.map { Texts.render(it, vars) }))
        return stack
    }
}

/** `/pokesee`: o time (6 Pokémon) de um jogador. Só para ver: os cliques não fazem nada. */
class PartyViewGui(
    viewer: ServerPlayer,
    private val target: Targets.Target,
    private val config: PokeViewConfig
) : SimpleGui(config.party.type.type, viewer, !config.party.showPlayerInventory) {
    init {
        if (!config.party.showPlayerInventory) SbGui.hidePlayerInventory(this, config.party.playerInventoryFiller)
        build()
    }

    private fun build() {
        val cfg = config.party
        putFillers(cfg.fillers)

        val server = player.server
        if (server != null) {
            val party = Cobblemon.storage.getParty(target.uuid, server.registryAccess())
            cfg.pokemonSlots.forEachIndexed { index, slot ->
                val pokemon = party.get(index)
                val stack = if (pokemon != null) PokemonDisplay.item(pokemon, config) else cfg.emptyIcon.build()
                if (!stack.isEmpty) putSafe(slot, GuiElementBuilder.from(stack).build())
            }
        }

        putButton(cfg.close, true, emptyMap()) { close() }
        setTitle(Texts.render(cfg.title, mapOf("player" to Texts.escape(target.name))))
    }
}

/** `/compsee`: o PC de um jogador, caixa por caixa. Só para ver. */
class PcViewGui(
    viewer: ServerPlayer,
    private val target: Targets.Target,
    private val config: PokeViewConfig,
    private var boxIndex: Int = 0
) : SimpleGui(config.pc.type.type, viewer, !config.pc.showPlayerInventory) {
    init {
        if (!config.pc.showPlayerInventory) SbGui.hidePlayerInventory(this, config.pc.playerInventoryFiller)
        refresh()
    }

    private fun refresh() {
        val cfg = config.pc
        for (i in 0 until virtualSize) clearSlot(i)
        putFillers(cfg.fillers)

        var total = 1
        var boxName = ""
        val server = player.server
        if (server != null) {
            val boxes = Cobblemon.storage.getPC(target.uuid, server.registryAccess()).boxes
            total = max(1, boxes.size)
            boxIndex = boxIndex.coerceIn(0, total - 1)
            val box = boxes.getOrNull(boxIndex)
            if (box != null) {
                boxName = box.name ?: ""
                for (index in cfg.pokemonSlots.indices) {
                    val slot = cfg.pokemonSlots[index]
                    val pokemon = box.get(index)
                    if (pokemon != null) {
                        putSafe(slot, GuiElementBuilder.from(PokemonDisplay.item(pokemon, config)).build())
                    } else {
                        val empty = cfg.emptyIcon?.build()
                        if (empty != null && !empty.isEmpty) putSafe(slot, GuiElementBuilder.from(empty).build())
                    }
                }
            }
        }

        val vars = mapOf(
            "player" to Texts.escape(target.name),
            "box" to boxIndex + 1,
            "boxes" to total,
            "name" to Texts.escape(boxName)
        )
        putButton(cfg.previous, boxIndex > 0, vars) { boxIndex--; refresh() }
        putButton(cfg.next, boxIndex < total - 1, vars) { boxIndex++; refresh() }
        putButton(cfg.info, true, vars) { }
        putButton(cfg.close, true, vars) { close() }
        setTitle(Texts.render(cfg.title, vars))
    }
}
