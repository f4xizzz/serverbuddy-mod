package com.f4xizzz.serverbuddy.modules.pokeservices

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import net.minecraft.server.level.ServerPlayer

/** Helpers shared by STS, Daycare, GTS and Wonder Trade. Touches Cobblemon: only load when it is installed. */
object PokemonTools {
    /** The whole Pokémon (stats, moves, form, ball...) as JSON text, for the database. */
    fun toJson(pokemon: Pokemon, player: ServerPlayer): String = pokemon.saveToJSON(player.server.registryAccess(), JsonObject()).toString()

    fun fromJson(text: String, player: ServerPlayer): Pokemon? = try {
        Pokemon().loadFromJSON(player.server.registryAccess(), JsonParser.parseString(text).asJsonObject)
    } catch (e: Exception) {
        null
    }

    /** Puts [pokemon] in the party, or in the PC when the party is full. False if there is no room anywhere. */
    fun give(player: ServerPlayer, pokemon: Pokemon): Boolean = Cobblemon.storage.getParty(player).add(pokemon) || Cobblemon.storage.getPC(player).add(pokemon)

    /** Takes [pokemon] out of the player's party or PC. */
    fun take(player: ServerPlayer, pokemon: Pokemon): Boolean = Cobblemon.storage.getParty(player).remove(pokemon) || Cobblemon.storage.getPC(player).remove(pokemon)

    fun inBattle(player: ServerPlayer): Boolean = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null

    /** Pokémon in the party (not counting empty slots). */
    fun partySize(player: ServerPlayer): Int = Cobblemon.storage.getParty(player).occupied()

    fun speciesId(pokemon: Pokemon): String = pokemon.species.resourceIdentifier.toString()

    fun isLegendaryLike(pokemon: Pokemon): Boolean {
        val labels = pokemon.species.labels
        return pokemon.isLegendary() || "legendary" in labels || "mythical" in labels || "ultra_beast" in labels || "paradox" in labels
    }

    /** Percent of the maximum possible IVs (0-100). */
    fun ivPercent(pokemon: Pokemon): Double {
        val ivs = pokemon.ivs
        var total = 0
        for (stat in com.cobblemon.mod.common.api.pokemon.stats.Stats.PERMANENT) total += ivs.getOrDefault(stat)
        return total / 186.0 * 100.0
    }
}

/**
 * Which Pokémon a service refuses (a blacklist), written the same way in every service's config:
 * `{ "species": ["cobblemon:mewtwo"], "labels": ["legendary"], "shiny": false, "min_level": 1 }`.
 */
class PokemonFilter(
    @Doc("Species that are refused (\"cobblemon:mewtwo\" or just \"mewtwo\").")
    val species: List<String> = emptyList(),
    @Doc("Refuse every species with one of these labels: legendary, mythical, ultra_beast, paradox, baby...")
    val labels: List<String> = emptyList(),
    @Doc("true = shiny Pokémon are refused.")
    val shiny: Boolean = false,
    @SerializedName("min_level")
    @Doc("Pokémon below this level are refused (0 = no minimum).")
    val minLevel: Int = 0,
    @SerializedName("max_level")
    @Doc("Pokémon above this level are refused (0 = no maximum).")
    val maxLevel: Int = 0
) {
    /** Why [pokemon] is refused, or null when it is allowed. */
    fun reason(pokemon: Pokemon): String? {
        val id = PokemonTools.speciesId(pokemon).lowercase()
        if (species.any { normalize(it) == id }) return "species"
        val have = pokemon.species.labels.map { it.lowercase() }
        if (labels.any { it.lowercase() in have || (it.equals("legendary", true) && PokemonTools.isLegendaryLike(pokemon)) }) return "label"
        if (shiny && pokemon.shiny) return "shiny"
        if (minLevel > 0 && pokemon.level < minLevel) return "level"
        if (maxLevel > 0 && pokemon.level > maxLevel) return "level"
        return null
    }

    private fun normalize(text: String): String {
        val t = text.trim().lowercase()
        return if (':' in t) t else "cobblemon:$t"
    }
}
