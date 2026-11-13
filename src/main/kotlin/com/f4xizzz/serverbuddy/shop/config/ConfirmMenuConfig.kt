package com.f4xizzz.serverbuddy.shop.config

import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.data.confirm.ConfirmAmountItem
import com.f4xizzz.serverbuddy.shop.data.items.GenericItem
import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.gui.InventoryType

class ConfirmMenuConfig(
    val title: String = "",
    val type: InventoryType = InventoryType.GENERIC_9x3,
    @SerializedName("entry_slot")
    val entrySlot: Int = 13,
    @SerializedName("back_on_transaction", alternate = ["back_on_purchase"])
    val backOnTransaction: Boolean = true,
    @SerializedName("open_actions")
    val openActions: Map<String, Action>? = emptyMap(),
    @SerializedName("close_actions")
    val closeActions: Map<String, Action>? = emptyMap(),
    val amounts: Map<String, ConfirmAmountItem> = emptyMap(),
    val items: Map<String, GenericItem> = emptyMap()
) {
    lateinit var id: String
}