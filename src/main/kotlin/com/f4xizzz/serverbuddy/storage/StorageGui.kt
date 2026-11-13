package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.core.Texts
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import kotlin.math.max

/**
 * Menu do /storage: abas por tipo de recompensa e páginas que crescem conforme o jogador acumula recompensas.
 * Tudo (ícones, posições, textos) vem de [StorageConfig].
 */
class StorageGui(
    player: ServerPlayer,
    private val config: StorageConfig
) : SimpleGui(config.type.type, player, !config.showPlayerInventory) {
    private var tab: StorageType = StorageType.byKey(config.defaultTab) ?: StorageType.ITEM
    private var page = 0

    init {
        if (!config.showPlayerInventory) SbGui.hidePlayerInventory(this, config.playerInventoryFiller)
        refresh()
    }

    fun refresh() {
        val byType = StorageService.entriesByType(player.uuid)
        val total = byType.values.sumOf { it.size }
        for (i in 0 until virtualSize) clearSlot(i)

        // fundo
        for (filler in config.fillers) {
            val stack = filler.icon.build()
            if (stack.isEmpty) continue
            for (slot in filler.slots) put(slot, GuiElementBuilder.from(stack).build())
        }

        // recompensas da página atual
        val entries = byType[tab].orEmpty()
        val perPage = max(1, config.entrySlots.size)
        val pages = max(1, (entries.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)

        if (entries.isEmpty()) {
            val empty = config.emptyIcon
            if (empty.enabled) {
                val stack = empty.icon.build()
                if (!stack.isEmpty) for (slot in empty.slots) put(slot, GuiElementBuilder.from(stack).build())
            }
        } else {
            entries.drop(page * perPage).take(perPage).forEachIndexed { index, entry ->
                val slot = config.entrySlots.getOrNull(index) ?: return@forEachIndexed
                val display = when (entry) {
                    is StorageEntry.ItemEntry -> itemDisplay(entry)
                    is StorageEntry.CommandEntry -> commandDisplay(entry)
                }
                if (!display.isEmpty) {
                    put(slot, GuiElementBuilder.from(display).setCallback { click -> onEntryClick(entry, click) }.build())
                }
            }
        }

        // abas
        var tabTitle = tab.key
        for ((key, tabConfig) in config.tabs) {
            val type = StorageType.byKey(key) ?: continue
            val amount = byType[type]?.size ?: 0
            val selected = type == tab
            if (selected) tabTitle = tabConfig.title
            val spec = if (selected) tabConfig.selectedIcon ?: tabConfig.icon else tabConfig.icon
            val stack = spec.build(mapOf("amount" to amount, "tab" to tabConfig.title))
            if (stack.isEmpty) continue
            for (slot in tabConfig.slots) {
                put(slot, GuiElementBuilder.from(stack).setCallback { _ ->
                    if (!selected) {
                        tab = type
                        page = 0
                        refresh()
                    }
                }.build())
            }
        }

        // botões
        val vars = mapOf("page" to page + 1, "pages" to pages, "tab" to tabTitle, "amount" to entries.size)
        putButton(config.buttons.previous, page > 0, vars) { page--; refresh() }
        putButton(config.buttons.next, page < pages - 1, vars) { page++; refresh() }
        putButton(config.buttons.close, true, vars) { close() }
        putButton(config.buttons.claimAll, entries.isNotEmpty(), vars) {
            StorageService.claimAll(player, tab)
            refresh()
        }

        setTitle(
            Texts.render(
                config.title,
                mapOf("tab" to tabTitle, "page" to page + 1, "pages" to pages, "amount" to entries.size, "total" to total)
            )
        )
    }

    private fun onEntryClick(entry: StorageEntry, click: ClickType) {
        if (config.click.claim.none { it.buttonClicks.contains(click) }) return
        StorageService.claim(player, entry.id)
        if (config.closeOnClaim) close() else refresh()
    }

    private fun put(slot: Int, element: GuiElementInterface) = putSafe(slot, element)

    /** O item guardado, com as linhas extras da config no fim da descrição. */
    private fun itemDisplay(entry: StorageEntry.ItemEntry): ItemStack {
        val stack = entry.stack.copy()
        val extra = config.itemEntry.extraLore
            .filter { !(it.contains("%source%") && entry.source == null) }
            .flatMap { Texts.fill(it, mapOf("source" to (entry.source ?: ""))).split("\n") }
            .map { Texts.parse(it) }
        if (extra.isNotEmpty()) {
            val current = stack.get(DataComponents.LORE)?.lines() ?: emptyList()
            stack.set(DataComponents.LORE, ItemLore(current + extra))
        }
        return stack
    }

    private fun commandDisplay(entry: StorageEntry.CommandEntry): ItemStack {
        val cfg = config.commandEntry
        val spec = entry.spec
        val icon: ItemSpec = cfg.icon.copy(
            item = spec.icon ?: cfg.icon.item,
            lore = cfg.icon.lore.filter {
                !(it.contains("%source%") && entry.source == null) && !(it.trim() == "%lore%" && spec.lore.isEmpty())
            }
        )
        return icon.build(
            mapOf(
                "title" to (spec.title ?: cfg.defaultTitle),
                "lore" to spec.lore.joinToString("\n"),
                "source" to (entry.source ?: "")
            )
        )
    }
}
