package com.f4xizzz.serverbuddy.modules.pokesnack

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.cooking.Seasoning
import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import com.f4xizzz.serverbuddy.core.ServerHolder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Applies `PokeSnack/config.json` to Cobblemon: rewrites the bait/seasoning effect data it loaded from the datapacks.
 *
 * Cobblemon resolves a snack's effects by identifier every time it needs them, so replacing the registry entries is enough: no
 * mixin, and snacks that were already crafted pick the new numbers up. The originals are kept (by identity) so a config reload or
 * a datapack reload always starts again from Cobblemon's own data. This class touches Cobblemon: only load it when it is installed.
 */
object SnackBuffs {
    private val log = LoggerFactory.getLogger("ServerBuddy/PokeSnack")

    private val bakedBaits = IdentityHashMap<SpawnBait, SpawnBait>()
    private val bakedSeasonings = IdentityHashMap<Seasoning, Seasoning>()

    /** Cobblemon's own `pokeSnackPokemonPerChunk`, saved the first time we change it. */
    private var originalPerChunk: Float? = null

    /** What the last [apply] did, for `/pokesnack buffs`. */
    var summary: String = "not applied yet"
        private set

    @Suppress("UNCHECKED_CAST")
    private fun baitMap(): MutableMap<ResourceLocation, SpawnBait>? = try {
        val field = SpawnBaitEffects::class.java.getDeclaredField("effectsMap")
        field.isAccessible = true
        field.get(if (Modifier.isStatic(field.modifiers)) null else SpawnBaitEffects) as MutableMap<ResourceLocation, SpawnBait>
    } catch (e: ReflectiveOperationException) {
        log.warn("This Cobblemon version stores bait effects differently; the snack buffs config is ignored ({}).", e.toString())
        null
    }

    private fun toFx(effect: SpawnBait.Effect) = Fx(effect.type.toString(), effect.subcategory?.toString(), effect.chance, effect.value)

    private fun toEffect(fx: Fx): SpawnBait.Effect {
        val subcategory: ResourceLocation? = fx.subcategory?.let { ResourceLocation.tryParse(it) }
        return SpawnBait.Effect(ResourceLocation.parse(fx.type), subcategory, fx.chance, fx.value)
    }

    /** The item id an entry belongs to, or null when it matches by tag. */
    private fun ingredientOf(condition: RegistryLikeCondition<Item>): String? =
        (condition as? RegistryLikeIdentifierCondition<Item>)?.identifier?.toString()

    /** Puts Cobblemon's original bait and seasoning data back (undoes the last [apply]). */
    private fun restore(baits: MutableMap<ResourceLocation, SpawnBait>) {
        for ((id, bait) in baits.entries.toList()) bakedBaits[bait]?.let { baits[id] = it }
        Seasonings.reloadEntries(Seasonings.seasonings.map { bakedSeasonings[it] ?: it })
        bakedBaits.clear()
        bakedSeasonings.clear()
    }

    /** Applies [config] on top of the data Cobblemon has loaded right now. Server thread. */
    fun apply(config: PokeSnackConfig) {
        applyPerChunk(config)
        val baits = baitMap() ?: return
        restore(baits)
        if (!config.enabled || !config.buffs.enabled) {
            summary = "buffs are off (Cobblemon's datapack values are in use)"
            return
        }
        val claimed = HashSet<String>()
        var changedBaits = 0
        for ((id, bait) in baits.entries.toList()) {
            val before = bait.effects.map(::toFx)
            val after = SnackRules.adjust(before, ingredientOf(bait.item), config.buffs, claimed)
            if (after == before) continue
            val copy = SpawnBait(bait.item, after.map(::toEffect))
            bakedBaits[copy] = bait
            baits[id] = copy
            changedBaits++
        }
        var changedSeasonings = 0
        val seasonings = Seasonings.seasonings.map { seasoning ->
            val before = (seasoning.baitEffects ?: emptyList()).map(::toFx)
            val after = SnackRules.adjust(before, ingredientOf(seasoning.ingredient), config.buffs, claimed)
            if (after == before) return@map seasoning
            val copy = Seasoning(seasoning.ingredient, seasoning.flavours, seasoning.colour, after.map(::toEffect), seasoning.food, seasoning.mobEffects)
            bakedSeasonings[copy] = seasoning
            changedSeasonings++
            copy
        }
        Seasonings.reloadEntries(seasonings)
        summary = "$changedBaits bait entries and $changedSeasonings seasonings adjusted"
        log.info("Poké Snack buffs: {}.", summary)
        syncPlayers()
    }

    private fun applyPerChunk(config: PokeSnackConfig) {
        val cobblemonConfig = Cobblemon.config
        if (config.enabled && config.maxPokemonPerChunk >= 0) {
            if (originalPerChunk == null) originalPerChunk = cobblemonConfig.pokeSnackPokemonPerChunk
            cobblemonConfig.pokeSnackPokemonPerChunk = config.maxPokemonPerChunk.toFloat()
        } else {
            originalPerChunk?.let { cobblemonConfig.pokeSnackPokemonPerChunk = it }
            originalPerChunk = null
        }
    }

    /** Players who are online keep the old numbers in their tooltips until they get the new data. */
    private fun syncPlayers() {
        val server = ServerHolder.server ?: return
        for (player in server.playerList.players) {
            SpawnBaitEffects.sync(player)
            Seasonings.sync(player)
        }
    }

    /** The effects `item` gives right now (as bait and as seasoning), formatted for `/pokesnack buffs <item>`. */
    fun describe(itemId: ResourceLocation): List<Fx>? {
        val item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null) ?: return null
        val stack = ItemStack(item)
        val effects = ArrayList<SpawnBait.Effect>()
        effects.addAll(SpawnBaitEffects.getBaitFromItemStack(stack.itemHolder).flatMap { it.effects })
        effects.addAll(Seasonings.getFromItemStack(stack)?.baitEffects ?: emptyList())
        return effects.map(::toFx)
    }
}
