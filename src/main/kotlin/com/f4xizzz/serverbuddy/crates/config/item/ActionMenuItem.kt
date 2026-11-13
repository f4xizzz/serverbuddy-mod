package com.f4xizzz.serverbuddy.crates.config.item

import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import net.minecraft.nbt.CompoundTag

open class ActionMenuItem(
    item: String = "minecraft:air",
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val slots: List<Int> = emptyList(),
    amount: Int = 1,
    name: String? = null,
    lore: List<String> = emptyList(),
    components: CompoundTag? = null,
    customModelData: Int? = null,
    val actions: Map<String, Action> = emptyMap(),
): GenericItem(item, amount, name, lore, components, customModelData) {
    override fun toString(): String {
        return "ActionMenuItem(item=$item, slots=$slots, amount=$amount, name=$name, lore=$lore, components=$components, customModelData=$customModelData, actions=$actions)"
    }
}