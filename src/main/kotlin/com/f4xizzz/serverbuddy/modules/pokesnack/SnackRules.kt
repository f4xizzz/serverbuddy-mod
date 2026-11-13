package com.f4xizzz.serverbuddy.modules.pokesnack

/** A Cobblemon bait effect without Cobblemon types, so the tuning rules can be tested on their own. */
data class Fx(val type: String, val subcategory: String?, val chance: Double, val value: Double)

/**
 * The tuning rules of `PokeSnack/config.json` → `buffs`: turns the effects a seasoning or bait has in the datapacks into the
 * effects the server wants. Pure functions: [SnackBuffs] does the Cobblemon side.
 */
object SnackRules {
    /** "shiny_reroll" and "cobblemon:shiny_reroll" are the same effect. */
    fun normalizeType(id: String): String {
        val text = id.trim().lowercase()
        return if (':' in text) text else "cobblemon:$text"
    }

    /** "golden_apple" and "minecraft:golden_apple" are the same item. */
    fun normalizeItem(id: String): String {
        val text = id.trim().lowercase()
        return if (':' in text) text else "minecraft:$text"
    }

    /** Only the part after the namespace: "cobblemon:atk" and "atk" match each other ("minecraft:water_1" and "water_1" too). */
    private fun path(id: String): String = id.trim().lowercase().substringAfter(':')

    /**
     * Effects of one bait/seasoning entry after the config is applied.
     *
     * [ingredient] is the item the entry belongs to (null when it is matched by tag). An ingredient rule with mode "replace" gives
     * its effects to the FIRST entry of that ingredient only (Cobblemon can define the same item twice: as bait and as seasoning);
     * [claimed] remembers which ingredients already did.
     */
    fun adjust(effects: List<Fx>, ingredient: String?, config: SnackBuffsConfig, claimed: MutableSet<String>): List<Fx> {
        var list = effects
        val rule = ingredient?.let { id -> config.ingredients.entries.firstOrNull { normalizeItem(it.key) == id }?.value }
        if (ingredient != null && rule != null) {
            when (rule.mode.trim().lowercase()) {
                "replace" -> list = if (claimed.add(ingredient)) rule.effects.mapNotNull(::specToFx) else emptyList()
                "add" -> if (claimed.add(ingredient)) list = list + rule.effects.mapNotNull(::specToFx)
            }
        }
        return list.mapNotNull { adjustOne(it, config) }
    }

    private fun specToFx(spec: EffectSpec): Fx? {
        if (spec.type.isBlank()) return null
        return Fx(normalizeType(spec.type), spec.subcategory.ifBlank { null }, spec.chance.coerceIn(0.0, 1.0), spec.value)
    }

    private fun adjustOne(fx: Fx, config: SnackBuffsConfig): Fx? {
        val rule = ruleFor(fx, config)
        if (rule != null && !rule.enabled) return null
        var chance = if (rule != null && rule.chance >= 0.0) rule.chance else fx.chance
        var value = if (rule != null && rule.value >= 0.0) rule.value else fx.value
        chance *= (rule?.chanceMultiplier ?: 1.0) * config.chanceMultiplier
        value *= (rule?.valueMultiplier ?: 1.0) * config.valueMultiplier
        return Fx(fx.type, fx.subcategory, chance.coerceIn(0.0, 1.0), value)
    }

    /** The rule for this effect: "type@subcategory" wins over "type". */
    fun ruleFor(fx: Fx, config: SnackBuffsConfig): EffectRule? {
        var plain: EffectRule? = null
        for ((key, rule) in config.effects) {
            val at = key.indexOf('@')
            if (at < 0) {
                if (normalizeType(key) == fx.type) plain = rule
            } else if (normalizeType(key.substring(0, at)) == fx.type && fx.subcategory != null && path(key.substring(at + 1)) == path(fx.subcategory)) {
                return rule
            }
        }
        return plain
    }
}
