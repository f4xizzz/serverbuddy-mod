package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.testutil.MinecraftBootstrap
import com.google.gson.JsonObject
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.enchantment.Enchantments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The offline half of the players section: items out of a player file, the profile out of the NBT and the small helpers around them. */
class PlayersViewTest {
    private fun registries(): HolderLookup.Provider {
        MinecraftBootstrap.ensure()
        return VanillaRegistries.createLookup()
    }

    private fun sword(registries: HolderLookup.Provider): ItemStack {
        val sharpness = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)
        return ItemStack(Items.DIAMOND_SWORD).also {
            it.enchant(sharpness, 5)
            it.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"))
            it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Pulled from a stone"))))
            it.damageValue = 12
        }
    }

    /** How a player file stores it: the item saved, plus the `Slot` byte. */
    private fun asInFile(stack: ItemStack, slot: Int, registries: HolderLookup.Provider): CompoundTag =
        (stack.save(registries) as CompoundTag).also { it.putByte("Slot", slot.toByte()) }

    @Test
    fun `an item of a live inventory and the same item from the file look the same`() {
        val registries = registries()
        val stack = sword(registries)
        val live = ItemViews.of(3, stack)
        val fromFile = ItemViews.ofTag(asInFile(stack, 3, registries), registries)!!
        assertEquals(live, fromFile)

        assertEquals(3, live.get("slot").asInt)
        assertEquals("minecraft:diamond_sword", live.get("id").asString)
        assertEquals(1, live.get("count").asInt)
        assertEquals("Excalibur", live.get("name").asString)
        assertTrue(live.get("customName").asBoolean)
        val enchant = live.getAsJsonArray("enchants")[0].asJsonObject
        assertEquals("minecraft:sharpness", enchant.get("id").asString)
        assertEquals(5, enchant.get("level").asInt)
        assertTrue(enchant.get("name").asString.contains("Sharpness"), enchant.get("name").asString)
        assertEquals("Pulled from a stone", live.getAsJsonArray("lore")[0].asString)
        assertEquals(12, live.get("damage").asInt)
        assertEquals(Items.DIAMOND_SWORD.components().get(DataComponents.MAX_DAMAGE), live.get("maxDamage").asInt)
    }

    @Test
    fun `a plain item has no extras and the armor and off hand slots keep their numbers`() {
        val registries = registries()
        val stack = ItemStack(Items.BREAD, 17)
        val json = ItemViews.of(0, stack)
        assertEquals("Bread", json.get("name").asString)
        assertEquals(17, json.get("count").asInt)
        for (extra in listOf("customName", "enchants", "lore", "damage", "maxDamage", "unknown")) assertFalse(json.has(extra), extra)

        // the file stores the off hand as -106 (a signed byte) and the armor as 100-103
        assertEquals(-106, ItemViews.ofTag(asInFile(stack, -106, registries), registries)!!.get("slot").asInt)
        assertEquals(103, ItemViews.ofTag(asInFile(stack, 103, registries), registries)!!.get("slot").asInt)
        assertEquals(listOf(0, 35, 100, 101, 102, 103, -106), listOf(0, 35, 36, 37, 38, 39, 40).map(ItemViews::fileSlot).let { listOf(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) })
    }

    @Test
    fun `an enchanted book shows what it stores`() {
        val registries = registries()
        val protection = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION)
        val book = ItemStack(Items.ENCHANTED_BOOK)
        book.set(DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).also { it.set(protection, 4) }.toImmutable())
        val enchant = ItemViews.of(1, book).getAsJsonArray("enchants")[0].asJsonObject
        assertEquals("minecraft:protection", enchant.get("id").asString)
        assertEquals(4, enchant.get("level").asInt)
    }

    @Test
    fun `an item from a mod that is gone still shows, marked as unknown, and an empty slot does not`() {
        val registries = registries()
        val gone = CompoundTag().apply {
            putByte("Slot", 5)
            putString("id", "removedmod:magic_wand")
            putInt("count", 3)
        }
        val json = ItemViews.ofTag(gone, registries)!!
        assertEquals("removedmod:magic_wand", json.get("id").asString)
        assertEquals(3, json.get("count").asInt)
        assertEquals("magic wand", json.get("name").asString)
        assertTrue(json.get("unknown").asBoolean)
        assertEquals(5, json.get("slot").asInt)

        assertNull(ItemViews.ofTag(CompoundTag().apply { putString("id", "minecraft:air"); putInt("count", 1) }, registries))
        assertNull(ItemViews.ofTag(CompoundTag(), registries))
    }

    @Test
    fun `a whole list of the file keeps the order and skips the empty entries`() {
        val registries = registries()
        val list = ListTag().apply {
            add(asInFile(ItemStack(Items.STONE, 64), 0, registries))
            add(CompoundTag().apply { putByte("Slot", 1); putString("id", "minecraft:air"); putInt("count", 0) })
            add(asInFile(sword(registries), 9, registries))
        }
        val array = ItemViews.ofList(list, registries)
        assertEquals(2, array.size())
        assertEquals(listOf(0, 9), array.map { it.asJsonObject.get("slot").asInt })
        assertEquals("minecraft:stone", array[0].asJsonObject.get("id").asString)
    }

    @Test
    fun `the profile out of a saved player`() {
        val data = CompoundTag().apply {
            putFloat("Health", 17.5f)
            putInt("foodLevel", 14)
            putInt("XpLevel", 30)
            putInt("playerGameType", 1)
            putString("Dimension", "minecraft:the_nether")
            put("Pos", ListTag().apply { add(DoubleTag.valueOf(12.34)); add(DoubleTag.valueOf(64.0)); add(DoubleTag.valueOf(-8.76)) })
            putInt("SpawnX", 10)
            putInt("SpawnY", 70)
            putInt("SpawnZ", -20)
            putString("SpawnDimension", "minecraft:overworld")
        }
        val json = JsonObject()
        PlayersView.offlineFields(data, json)
        assertEquals(17.5, json.get("health").asDouble)
        assertEquals(14, json.get("food").asInt)
        assertEquals(30, json.get("xpLevel").asInt)
        assertEquals("creative", json.get("gamemode").asString)
        assertEquals("minecraft:the_nether", json.get("dimension").asString)
        assertEquals(12.3, json.get("x").asDouble)
        assertEquals(64.0, json.get("y").asDouble)
        assertEquals(-8.8, json.get("z").asDouble)
        val spawn = json.getAsJsonObject("spawn")
        assertEquals(listOf(10, 70, -20), listOf(spawn.get("x").asInt, spawn.get("y").asInt, spawn.get("z").asInt))
        assertEquals("minecraft:overworld", spawn.get("dimension").asString)
    }

    @Test
    fun `a nearly empty player file gives a nearly empty profile instead of failing`() {
        val json = JsonObject()
        PlayersView.offlineFields(CompoundTag(), json)
        assertEquals(0, json.size())
    }

    @Test
    fun `the user cache is read leniently`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val names = PlayersView.KnownNames.parse(
            """[{"name":"Steve","uuid":"$a","expiresOn":"2030-01-01 00:00:00 +0000"},{"name":"","uuid":"$b"},{"name":"NoUuid"},{"name":"BadUuid","uuid":"nope"}]"""
        )
        assertEquals(mapOf(a to "Steve"), names)
    }

    @Test
    fun `what was read about an offline player is kept briefly and forgotten when they play`() {
        val id = UUID.randomUUID()
        val other = UUID.randomUUID()
        var builds = 0
        fun read(uuid: UUID) = PlayersView.OfflineCache.get("party:$uuid") { builds++; JsonObject() }
        val first = read(id)
        assertSame(first, read(id), "the second look does not read again")
        read(other)
        assertEquals(2, builds)
        PlayersView.OfflineCache.forget(id)
        assertNotNull(read(id))
        assertEquals(3, builds, "after they played, the old answer is gone")
        read(other)
        assertEquals(3, builds, "somebody else's answer stays")
        PlayersView.OfflineCache.clear()
    }

    @Test
    fun `an answer read while Cobblemon may still be writing the player's files is not kept`() {
        val id = UUID.randomUUID()
        var builds = 0
        fun read(keep: Boolean) = PlayersView.OfflineCache.get("pc:$id", keep = keep) { builds++; JsonObject() }
        read(keep = false)
        read(keep = false)
        assertEquals(2, builds, "a player who just left is read from the disk every time")
        val settled = read(keep = true)
        assertSame(settled, read(keep = true), "once the files are settled the answer is kept")
        assertEquals(3, builds)
        PlayersView.OfflineCache.clear()
    }
}
