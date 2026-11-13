package com.f4xizzz.serverbuddy.core

import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.world.item.ItemStack

/**
 * Peças comuns dos menus de baú do ServerBuddy.
 *
 * **Sem o inventário embaixo:** o cliente do Minecraft sempre desenha os 36 espaços do jogador abaixo de um baú, e um
 * mod só de servidor não consegue tirá-los (isso exige resource pack). O que dá para fazer é esvaziar essa parte e
 * bloquear os cliques nela, opcionalmente preenchendo com um item (um vidro, por exemplo). É o que as opções
 * `show_player_inventory` e `player_inventory_filler` dos menus fazem.
 */
object SbGui {
    /**
     * Esvazia a parte do inventário do jogador de [gui]. O menu precisa ter sido criado com
     * `manipulatePlayerSlots = true` (use `!config.showPlayerInventory` no construtor do SimpleGui).
     */
    @JvmStatic
    fun hidePlayerInventory(gui: SimpleGui, filler: ItemSpec? = null) {
        val stack = filler?.build() ?: ItemStack.EMPTY
        for (slot in gui.virtualSize until gui.size) {
            if (stack.isEmpty) gui.clearSlot(slot) else gui.setSlot(slot, GuiElementBuilder.from(stack).build())
        }
    }
}
