package com.f4xizzz.serverbuddy.cobblemon.features

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.PokemonStats
import com.f4xizzz.serverbuddy.core.Texts

/**
 * Valores de um Pokémon para os textos configuráveis (%name%, %level%, %nature%...). Nomes de espécie, natureza,
 * habilidade e golpes saem como `<lang:...>`: o cliente traduz com o idioma do jogador.
 */
object PokemonVars {
    private val STAT_LABELS: List<Pair<Stat, String>> = listOf(
        Stats.HP to "HP",
        Stats.ATTACK to "Atq",
        Stats.DEFENCE to "Def",
        Stats.SPECIAL_ATTACK to "AtqE",
        Stats.SPECIAL_DEFENCE to "DefE",
        Stats.SPEED to "Vel"
    )

    /** Nome para os textos: o apelido (se tiver) ou o nome traduzido da espécie. */
    fun name(pokemon: Pokemon): String {
        val nickname = pokemon.nickname?.string?.takeIf { it.isNotBlank() }
        return if (nickname != null) Texts.escape(nickname) else species(pokemon)
    }

    fun species(pokemon: Pokemon): String = Texts.lang("cobblemon.species.${pokemon.species.resourceIdentifier.path}.name")

    fun of(pokemon: Pokemon, shinyPrefix: String): Map<String, String> {
        val moves = pokemon.moveSet.getMoves()
        val held = pokemon.heldItem()
        val ball = pokemon.caughtBall.name
        val result = LinkedHashMap<String, String>()
        result["name"] = name(pokemon)
        result["species"] = species(pokemon)
        result["nickname"] = pokemon.nickname?.string?.takeIf { it.isNotBlank() }?.let { Texts.escape(it) } ?: ""
        result["level"] = pokemon.level.toString()
        result["shiny"] = if (pokemon.shiny) shinyPrefix else ""
        result["gender"] = genderSymbol(pokemon.gender)
        result["nature"] = Texts.lang(pokemon.nature.displayName)
        result["ability"] = Texts.lang(pokemon.ability.displayName)
        result["held_item"] = if (held.isEmpty) "-" else Texts.lang(held.descriptionId)
        result["ball"] = Texts.lang("item.${ball.namespace}.${ball.path}")
        result["ivs"] = stats(pokemon.ivs)
        result["evs"] = stats(pokemon.evs)
        for (i in 0 until 4) {
            result["move${i + 1}"] = moves.getOrNull(i)?.let { Texts.lang("cobblemon.move.${it.name}") } ?: "-"
        }
        result["health"] = "${pokemon.currentHealth}/${pokemon.maxHealth}"
        result["friendship"] = pokemon.friendship.toString()
        result["trainer"] = pokemon.originalTrainerName?.takeIf { it.isNotBlank() }?.let { Texts.escape(it) } ?: "-"
        return result
    }

    private fun genderSymbol(gender: Gender): String = when (gender) {
        Gender.MALE -> "<blue>♂</blue>"
        Gender.FEMALE -> "<light_purple>♀</light_purple>"
        else -> "<gray>-</gray>"
    }

    private fun stats(stats: PokemonStats): String =
        STAT_LABELS.joinToString(" <dark_gray>·</dark_gray> ") { (stat, label) -> "$label ${stats.getOrDefault(stat)}" }
}
