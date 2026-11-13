package com.f4xizzz.serverbuddy.modules.missions

/** Como o progresso de um objetivo é medido. */
enum class Kind {
    /** Estatística do Minecraft (blocos quebrados, passos, mobs mortos...): conta só o que aconteceu depois que a missão começou. */
    STAT,

    /** Estado de agora (nível de XP, itens no inventário): vale o valor atual. */
    STATE,

    /** Evento que alguém avisa (Cobblemon, comando manual): soma a cada aviso. */
    EVENT
}

/** O que cada verbo significa. Para os de estatística [stat] é o registro de onde ler; [divisor] converte (cm → blocos). */
class VerbInfo(val canonical: String, val kind: Kind, val needsTarget: Boolean = false, val stat: String? = null, val divisor: Long = 1, val unit: String = "")

/**
 * Um objetivo de missão, escrito como `verbo [alvo] quantidade`:
 * `mine minecraft:stone 100`, `mine #minecraft:logs 50`, `kill any 20`, `walk 5000`, `fish 3`, `play_time 30`,
 * `level 30`, `has minecraft:diamond 5`, `capture shiny 1`, `manual visitar_spawn 1`.
 */
class Objective(val verb: VerbInfo, val target: String?, val amount: Long, val raw: String) {
    val kind: Kind get() = verb.kind

    companion object {
        private val VERBS: Map<String, VerbInfo> = buildMap {
            fun add(info: VerbInfo, vararg aliases: String) {
                put(info.canonical, info)
                for (alias in aliases) put(alias, info)
            }
            // estatísticas com alvo (um bloco, item ou mob; "any" soma todos; "#tag" soma uma tag)
            add(VerbInfo("mine", Kind.STAT, needsTarget = true), "break", "dig")
            add(VerbInfo("craft", Kind.STAT, needsTarget = true))
            add(VerbInfo("use", Kind.STAT, needsTarget = true), "place", "eat", "drink")
            add(VerbInfo("pickup", Kind.STAT, needsTarget = true), "collect")
            add(VerbInfo("drop", Kind.STAT, needsTarget = true))
            add(VerbInfo("break_item", Kind.STAT, needsTarget = true), "wear_out")
            add(VerbInfo("kill", Kind.STAT, needsTarget = true))
            // estatísticas sem alvo
            add(VerbInfo("walk", Kind.STAT, stat = "walk_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("sprint", Kind.STAT, stat = "sprint_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("swim", Kind.STAT, stat = "swim_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("fly", Kind.STAT, stat = "aviate_one_cm", divisor = 100, unit = "blocks"), "elytra")
            add(VerbInfo("climb", Kind.STAT, stat = "climb_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("boat", Kind.STAT, stat = "boat_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("horse", Kind.STAT, stat = "horse_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("fall", Kind.STAT, stat = "fall_one_cm", divisor = 100, unit = "blocks"))
            add(VerbInfo("jump", Kind.STAT, stat = "jump", unit = "jumps"))
            add(VerbInfo("play_time", Kind.STAT, stat = "play_time", divisor = 1200, unit = "minutes"), "playtime")
            add(VerbInfo("deaths", Kind.STAT, stat = "deaths", unit = "deaths"), "die")
            add(VerbInfo("damage", Kind.STAT, stat = "damage_dealt", divisor = 10, unit = "damage"))
            add(VerbInfo("fish", Kind.STAT, stat = "fish_caught", unit = "fish"))
            add(VerbInfo("breed", Kind.STAT, stat = "animals_bred", unit = "animals"))
            add(VerbInfo("trade", Kind.STAT, stat = "traded_with_villager", unit = "trades"))
            add(VerbInfo("enchant", Kind.STAT, stat = "enchant_item", unit = "enchantments"))
            add(VerbInfo("sleep", Kind.STAT, stat = "sleep_in_bed", unit = "times"))
            add(VerbInfo("open_chest", Kind.STAT, stat = "open_chest", unit = "chests"), "chest")
            add(VerbInfo("raid", Kind.STAT, stat = "raid_win", unit = "raids"))
            add(VerbInfo("player_kills", Kind.STAT, stat = "player_kills", unit = "players"), "pvp")
            // estado de agora
            add(VerbInfo("level", Kind.STATE, unit = "levels"), "xp_level")
            add(VerbInfo("has", Kind.STATE, needsTarget = true))
            // eventos
            add(VerbInfo("manual", Kind.EVENT, needsTarget = true))
            add(VerbInfo("capture", Kind.EVENT, unit = "captures"), "catch")
            add(VerbInfo("defeat", Kind.EVENT, unit = "wins"))
            add(VerbInfo("battle_win", Kind.EVENT, unit = "wins"), "battle")
            add(VerbInfo("evolve", Kind.EVENT, unit = "evolutions"), "evolution")
            add(VerbInfo("hatch", Kind.EVENT, unit = "eggs"))
            add(VerbInfo("fossil", Kind.EVENT, unit = "fossils"), "revive")
            add(VerbInfo("berry", Kind.EVENT, unit = "berries"), "berries")
            add(VerbInfo("apricorn", Kind.EVENT, unit = "apricorns"))
            add(VerbInfo("candy", Kind.EVENT, unit = "candies"), "candy_use")
            add(VerbInfo("held_item", Kind.EVENT, unit = "items"), "helditem")
            add(VerbInfo("scan", Kind.EVENT, unit = "scans"), "pokedex_scan")
            add(VerbInfo("level_up", Kind.EVENT, unit = "levels"), "levelup")
            add(VerbInfo("gained", Kind.EVENT, unit = "Pokémon"), "gain")
            add(VerbInfo("pokefish", Kind.EVENT, unit = "fishing catches"), "fish_pokemon")
        }

        /** Os verbos que existem (para a ajuda dos comandos e das configs). */
        fun verbs(): List<String> = VERBS.values.map { it.canonical }.distinct().sorted()

        /** Lê `verbo [alvo] quantidade`. null se está mal escrito. */
        @JvmStatic
        fun parse(text: String): Objective? {
            val parts = text.trim().split(Regex("\\s+"))
            if (parts.size < 2) return null
            val amount = parts.last().toLongOrNull() ?: return null
            if (amount <= 0) return null
            val verb = VERBS[parts[0].lowercase()] ?: return null
            val middle = parts.subList(1, parts.size - 1)
            val target = middle.firstOrNull()
            if (verb.needsTarget && target == null) return null
            if (!verb.needsTarget && verb.kind != Kind.EVENT && target != null) return null
            if (middle.size > 1) return null
            return Objective(verb, target, amount, text.trim())
        }
    }
}

/** Filtro dos objetivos do Cobblemon: `any`, uma espécie (`pikachu`), `shiny`, `legendary` ou `type:fire`. */
object PokemonFilter {
    class Info(val species: String, val shiny: Boolean, val legendary: Boolean, val types: Set<String>)

    @JvmStatic
    fun matches(target: String?, info: Info): Boolean {
        val t = target?.trim()?.lowercase().orEmpty()
        return when {
            t.isEmpty() || t == "any" || t == "*" -> true
            t == "shiny" -> info.shiny
            t == "legendary" || t == "lendario" -> info.legendary
            t.startsWith("type:") -> t.removePrefix("type:") in info.types
            else -> t.removePrefix("cobblemon:").replace(Regex("[^a-z0-9]"), "") == info.species.lowercase().replace(Regex("[^a-z0-9]"), "")
        }
    }
}

/** Como os alvos dos objetivos de evento (frutinha, apricorn, doce, item, nível) são comparados com o que aconteceu. Lógica pura. */
object EventTargets {
    private fun isAny(target: String?): Boolean = target == null || target.isBlank() || target.equals("any", ignoreCase = true) || target == "*"

    /** Tira o que não ajuda a comparar ids: maiúsculas, "cobblemon:" and "minecraft:" na frente e os sufixos de [strip] no fim. */
    @JvmStatic
    fun normalizeId(id: String, vararg strip: String): String {
        var out = id.trim().lowercase().removePrefix("cobblemon:").removePrefix("minecraft:")
        for (suffix in strip) out = out.removeSuffix(suffix)
        return out
    }

    /** O alvo ("oran", "cobblemon:oran_berry", "any"...) é o mesmo [id] que aconteceu? */
    @JvmStatic
    fun matchesId(target: String?, id: String, vararg strip: String): Boolean =
        isAny(target) || normalizeId(target.orEmpty(), *strip) == normalizeId(id, *strip)

    /** O filtro de Pokémon de "pikachu@50" (ou "any@30"): [target] sem o nível mínimo, e o nível mínimo (0 se não há). */
    @JvmStatic
    fun splitLevel(target: String?): Pair<String, Int> {
        val raw = target?.trim().orEmpty()
        return raw.substringBefore('@').ifBlank { "any" } to (raw.substringAfter('@', "").trim().toIntOrNull() ?: 0)
    }

    /** O Pokémon que subiu para [newLevel] vale para o alvo? "pikachu@50" = só quando um Pikachu chega ao nível 50 ou mais. */
    @JvmStatic
    fun matchesLevelUp(target: String?, info: PokemonFilter.Info, newLevel: Int): Boolean {
        val (filter, minLevel) = splitLevel(target)
        return newLevel >= minLevel && PokemonFilter.matches(filter, info)
    }
}

/** Barra de progresso em texto (MiniMessage): `████░░░░░░`. */
object ProgressBar {
    @JvmStatic
    fun render(current: Long, total: Long, width: Int = 10, done: String = "<green>", todo: String = "<dark_gray>"): String {
        val fraction = if (total <= 0) 1.0 else (current.toDouble() / total).coerceIn(0.0, 1.0)
        val filled = Math.round(fraction * width).toInt().coerceIn(0, width)
        return done + "█".repeat(filled) + todo + "█".repeat(width - filled)
    }
}
