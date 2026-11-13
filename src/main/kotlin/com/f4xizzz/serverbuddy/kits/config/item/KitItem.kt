package com.f4xizzz.serverbuddy.kits.config.item

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity

class KitItem(
    item: String = "minecraft:air",
    amount: Int = 1,
    name: String? = null,
    lore: List<String> = emptyList(),
    components: CompoundTag? = null,
    customModelData: Int? = null,
): GenericItem(item, amount, name, lore, components, customModelData) {
    fun giveItem(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData?) {
        val itemStack = createItemStack(player, kitId, kit, kitData) ?: return

        if (!player.addItem(itemStack)) {
            player.serverLevel().addFreshEntity(ItemEntity(player.serverLevel(), player.x, player.y, player.z, itemStack))
        }
    }
}
