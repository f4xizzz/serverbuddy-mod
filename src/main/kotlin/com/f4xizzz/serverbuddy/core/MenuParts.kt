package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui

/**
 * Peças de menu de baú que se repetem em vários módulos (Storage, ver time/PC, Tags...): um botão que ocupa uns
 * slots e um item de fundo. Em config:
 *
 * ```
 * "close": { "slots": [49], "enabled": true, "hide_when_unavailable": false,
 *            "icon": { "item": "minecraft:barrier", "name": "<red>Close" } }
 * ```
 */
class MenuButton(
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Button positions (0 is the top-left corner). Accepts a list or a range: [45] ou \"45-47\".")
    val slots: List<Int> = emptyList(),
    @Doc("false = the button is not shown.")
    val enabled: Boolean = true,
    @SerializedName("hide_when_unavailable")
    @Doc("true = disappears when it does not apply (e.g. \"previous page\" on the first page).")
    val hideWhenUnavailable: Boolean = true,
    @Doc("Icon. Name and description accept the menu values (e.g. %page% and %pages%).")
    val icon: ItemSpec = ItemSpec()
)

/** Item de fundo repetido nos [slots]. */
class MenuFiller(
    @JsonAdapter(SlotsAdapter::class)
    @Doc("Positions that receive this item. Accepts a list or a range: [0, 1, 2] ou \"0-8\".")
    val slots: List<Int> = emptyList(),
    @Doc("O item de fundo.")
    val icon: ItemSpec = ItemSpec()
)

/** Põe [element] em [slot] só se o slot é do menu (ignora posições fora do baú, que seriam o inventário do jogador). */
fun SimpleGui.putSafe(slot: Int, element: GuiElementInterface) {
    if (slot in 0 until virtualSize) setSlot(slot, element)
}

fun SimpleGui.putFillers(fillers: List<MenuFiller>) {
    for (filler in fillers) {
        val stack = filler.icon.build()
        if (stack.isEmpty) continue
        for (slot in filler.slots) putSafe(slot, GuiElementBuilder.from(stack).build())
    }
}

/**
 * Põe um botão: só aparece se está ligado e (quando [available] é false) não manda esconder; o clique só funciona
 * quando [available] é true. [vars] troca os %valores% do nome e da descrição.
 */
fun SimpleGui.putButton(button: MenuButton, available: Boolean, vars: Map<String, Any?>, action: () -> Unit) {
    if (!button.enabled) return
    if (!available && button.hideWhenUnavailable) return
    val stack = button.icon.build(vars)
    if (stack.isEmpty) return
    for (slot in button.slots) {
        val builder = GuiElementBuilder.from(stack)
        if (available) builder.setCallback { _ -> action() }
        putSafe(slot, builder.build())
    }
}
