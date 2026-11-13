package com.f4xizzz.serverbuddy.modules.pokesnack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnackRulesTest {
    private val shiny = Fx("cobblemon:shiny_reroll", null, 1.0, 4.0)
    private val nature = Fx("cobblemon:nature", "cobblemon:spd", 0.5, 0.0)
    private val iv = Fx("cobblemon:iv", "cobblemon:atk", 1.0, 5.0)

    private fun config(
        chance: Double = 1.0, value: Double = 1.0,
        effects: Map<String, EffectRule> = emptyMap(), ingredients: Map<String, IngredientRule> = emptyMap()
    ) = SnackBuffsConfig(true, chance, value, effects, ingredients)

    private fun adjust(list: List<Fx>, cfg: SnackBuffsConfig, ingredient: String? = null) = SnackRules.adjust(list, ingredient, cfg, HashSet())

    @Test
    fun `neutral config changes nothing`() {
        val list = listOf(shiny, nature, iv)
        assertEquals(list, adjust(list, SnackBuffsConfig()))
    }

    @Test
    fun `global multipliers scale chance and value and cap the chance at 1`() {
        val result = adjust(listOf(shiny, nature), config(chance = 3.0, value = 2.0))
        assertEquals(Fx("cobblemon:shiny_reroll", null, 1.0, 8.0), result[0])
        assertEquals(Fx("cobblemon:nature", "cobblemon:spd", 1.0, 0.0), result[1])
    }

    @Test
    fun `effect rule forces numbers before the multipliers`() {
        val rules = mapOf("cobblemon:shiny_reroll" to EffectRule(value = 10.0, chanceMultiplier = 0.5))
        val result = adjust(listOf(shiny), config(value = 2.0, effects = rules))
        assertEquals(Fx("cobblemon:shiny_reroll", null, 0.5, 20.0), result[0])
    }

    @Test
    fun `a short type name means cobblemon`() {
        val rules = mapOf("shiny_reroll" to EffectRule(enabled = false))
        assertTrue(adjust(listOf(shiny, iv), config(effects = rules)).none { it.type == "cobblemon:shiny_reroll" })
        assertEquals("cobblemon:iv", SnackRules.normalizeType("IV"))
        assertEquals("minecraft:golden_apple", SnackRules.normalizeItem("golden_apple"))
    }

    @Test
    fun `a rule with a subcategory wins over the plain one and ignores the namespace`() {
        val rules = linkedMapOf(
            "cobblemon:iv" to EffectRule(valueMultiplier = 2.0),
            "cobblemon:iv@atk" to EffectRule(enabled = false)
        )
        val other = Fx("cobblemon:iv", "cobblemon:hp", 1.0, 5.0)
        val result = adjust(listOf(iv, other), config(effects = rules))
        assertEquals(listOf(Fx("cobblemon:iv", "cobblemon:hp", 1.0, 10.0)), result)
    }

    @Test
    fun `ingredient replace gives the listed effects to the first entry only`() {
        val spec = EffectSpec(type = "cobblemon:ha_chance", chance = 0.3, value = 1.0)
        val ingredients = mapOf("minecraft:golden_apple" to IngredientRule("replace", listOf(spec)))
        val cfg = config(ingredients = ingredients)
        val claimed = HashSet<String>()
        val first = SnackRules.adjust(listOf(shiny), "minecraft:golden_apple", cfg, claimed)
        val second = SnackRules.adjust(listOf(nature), "minecraft:golden_apple", cfg, claimed)
        assertEquals(listOf(Fx("cobblemon:ha_chance", null, 0.3, 1.0)), first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `ingredient add keeps the original effects`() {
        val spec = EffectSpec(type = "alpha_chance", chance = 0.02, value = 1.0)
        val ingredients = mapOf("golden_apple" to IngredientRule("add", listOf(spec)))
        val result = adjust(listOf(shiny), config(ingredients = ingredients), "minecraft:golden_apple")
        assertEquals(2, result.size)
        assertEquals("cobblemon:alpha_chance", result[1].type)
    }

    @Test
    fun `ingredient keep and unknown modes change nothing`() {
        val spec = EffectSpec(type = "cobblemon:ha_chance", chance = 0.3, value = 1.0)
        val ingredients = mapOf("minecraft:golden_apple" to IngredientRule("keep", listOf(spec)))
        assertEquals(listOf(shiny), adjust(listOf(shiny), config(ingredients = ingredients), "minecraft:golden_apple"))
    }

    @Test
    fun `an ingredient rule does not touch other ingredients`() {
        val ingredients = mapOf("minecraft:golden_apple" to IngredientRule("replace", emptyList()))
        assertEquals(listOf(shiny), adjust(listOf(shiny), config(ingredients = ingredients), "minecraft:apple"))
        assertTrue(adjust(listOf(shiny), config(ingredients = ingredients), "minecraft:golden_apple").isEmpty())
    }
}
