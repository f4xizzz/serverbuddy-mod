package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrNull

class TakeItem(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    val item: String = "",
    val amount: Int = 1,
    val nbt: CompoundTag? = null,
    val strict: Boolean = true
) : Action(ActionType.TAKE_ITEM, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("Attempting to execute a ${type.identifier} Action: $this")
        var removed = 0
        for ((i, stack) in player.inventory.items.withIndex()) {
            if (!stack.isEmpty) {
                if (isItem(stack)) {
                    val stackSize = stack.count
                    if (removed + stackSize >= amount) {
                        player.inventory.items[i].shrink(amount - removed)
                        break
                    } else {
                        player.inventory.items[i].shrink(stackSize)
                    }
                    removed += stackSize
                }
            }
        }
    }

    private fun isItem(checkItem: ItemStack): Boolean {
        val newItem = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item))
        if (newItem.isEmpty) {
            Utils.printDebug("[ACTION - ${type.name}] Failed due to an empty or invalid item ID. Item ID: $item, returned: $newItem")
            return false
        }
        if (!checkItem.item.equals(newItem.get())) {
            return false
        }

        val nbtCopy = nbt?.copy()

        if (strict && nbtCopy != null) {
            val checkNBT = DataComponentPatch.CODEC.encodeStart(KitsModule.INSTANCE.nbtOpts, checkItem.componentsPatch).result().getOrNull() ?: return false

            if (checkNBT != nbtCopy)
                return false
        }

        return true
    }

    override fun toString(): String {
        return "TakeItem(item=$item, amount=$amount, nbt=$nbt, strict=$strict)"
    }
}
