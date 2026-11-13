package com.f4xizzz.serverbuddy.kits.config

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.kits.config.item.ActionMenuItem
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.f4xizzz.serverbuddy.kits.utils.FlexibleListAdaptorFactory

class PreviewConfig(
    val title: String = "Kit Preview - %kit_name%",
    @SerializedName("type", alternate = ["size"])
    val type: InventoryType = InventoryType.GENERIC_9x6,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val slots: List<Int> = emptyList(),
    val items: Map<String, ActionMenuItem> = emptyMap()
) {
    override fun toString(): String {
        return "PreviewConfig(title='$title', type=$type, slots=$slots, items=$items)"
    }
}