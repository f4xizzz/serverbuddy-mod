package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Texts
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

/** O texto de cada objetivo para o jogador ("Quebrar Pedra", "Andar (blocos)"...), em MiniMessage. */
object ObjectiveLabels {
    private val VERB_NAMES = mapOf(
        "mine" to "Mine", "craft" to "Craft", "use" to "Use", "pickup" to "Pick up", "drop" to "Drop", "break_item" to "Wear out",
        "kill" to "Defeat", "walk" to "Walk", "sprint" to "Sprint", "swim" to "Swim", "fly" to "Fly with elytra", "climb" to "Climb",
        "boat" to "Travel by boat", "horse" to "Ride a horse", "fall" to "Fall", "jump" to "Jump", "play_time" to "Play", "deaths" to "Die",
        "damage" to "Deal damage", "fish" to "Fish", "breed" to "Breed animals", "trade" to "Trade with villagers", "enchant" to "Enchant items",
        "sleep" to "Sleep in beds", "open_chest" to "Open chests", "raid" to "Win raids", "player_kills" to "Defeat players",
        "level" to "Reach level", "has" to "Have in inventory", "manual" to "Objective", "capture" to "Capture", "defeat" to "Defeat wild Pokémon",
        "battle_win" to "Win battles", "evolve" to "Evolve", "hatch" to "Hatch eggs", "fossil" to "Revive fossils",
        "berry" to "Harvest berries", "apricorn" to "Harvest apricorns", "candy" to "Use experience candies", "held_item" to "Give held items to Pokémon",
        "scan" to "Scan Pokémon", "level_up" to "Level up", "gained" to "Obtain", "pokefish" to "Fish for Pokémon"
    )

    fun label(objective: Objective): String {
        val verb = objective.verb
        val name = VERB_NAMES[verb.canonical] ?: verb.canonical
        val target = objective.target?.let { targetName(verb.canonical, it) }
        val unit = if (verb.unit.isNotEmpty() && verb.kind != Kind.EVENT) " (${verb.unit})" else ""
        return when {
            verb.canonical == "manual" -> Texts.escape(objective.target ?: "")
            target != null -> "$name $target"
            verb.kind == Kind.EVENT && verb.unit.isNotEmpty() -> "$name ${verb.unit}".trim()
            else -> name + unit
        }
    }

    private fun targetName(verb: String, target: String): String {
        val any = target.equals("any", ignoreCase = true) || target == "*"
        if (any) return when (verb) {
            "mine" -> "blocks"
            "kill" -> "mobs"
            "capture", "defeat", "evolve", "hatch", "fossil", "scan", "level_up", "gained", "pokefish" -> "Pokémon"
            "berry" -> "berries"
            "apricorn" -> "apricorns"
            "candy" -> "candies"
            "held_item" -> "items"
            else -> "any item"
        }
        if (verb == "level_up") {
            val (filter, minLevel) = EventTargets.splitLevel(target)
            val who = if (filter.equals("any", ignoreCase = true)) "Pokémon" else Texts.escape(filter)
            return if (minLevel > 0) "$who (level $minLevel or higher)" else who
        }
        if (target.startsWith("#")) return "<italic>" + Texts.escape(target.substringAfter(':').replace('_', ' ').replace('/', ' ')) + "</italic>"
        val id = ResourceLocation.tryParse(if (':' in target) target else "minecraft:$target") ?: return Texts.escape(target)
        return when (verb) {
            "mine" -> BuiltInRegistries.BLOCK.getOptional(id).map { Texts.lang(it.descriptionId) }.orElse(Texts.escape(target))
            "kill" -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map { Texts.lang(it.descriptionId) }.orElse(Texts.escape(target))
            "craft", "use", "pickup", "drop", "break_item", "has" -> BuiltInRegistries.ITEM.getOptional(id).map { Texts.lang(it.descriptionId) }.orElse(Texts.escape(target))
            "candy", "held_item" -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(if (':' in target) target else "cobblemon:$target") ?: id)
                .map { Texts.lang(it.descriptionId) }.orElse(Texts.escape(target))
            else -> Texts.escape(target) // espécie, "shiny", "type:fire"...
        }
    }
}
