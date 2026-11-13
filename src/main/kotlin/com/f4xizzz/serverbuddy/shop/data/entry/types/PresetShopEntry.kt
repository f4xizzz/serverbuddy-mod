package com.f4xizzz.serverbuddy.shop.data.entry.types

import com.f4xizzz.serverbuddy.shop.config.GuiItem
import com.f4xizzz.serverbuddy.shop.config.PriceOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOption
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntry
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntryType
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType

class PresetShopEntry(
    type: ShopEntryType = ShopEntryType.PRESET,
    display: GuiItem = GuiItem(),
    slot: List<Int> = listOf(),
    page: List<Int> = listOf(1),
    buy: PriceOption? = null,
    sell: PriceOption? = null,
    entryLore: List<String>? = null,
    clickOptions: Map<GenericClickType, EntryClickOption>? = null,
) : ShopEntry(type, display, slot, page, buy, sell, entryLore, clickOptions)
