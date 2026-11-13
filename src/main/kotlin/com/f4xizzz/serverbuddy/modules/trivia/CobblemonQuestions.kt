package com.f4xizzz.serverbuddy.modules.trivia

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import kotlin.random.Random

/**
 * Perguntas geradas com os dados do Cobblemon (espécies e golpes). Só são registradas quando o Cobblemon está instalado.
 * Todas aceitam `"exclude": ["cobblemon:mewtwo", "thunderbolt"]` para tirar espécies ou golpes do sorteio e `"text"`
 * para trocar o enunciado (com os mesmos %valores%).
 */
internal object CobblemonQuestions {
    // nomes dos tipos em português, aceitos junto com o nome em inglês
    private val TYPES_PT = mapOf(
        "normal" to "normal", "fire" to "fogo", "water" to "água", "grass" to "planta", "electric" to "elétrico", "ice" to "gelo",
        "fighting" to "lutador", "poison" to "veneno", "ground" to "terra", "flying" to "voador", "psychic" to "psíquico",
        "bug" to "inseto", "rock" to "pedra", "ghost" to "fantasma", "dragon" to "dragão", "dark" to "sombrio", "steel" to "aço",
        "fairy" to "fada"
    )

    fun register() {
        Questions.register("dex_number_to_species") { o, r -> species(o, r) { s -> Question(text(o, "Which Pokémon has National Pokédex number %dex_number%?").replace("%dex_number%", s.nationalPokedexNumber.toString()), names(s)) } }
        Questions.register("species_to_dex_number") { o, r -> species(o, r) { s -> Question(text(o, "What is the National Pokédex number of %species%?").replace("%species%", name(s)), listOf(s.nationalPokedexNumber.toString()), hint = "") } }
        Questions.register("description_to_species") { o, r -> species(o, r) { s -> describe(s)?.let { Question(text(o, "Which Pokémon is this? %description%").replace("%description%", it), names(s)) } } }
        Questions.register("species_to_type") { o, r -> species(o, r) { s -> Question(text(o, "What type is %species%? (either type is accepted)").replace("%species%", name(s)), s.types.flatMap { typeNames(it.name, it.displayName.string) }) } }
        Questions.register("species_to_ability") { o, r -> species(o, r) { s -> abilities(s).takeIf { it.isNotEmpty() }?.let { Question(text(o, "Name an ability of %species%.").replace("%species%", name(s)), it) } } }
        Questions.register("species_to_egg_group") { o, r -> species(o, r) { s -> s.eggGroups.map { group -> group.name.replace('_', ' ').lowercase() }.takeIf { it.isNotEmpty() }?.let { Question(text(o, "What is the egg group of %species%?").replace("%species%", name(s)), it) } } }
        Questions.register("unscramble_species") { o, r -> species(o, r) { s -> Question(text(o, "Unscramble this Pokémon name: %species%").replace("%species%", UnscrambleGenerator.scramble(name(s), r)), names(s), hint = null) } }
        Questions.register("description_to_move") { o, r -> move(o, r) { m -> Question(text(o, "Which move is this? %description%").replace("%description%", m.description.string.replace(m.displayName.string, "???")), listOf(m.displayName.string, m.name)) } }
        Questions.register("move_to_type") { o, r -> move(o, r) { m -> Question(text(o, "What type is the move %move%?").replace("%move%", m.displayName.string), typeNames(m.elementalType.name, m.elementalType.displayName.string)) } }
        Questions.register("unscramble_move") { o, r -> move(o, r) { m -> Question(text(o, "Unscramble this move name: %move%").replace("%move%", UnscrambleGenerator.scramble(m.displayName.string, r)), listOf(m.displayName.string, m.name)) } }
    }

    private fun text(options: JsonObject, default: String) = Questions.template(options, default)

    private fun excluded(options: JsonObject): Set<String> =
        options.get("exclude")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonPrimitive }?.map { it.asString.lowercase() }?.toSet().orEmpty()

    private fun species(options: JsonObject, random: Random, build: (Species) -> Question?): Question? {
        val skip = excluded(options)
        val all = PokemonSpecies.species.filter { it.resourceIdentifier.toString().lowercase() !in skip && it.name.lowercase() !in skip }
        repeat(6) { build(all.randomOrNull(random) ?: return null)?.let { return it } }
        return null
    }

    private fun move(options: JsonObject, random: Random, build: (MoveTemplate) -> Question?): Question? {
        val skip = excluded(options)
        val all = Moves.all().filter { it.name.lowercase() !in skip }
        repeat(6) { build(all.randomOrNull(random) ?: return null)?.let { return it } }
        return null
    }

    private fun name(species: Species): String = species.translatedName.string

    /** O nome traduzido e o nome interno (o jogador pode digitar qualquer um). */
    private fun names(species: Species): List<String> = listOf(name(species), species.name).distinct()

    private fun typeNames(key: String, display: String): List<String> = listOfNotNull(display, key, TYPES_PT[key.lowercase()]).distinct()

    private fun abilities(species: Species): List<String> = species.abilities.map { Component.translatable(it.template.displayName).string }.distinct()

    /** A descrição da Pokédex com o nome da espécie escondido (senão a pergunta já entrega a resposta). */
    private fun describe(species: Species): String? {
        val key = species.resourceIdentifier.namespace + ".species." + species.name.replace(Regex("\\P{Alnum}+"), "").lowercase() + ".desc"
        val text = Component.translatable(key).string
        if (text == key || text.isBlank()) return null
        return text.replace(name(species), "???", ignoreCase = true)
    }
}
