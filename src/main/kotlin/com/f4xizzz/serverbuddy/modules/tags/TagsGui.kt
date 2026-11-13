package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.math.max

/**
 * Tags chest menu: categories, pages, equip, take off and buy. Everything comes from [TagsConfig] (positions, icons,
 * texts). Works the same with ServerBuddy's catalog and with GreatCosmetics'.
 */
class TagsGui(
    player: ServerPlayer,
    private val config: TagsConfig
) : SimpleGui(config.menu.type.type, player, !config.menu.showPlayerInventory) {
    private var page = 0

    /** null = todas as categorias. */
    private var category: String? = null
    private var pendingBuy: Pair<String, Long>? = null

    init {
        if (!config.menu.showPlayerInventory) SbGui.hidePlayerInventory(this, config.menu.playerInventoryFiller)
        refresh()
    }

    fun refresh() {
        val menu = config.menu
        for (slot in 0 until virtualSize) clearSlot(slot)
        putFillers(menu.fillers)

        val snapshot = TagsService.snapshot(player)
        val visible = TagsService.catalog().filter { view ->
            !view.def.hidden || TagsService.owns(player, view, snapshot)
        }

        // categories that have some tag to show, in catalog order
        val usedCategories = visible.map { it.def.category.lowercase() }.filter { it.isNotEmpty() }.toSet()
        val categories = TagsService.catalogFile.categories.filter { (key, _) -> key.lowercase() in usedCategories }.toList()
        // categories that exist in the tags but were not declared yet show up under their own name
        val extra = usedCategories.filter { used -> categories.none { it.first.equals(used, ignoreCase = true) } }

        val inCategory = category?.let { current -> visible.filter { it.def.category.equals(current, ignoreCase = true) } } ?: visible
        val perPage = max(1, menu.tagSlots.size)
        val pages = max(1, (inCategory.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)

        if (inCategory.isEmpty()) {
            val empty = menu.emptyIcon
            if (empty.enabled) {
                val stack = empty.icon.build()
                if (!stack.isEmpty) for (slot in empty.slots) putSafe(slot, GuiElementBuilder.from(stack).build())
            }
        } else {
            inCategory.drop(page * perPage).take(perPage).forEachIndexed { index, view ->
                val slot = menu.tagSlots.getOrNull(index) ?: return@forEachIndexed
                val state = TagsService.stateOf(player, view, snapshot)
                val stack = iconFor(view, state)
                if (!stack.isEmpty) putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> onClick(view, state) }.build())
            }
        }

        // category buttons: "all" and then one per used category
        var categoryName = menu.allCategory.name.ifBlank { "All" }
        val buttons = ArrayList<Triple<String?, String, ItemStack>>()
        buttons.add(Triple(null, categoryName, menu.allCategory.icon.build()))
        for ((key, value) in categories) buttons.add(Triple(key, value.name.ifBlank { key }, value.icon.build()))
        for (key in extra) buttons.add(Triple(key, key, ItemStack.EMPTY))
        buttons.forEachIndexed { index, (key, name, stack) ->
            val slot = menu.categorySlots.getOrNull(index) ?: return@forEachIndexed
            val selected = key.equals(category, ignoreCase = true) && (key != null || category == null)
            if (selected) categoryName = name
            val shown = if (stack.isEmpty) net.minecraft.world.item.Items.PAPER.defaultInstance.also {
                it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Texts.parse(name))
            } else stack.copy()
            if (selected) shown.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            putSafe(slot, GuiElementBuilder.from(shown).setCallback { _ ->
                category = key
                page = 0
                refresh()
            }.build())
        }

        val equipped = TagsService.find(snapshot.equipped)
        val vars = mapOf(
            "page" to page + 1,
            "pages" to pages,
            "category" to categoryName,
            "tag" to (equipped?.def?.name ?: "")
        )
        putButton(menu.buttons.previous, page > 0, vars) { page--; refresh() }
        putButton(menu.buttons.next, page < pages - 1, vars) { page++; refresh() }
        putButton(menu.buttons.close, true, vars) { close() }
        putButton(menu.buttons.remove, equipped != null, vars) { act { TagsService.unequip(player).also { outcome -> if (outcome == Outcome.OK) Texts.send(player, config.messages.removed, TagsService.vars(equipped, player)) } } }

        val owned = visible.count { TagsService.owns(player, it, snapshot) }
        setTitle(Texts.render(menu.title, vars + mapOf("owned" to owned, "total" to visible.size)))
    }

    private fun iconFor(view: TagView, state: TagState): ItemStack {
        val menu = config.menu
        val def = view.def
        var spec = def.icon
        if (state == TagState.LOCKED && menu.lockedIcon != null) spec = spec.copy(item = menu.lockedIcon)
        val hint = def.unlock.hint
        val stateLines = when (state) {
            TagState.EQUIPPED -> menu.states.equipped
            TagState.OWNED -> menu.states.owned
            TagState.PURCHASABLE -> menu.states.purchasable
            TagState.LOCKED -> menu.states.locked
        }.filter { !(it.trim() == "%hint%" && hint.isBlank()) }
        val lore = spec.lore + def.description + stateLines
        val shown = spec.copy(
            name = spec.name ?: def.name.ifBlank { view.id },
            lore = lore,
            glint = if (state == TagState.EQUIPPED) true else spec.glint
        )
        return shown.build(TagsService.vars(view, player) + mapOf("hint" to hint))
    }

    private fun onClick(view: TagView, state: TagState) {
        val messages = config.messages
        val vars = TagsService.vars(view, player)
        when (state) {
            TagState.EQUIPPED -> act {
                TagsService.unequip(player)
                Texts.send(player, messages.removed, vars)
            }
            TagState.OWNED -> act {
                if (TagsService.equip(player, view.id) == Outcome.OK) Texts.send(player, messages.equipped, vars)
            }
            TagState.PURCHASABLE -> {
                val now = System.currentTimeMillis()
                val pending = pendingBuy
                if (config.menu.confirmPurchase && (pending == null || pending.first != view.id || now - pending.second > 10_000)) {
                    pendingBuy = view.id to now
                    Texts.send(player, config.messages.confirmBuy, vars)
                } else {
                    pendingBuy = null
                    act {
                        when (TagsService.buy(player, view.id)) {
                            Outcome.OK -> Texts.send(player, messages.bought, vars)
                            Outcome.NO_ECONOMY -> Texts.send(player, messages.noEconomy, vars)
                            Outcome.NOT_ENOUGH -> Texts.send(player, messages.notEnoughMoney, vars)
                            else -> Texts.send(player, messages.notOwned, vars)
                        }
                    }
                }
            }
            TagState.LOCKED -> Texts.send(player, messages.notOwned, vars)
        }
    }

    /** Runs a menu action and refreshes what is on screen. */
    private fun act(action: () -> Unit) {
        action()
        refresh()
    }
}
