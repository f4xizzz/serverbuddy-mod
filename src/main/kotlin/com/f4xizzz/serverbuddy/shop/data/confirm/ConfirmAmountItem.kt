package com.f4xizzz.serverbuddy.shop.data.confirm

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.config.GuiItem
import com.f4xizzz.serverbuddy.shop.config.SoundOption
import com.f4xizzz.serverbuddy.shop.data.TransactionType
import com.f4xizzz.serverbuddy.shop.utils.FlexibleListAdaptorFactory
import net.minecraft.nbt.CompoundTag

class ConfirmAmountItem(
    val item: String = "",
    @SerializedName("slots", alternate = ["slot"])
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val slots: List<Int> = emptyList(),
    val amount: Int = 1,
    val type: TransactionType = TransactionType.BUY,
    val name: String? = null,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val lore: List<String> = emptyList(),
    @SerializedName("components", alternate = ["nbt"])
    val components: CompoundTag? = null,
    @SerializedName("custom_model_data")
    val customModelData: Int? = null,
    val sound: SoundOption? = null
) {
    fun asGuiItem(): GuiItem {
        return GuiItem(
            item = item,
            slots = slots,
            amount = amount,
            name = name,
            lore = lore,
            components = components,
            customModelData = customModelData
        )
    }
}