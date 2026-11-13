package com.f4xizzz.serverbuddy.kits.config

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.kits.config.item.MenuItem
import com.f4xizzz.serverbuddy.kits.utils.FlexibleListAdaptorFactory

class KitMenuOptions(
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val slots: List<Int> = emptyList(),
    val available: MenuItem = MenuItem(),
    @SerializedName("on_cooldown")
    val onCooldown: MenuItem = MenuItem(),
    @SerializedName("no_permission")
    val noPermission: MenuItem = MenuItem(),
    @SerializedName("max_uses")
    val maxUses: MenuItem = MenuItem(),
    @SerializedName("failed_requirements")
    val failedRequirements: MenuItem = MenuItem()
) {
    override fun toString(): String {
        return "KitMenuOptions(slots=$slots, available=$available, onCooldown=$onCooldown, noPermission=$noPermission, maxUses=$maxUses, failedRequirements=$failedRequirements)"
    }
}