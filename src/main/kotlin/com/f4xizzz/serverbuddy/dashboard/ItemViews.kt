package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment

/**
 * Items as the dashboard shows them: id, count, display name, enchantments, lore and wear. The same JSON comes from an item in a live
 * inventory ([of]) and from an item inside a player's `.dat` file ([ofTag]), so an offline inventory looks exactly like an online one.
 *
 * Slot numbers follow the player file: 0-35 main inventory (0-8 is the hotbar), 100 boots, 101 leggings, 102 chestplate, 103 helmet and
 * -106 the off hand. An ender chest is 0-26.
 */
object ItemViews {
    /** A slot of a live [net.minecraft.world.entity.player.Inventory] (36-39 armor, 40 off hand) in the numbers of the player file. */
    @JvmStatic
    fun fileSlot(inventorySlot: Int): Int = when (inventorySlot) {
        36 -> 100
        37 -> 101
        38 -> 102
        39 -> 103
        40 -> -106
        else -> inventorySlot
    }

    /** A live item. */
    @JvmStatic
    fun of(slot: Int, stack: ItemStack): JsonObject = JsonObject().apply {
        addProperty("slot", slot)
        addProperty("id", BuiltInRegistries.ITEM.getKey(stack.item).toString())
        addProperty("count", stack.count)
        addProperty("name", stack.hoverName.string)
        if (stack.has(DataComponents.CUSTOM_NAME)) addProperty("customName", true)
        val enchants = enchantments(stack)
        if (enchants.size() > 0) add("enchants", enchants)
        stack.get(DataComponents.LORE)?.lines()?.takeIf { it.isNotEmpty() }?.let { lines ->
            add("lore", JsonArray().also { array -> lines.forEach { array.add(it.string) } })
        }
        if (stack.isDamaged) {
            addProperty("damage", stack.damageValue)
            addProperty("maxDamage", stack.maxDamage)
        }
    }

    /**
     * An item of a player file (`{ Slot, id, count, components }`), or null for an empty slot. An item the server does not know any more
     * (from a mod that was removed) still shows, with the id it had and `unknown: true`.
     */
    @JvmStatic
    fun ofTag(tag: CompoundTag, registries: HolderLookup.Provider): JsonObject? {
        val slot = if (tag.contains("Slot")) tag.getByte("Slot").toInt() else 0
        val stack = try {
            ItemStack.parse(registries, tag).orElse(null)
        } catch (e: Exception) {
            null
        }
        if (stack != null) return if (stack.isEmpty) null else of(slot, stack)
        val id = tag.getString("id")
        if (id.isBlank() || id == "minecraft:air") return null
        return JsonObject().apply {
            addProperty("slot", slot)
            addProperty("id", id)
            addProperty("count", if (tag.contains("count")) tag.getInt("count") else 1)
            addProperty("name", id.substringAfter(':').replace('_', ' '))
            addProperty("unknown", true)
        }
    }

    /** Every item of a list of the player file (`Inventory`, `EnderItems`). */
    @JvmStatic
    fun ofList(list: ListTag, registries: HolderLookup.Provider): JsonArray = JsonArray().also { array ->
        for (index in 0 until list.size) ofTag(list.getCompound(index), registries)?.let(array::add)
    }

    private fun enchantments(stack: ItemStack): JsonArray = JsonArray().also { array ->
        for (component in listOf(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            val enchantments = stack.get(component) ?: continue
            for (entry in enchantments.entrySet()) {
                val holder = entry.key
                array.add(JsonObject().apply {
                    addProperty("id", holder.unwrapKey().map { it.location().toString() }.orElse("?"))
                    addProperty("name", Enchantment.getFullname(holder, entry.intValue).string)
                    addProperty("level", entry.intValue)
                })
            }
        }
    }
}
