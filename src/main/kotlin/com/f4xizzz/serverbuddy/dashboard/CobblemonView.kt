package com.f4xizzz.serverbuddy.dashboard

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.modules.leaderboards.CobblemonDexStats
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Everything the dashboard reads or changes about Cobblemon Pokémon. Kept in its own object so the class (and Cobblemon's classes
 * it uses) only loads when Cobblemon is installed.
 */
object CobblemonView {
    fun dexPercent(player: ServerPlayer): Double? = CobblemonDexStats.read(player)?.percent?.let { Math.round(it * 10) / 10.0 }

    /**
     * The Pokédex numbers of any player. An online player is read live; an offline one straight from the file ([CobblemonDexStats.readOffline]),
     * and a player with no Pokédex file yet has zeros.
     */
    fun dex(server: MinecraftServer, uuid: UUID): JsonObject {
        val player = server.playerList.getPlayer(uuid)
        val dex = if (player != null) CobblemonDexStats.read(player) ?: throw DashboardError("error", "Pokédex unavailable") else CobblemonDexStats.readOffline(uuid) ?: CobblemonDexStats.Dex(0, 0, 0.0)
        return JsonObject().apply {
            addProperty("caught", dex.caught)
            addProperty("seen", dex.seen)
            addProperty("percent", Math.round(dex.percent * 10) / 10.0)
        }
    }

    /** The common part of a Pokémon in the party and in the PC: what the dashboard needs to draw it. */
    private fun JsonObject.basics(slot: Int, pokemon: Pokemon) {
        addProperty("slot", slot)
        addProperty("uuid", pokemon.uuid.toString())
        addProperty("species", pokemon.species.name)
        addProperty("showdownId", pokemon.species.showdownId())
        addProperty("dex", pokemon.species.nationalPokedexNumber)
        pokemon.form.name.takeIf { it.isNotBlank() && !it.equals("normal", ignoreCase = true) }?.let { addProperty("form", it) }
        pokemon.nickname?.string?.takeIf { it.isNotBlank() }?.let { addProperty("nickname", it) }
        addProperty("level", pokemon.level)
        addProperty("shiny", pokemon.shiny)
        addProperty("gender", pokemon.gender.name)
    }

    /** A Pokémon of the party in full (the PC uses [compact]: a PC can hold thousands and the answer has to fit in one message). */
    fun summary(slot: Int, pokemon: Pokemon): JsonObject = JsonObject().apply {
        basics(slot, pokemon)
        addProperty("nature", pokemon.nature.name.path)
        addProperty("ability", pokemon.ability.name)
        add("moves", JsonArray().also { moves -> pokemon.moveSet.getMoves().forEach { moves.add(it.name) } })
        addProperty("ball", pokemon.caughtBall.name.path)
        addProperty("friendship", pokemon.friendship)
        pokemon.heldItem().takeIf { !it.isEmpty }?.let { addProperty("heldItem", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.item).toString()) }
        add("ivs", stats { pokemon.ivs.getOrDefault(it) })
        add("evs", stats { pokemon.evs.getOrDefault(it) })
    }

    private fun stats(value: (com.cobblemon.mod.common.api.pokemon.stats.Stat) -> Int): JsonObject = JsonObject().apply {
        for (stat in com.cobblemon.mod.common.api.pokemon.stats.Stats.PERMANENT) addProperty(stat.showdownId, value(stat))
    }

    /** A Pokémon of the PC with only what draws it. */
    fun compact(slot: Int, pokemon: Pokemon): JsonObject = JsonObject().apply { basics(slot, pokemon) }

    fun party(server: MinecraftServer, uuid: UUID): JsonObject {
        val party = Cobblemon.storage.getParty(uuid, server.registryAccess())
        return JsonObject().apply {
            add("pokemon", JsonArray().also { array ->
                for (slot in 0 until 6) party.get(slot)?.let { array.add(summary(slot, it)) }
            })
        }
    }

    /** The most Pokémon one `players.pc` answer carries (a message is at most 1 MiB; a compact Pokémon is about 150 bytes). */
    const val PC_LIMIT = 5000

    fun pc(server: MinecraftServer, uuid: UUID): JsonObject {
        val pc = Cobblemon.storage.getPC(uuid, server.registryAccess())
        var total = 0
        var truncated = false
        return JsonObject().apply {
            add("boxes", JsonArray().also { boxes ->
                pc.boxes.forEachIndexed { index, box ->
                    boxes.add(JsonObject().apply {
                        addProperty("index", index)
                        add("pokemon", JsonArray().also { array ->
                            for ((slot, pokemon) in box.getNonEmptySlots()) {
                                if (total >= PC_LIMIT) {
                                    truncated = true
                                    break
                                }
                                total++
                                array.add(compact(slot, pokemon))
                            }
                        })
                    })
                }
            })
            addProperty("total", total)
            if (truncated) addProperty("truncated", true)
        }
    }

    private val LOGGER = org.slf4j.LoggerFactory.getLogger("ServerBuddy/Dashboard")

    /**
     * Cobblemon keeps every store it loads in memory until its player leaves the server. A store loaded only to LOOK at an offline player never
     * gets that "leave", so the dashboard lets it go once the answer is built (the next look reads the file again). A store with changes that
     * are not saved yet is never let go. Best effort: if Cobblemon's insides change, nothing happens and the store just stays cached.
     * Returns how many stores were let go.
     */
    fun release(uuid: UUID): Int {
        var released = 0
        try {
            val factory = com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory::class.java
            val manager = Cobblemon.storage
            val factories = manager.javaClass.getDeclaredField("factories").also { it.isAccessible = true }.get(manager) as Iterable<*>
            val caches = factory.getDeclaredMethod("getStoreCaches").also { it.isAccessible = true }
            val dirtyField = factory.getDeclaredField("dirtyStores").also { it.isAccessible = true }
            for (candidate in factories) {
                if (!factory.isInstance(candidate)) continue
                val dirty = dirtyField.get(candidate) as Set<*>
                for (cache in (caches.invoke(candidate) as Map<*, *>).values) {
                    @Suppress("UNCHECKED_CAST")
                    val stores = cache!!.javaClass.getMethod("getCacheMap").invoke(cache) as MutableMap<UUID, Any?>
                    val store = stores[uuid] ?: continue
                    if (store in dirty) continue
                    stores.remove(uuid)
                    released++
                }
            }
        } catch (e: Throwable) {
            LOGGER.debug("[Dashboard] Could not let go of the Cobblemon stores of {}: {}", uuid, e.toString())
        }
        LOGGER.debug("[Dashboard] Released {} Cobblemon store(s) of {} (offline look)", released, uuid)
        return released
    }

    /** Gives a Pokémon written like the Cobblemon command (`pikachu level=50 shiny`) to an online player (party first, then PC). */
    fun give(player: ServerPlayer, properties: String): JsonObject {
        val pokemon = try {
            PokemonProperties.parse(properties).create()
        } catch (e: Exception) {
            throw DashboardError("bad_params", "Invalid Pokémon properties: ${e.message}")
        }
        val party = Cobblemon.storage.getParty(player)
        val placed = party.add(pokemon) || Cobblemon.storage.getPC(player).add(pokemon)
        if (!placed) throw DashboardError("storage_full")
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("species", pokemon.species.name)
        }
    }

    /** Removes the Pokémon in [where] (`party` or `pc`) at [box]/[slot] from an online player. */
    fun remove(player: ServerPlayer, where: String, box: Int, slot: Int): JsonObject {
        val pokemon = find(player, where, box, slot)
        val removed = if (where == "party") Cobblemon.storage.getParty(player).remove(pokemon) else Cobblemon.storage.getPC(player).remove(pokemon)
        if (!removed) throw DashboardError("error", "Could not remove it")
        return JsonObject().apply { addProperty("ok", true) }
    }

    /** Moves a Pokémon between the party and the PC (the destination takes the first free slot). */
    fun move(player: ServerPlayer, fromWhere: String, box: Int, slot: Int, toWhere: String): JsonObject {
        if (fromWhere == toWhere) throw DashboardError("bad_params", "Same place")
        val pokemon = find(player, fromWhere, box, slot)
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        val target = if (toWhere == "party") party else pc
        if (!target.add(pokemon)) throw DashboardError("storage_full")
        if (!(if (fromWhere == "party") party else pc).remove(pokemon)) throw DashboardError("error", "Could not move it")
        return JsonObject().apply { addProperty("ok", true) }
    }

    private fun find(player: ServerPlayer, where: String, box: Int, slot: Int): Pokemon = when (where) {
        "party" -> Cobblemon.storage.getParty(player).get(slot)
        "pc" -> Cobblemon.storage.getPC(player).boxes.getOrNull(box)?.get(slot)
        else -> throw DashboardError("bad_params", "where must be party or pc")
    } ?: throw DashboardError("not_found", "no Pokémon there")
}
