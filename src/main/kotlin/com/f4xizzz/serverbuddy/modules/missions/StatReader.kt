package com.f4xizzz.serverbuddy.modules.missions

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.StatType
import net.minecraft.stats.Stats
import net.minecraft.tags.TagKey

/** Lê no jogador o valor atual de um objetivo de estatística ou de estado (os de evento são contados à parte). */
object StatReader {
    /** O valor atual de [objective] (já convertido para a unidade do objetivo: blocos, minutos...). */
    fun read(player: ServerPlayer, objective: Objective): Long {
        val verb = objective.verb
        return when (verb.canonical) {
            "level" -> player.experienceLevel.toLong()
            "has" -> countItem(player, objective.target)
            "mine" -> sum(player, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK, objective.target)
            "craft" -> sum(player, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM, objective.target)
            "use" -> sum(player, Stats.ITEM_USED, BuiltInRegistries.ITEM, objective.target)
            "pickup" -> sum(player, Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM, objective.target)
            "drop" -> sum(player, Stats.ITEM_DROPPED, BuiltInRegistries.ITEM, objective.target)
            "break_item" -> sum(player, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM, objective.target)
            "kill" -> if (isAny(objective.target)) custom(player, "mob_kills") else sum(player, Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE, objective.target)
            else -> verb.stat?.let { custom(player, it) / verb.divisor } ?: 0L
        }
    }

    private fun isAny(target: String?): Boolean = target == null || target.equals("any", ignoreCase = true) || target == "*"

    private fun custom(player: ServerPlayer, id: String): Long {
        // o registro compara pela própria instância: tem que ser o ResourceLocation que o Minecraft registrou, não um igual
        val registered = BuiltInRegistries.CUSTOM_STAT.get(ResourceLocation.withDefaultNamespace(id)) ?: return 0
        return player.stats.getValue(Stats.CUSTOM.get(registered)).toLong()
    }

    /** Soma a estatística [type] de um valor (`minecraft:stone`), de uma tag (`#minecraft:logs`) ou de todos (`any`). */
    private fun <T : Any> sum(player: ServerPlayer, type: StatType<T>, registry: Registry<T>, target: String?): Long {
        val counter = player.stats
        if (isAny(target)) return registry.sumOf { counter.getValue(type, it).toLong() }
        val text = target!!.trim()
        if (text.startsWith("#")) {
            val tag = ResourceLocation.tryParse(text.substring(1)) ?: return 0
            val holders = registry.getTag(TagKey.create(registry.key(), tag)).orElse(null) ?: return 0
            return holders.sumOf { counter.getValue(type, it.value()).toLong() }
        }
        val id = ResourceLocation.tryParse(if (':' in text) text else "minecraft:$text") ?: return 0
        val value = registry.getOptional(id).orElse(null) ?: return 0
        return counter.getValue(type, value).toLong()
    }

    private fun countItem(player: ServerPlayer, target: String?): Long {
        val text = target?.trim().orEmpty()
        if (text.isEmpty()) return 0
        val id = ResourceLocation.tryParse(if (':' in text) text else "minecraft:$text") ?: return 0
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return 0
        var total = 0L
        for (stack in player.inventory.items) if (stack.`is`(item)) total += stack.count
        for (stack in player.inventory.offhand) if (stack.`is`(item)) total += stack.count
        return total
    }

    /** O alvo existe no jogo? (usado para avisar de erro de digitação nas missões). null = não dá para saber / não se aplica. */
    fun targetExists(objective: Objective): Boolean? {
        val text = objective.target?.trim() ?: return null
        if (text.equals("any", ignoreCase = true) || text == "*") return null
        val kind = objective.verb.canonical
        val registry: Registry<*> = when (kind) {
            "mine" -> BuiltInRegistries.BLOCK
            "craft", "use", "pickup", "drop", "break_item", "has" -> BuiltInRegistries.ITEM
            "kill" -> BuiltInRegistries.ENTITY_TYPE
            else -> return null
        }
        if (text.startsWith("#")) return ResourceLocation.tryParse(text.substring(1)) != null
        val id = ResourceLocation.tryParse(if (':' in text) text else "minecraft:$text") ?: return false
        return registry.containsKey(id)
    }
}
