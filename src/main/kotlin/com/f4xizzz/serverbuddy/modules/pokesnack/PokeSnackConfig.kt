package com.f4xizzz.serverbuddy.modules.pokesnack

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/PokeSnack/config.json` (only created when Cobblemon is installed). */
@Doc(
    "Poké Snack · server-side tuning of the Cobblemon Poké Snack (the cake players cook and place to attract Pokémon).\n" +
        "\n" +
        "WHAT THIS CHANGES\n" +
        "  - max_pokemon_per_chunk: how many snack-spawned Pokémon may be around a snack at once (Cobblemon's own limit, set here).\n" +
        "  - buffs: what each seasoning ingredient does to the Pokémon a snack attracts (shiny rerolls, IVs, natures, rarity...).\n" +
        "    Cobblemon defines those effects in datapack files; this file lets you tune them WITHOUT a datapack.\n" +
        "    The same effect data is used by Poké Rod fishing bait, so a change here also affects bait made from the same item.\n" +
        "\n" +
        "To limit how many Poké Snack BLOCKS can be placed in one chunk, see the \"poke_snack\" group in ChunkLimits/config.json.\n" +
        "Changes are applied on /serverbuddy reload. Snacks that are already placed keep their old buffs until their chunk reloads."
)
class PokeSnackConfig(
    @Doc("false = this module does nothing (Cobblemon's own values stay as they are).")
    val enabled: Boolean = true,
    @SerializedName("max_pokemon_per_chunk")
    @Doc("Cobblemon's \"pokeSnackPokemonPerChunk\": how many Pokémon a snack keeps around it. -1 = leave Cobblemon's value alone.")
    val maxPokemonPerChunk: Double = -1.0,
    val buffs: SnackBuffsConfig = SnackBuffsConfig()
)

class SnackBuffsConfig(
    @Doc("false = the buffs stay exactly as the datapacks define them.")
    val enabled: Boolean = true,
    @SerializedName("chance_multiplier")
    @Doc("Multiplies the CHANCE of every effect (result capped at 1.0). 2.0 = every buff triggers twice as often.")
    val chanceMultiplier: Double = 1.0,
    @SerializedName("value_multiplier")
    @Doc("Multiplies the VALUE (strength) of every effect. 2.0 = every buff is twice as strong.")
    val valueMultiplier: Double = 1.0,
    @Doc(
        "Per effect type. The key is the effect id (\"cobblemon:shiny_reroll\") or \"id@subcategory\" for one variant (\"cobblemon:ev@cobblemon:atk\").\n" +
            "A specific key wins over the plain id. What the types mean (chance = how often it triggers 0-1, value = strength):\n" +
            "  cobblemon:shiny_reroll   value = extra shiny rolls     cobblemon:iv             value = IV points guaranteed (subcategory = stat)\n" +
            "  cobblemon:ev             value = EV yield (stat)       cobblemon:nature         forces the nature tied to the stat (subcategory)\n" +
            "  cobblemon:typing         weight multiplier for a type  cobblemon:egg_group      weight multiplier for an egg group\n" +
            "  cobblemon:rarity_bucket  weight of a rarity bucket     cobblemon:bite_time      faster spawns (value = reduction 0-1)\n" +
            "  cobblemon:ha_chance      hidden ability chance         cobblemon:alpha_chance   chance of an alpha Pokémon\n" +
            "  cobblemon:gender_chance  forces a gender (subcategory) cobblemon:level_raise    value = levels added\n" +
            "  cobblemon:mark_chance    marks (value = attempts)      cobblemon:drops_reroll   extra drop rerolls\n" +
            "  cobblemon:size           size change in % (+/-)        cobblemon:friendship     starting friendship\n" +
            "Each rule: enabled (false removes the effect everywhere), chance_multiplier / value_multiplier, and chance / value to force an\n" +
            "exact number (-1 = keep the original). Forced numbers replace the original BEFORE the multipliers are applied."
    )
    val effects: Map<String, EffectRule> = defaultEffects(),
    @Doc(
        "Per ingredient (the item players put in the cooking pot). mode:\n" +
            "  \"keep\"    = leave the ingredient alone (this file's default so the examples change nothing)\n" +
            "  \"replace\" = the ingredient gives EXACTLY the effects listed here (an empty list = no buffs at all)\n" +
            "  \"add\"     = the ingredient keeps its effects and also gives the ones listed here\n" +
            "Only items Cobblemon already accepts as bait seasoning can be used (the recipe filters by tag). Effects: { \"type\", \"subcategory\"\n" +
            "(optional), \"chance\", \"value\" } as in the datapack. The effect rules above still apply on top."
    )
    val ingredients: Map<String, IngredientRule> = defaultIngredients()
) {
    companion object {
        /** Every effect type Cobblemon 1.8 registers, with neutral settings (so the file lists them all). */
        fun defaultEffects(): Map<String, EffectRule> {
            val map = LinkedHashMap<String, EffectRule>()
            for (type in listOf(
                "shiny_reroll", "iv", "ev", "nature", "typing", "egg_group", "rarity_bucket", "bite_time", "ha_chance",
                "alpha_chance", "gender_chance", "level_raise", "mark_chance", "drops_reroll", "size", "friendship"
            )) map["cobblemon:$type"] = EffectRule()
            return map
        }

        fun defaultIngredients(): Map<String, IngredientRule> = linkedMapOf(
            "minecraft:golden_apple" to IngredientRule(
                mode = "keep",
                effects = listOf(EffectSpec(type = "cobblemon:shiny_reroll", chance = 1.0, value = 1.0))
            )
        )
    }
}

class EffectRule(
    @Doc("false = this effect never happens.")
    val enabled: Boolean = true,
    @SerializedName("chance_multiplier")
    val chanceMultiplier: Double = 1.0,
    @SerializedName("value_multiplier")
    val valueMultiplier: Double = 1.0,
    @Doc("Force the chance (0-1). -1 = keep the original.")
    val chance: Double = -1.0,
    @Doc("Force the value. -1 = keep the original.")
    val value: Double = -1.0
)

class IngredientRule(
    @Doc("keep | replace | add")
    val mode: String = "keep",
    val effects: List<EffectSpec> = emptyList()
)

class EffectSpec(
    val type: String = "",
    @Doc("Optional: stat, type, egg group or gender the effect is about (\"cobblemon:atk\", \"fire\"...).")
    val subcategory: String = "",
    val chance: Double = 1.0,
    val value: Double = 1.0
)
