package com.f4xizzz.serverbuddy.crates.data.rewards.types

import com.f4xizzz.serverbuddy.crates.config.item.GenericItem
import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardLimits
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardType
import net.minecraft.server.level.ServerPlayer

class ItemReward(
    name: String = "",
    description: List<String> = emptyList(),
    display: GenericItem? = null,
    weight: Int = 1,
    limits: RewardLimits? = null,
    broadcast: Boolean = false,
    private val item: GenericItem = GenericItem()
) : Reward(RewardType.ITEM, name, description, display, weight, limits, broadcast) {
    override fun giveReward(player: ServerPlayer, crate: Crate) {
        // Super to call the message
        super.giveReward(player, crate)

        player.inventory.placeItemBackInInventory(item.createItemStack(player))
    }

    override fun getGenericDisplay(): GenericItem {
        return display ?: item
    }

    override fun toString(): String {
        return "ItemReward(name='$name', display=$display, weight=$weight, limits=$limits, broadcast=$broadcast, item=$item)"
    }
}
