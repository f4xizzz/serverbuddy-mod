package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Durations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MissionsLogicTest {
    @Test
    fun `le objetivos com alvo e sem alvo`() {
        val mine = Objective.parse("mine minecraft:stone 100")!!
        assertEquals("mine", mine.verb.canonical)
        assertEquals("minecraft:stone", mine.target)
        assertEquals(100L, mine.amount)
        assertEquals(Kind.STAT, mine.kind)

        val walk = Objective.parse("walk 5000")!!
        assertEquals("walk_one_cm", walk.verb.stat)
        assertEquals(100L, walk.verb.divisor)
        assertNull(walk.target)

        assertEquals("mine", Objective.parse("break #minecraft:logs 20")!!.verb.canonical)
        assertEquals("use", Objective.parse("eat minecraft:bread 5")!!.verb.canonical)
        assertEquals(Kind.STATE, Objective.parse("level 30")!!.kind)
        assertEquals(Kind.STATE, Objective.parse("has minecraft:diamond 5")!!.kind)
        assertEquals(Kind.EVENT, Objective.parse("capture shiny 1")!!.kind)
        assertEquals("shiny", Objective.parse("capture shiny 1")!!.target)
        assertNull(Objective.parse("capture 5")!!.target)
        assertEquals("visitar_spawn", Objective.parse("manual visitar_spawn 1")!!.target)
        assertEquals("kill", Objective.parse("KILL any 15")!!.verb.canonical)
    }

    @Test
    fun `rejeita objetivos mal escritos`() {
        assertNull(Objective.parse(""))
        assertNull(Objective.parse("mine"))
        assertNull(Objective.parse("mine minecraft:stone"))
        assertNull(Objective.parse("mine 100"))
        assertNull(Objective.parse("voar 10"))
        assertNull(Objective.parse("walk minecraft:stone 10"))
        assertNull(Objective.parse("mine minecraft:stone 0"))
        assertNull(Objective.parse("mine minecraft:stone -3"))
        assertNull(Objective.parse("mine minecraft:stone minecraft:dirt 5"))
        assertTrue(Objective.verbs().containsAll(listOf("mine", "walk", "capture", "manual", "play_time")))
    }

    @Test
    fun `filtro de pokemon`() {
        val pikachu = PokemonFilter.Info("Pikachu", shiny = false, legendary = false, types = setOf("electric"))
        val mewtwo = PokemonFilter.Info("Mewtwo", shiny = true, legendary = true, types = setOf("psychic"))
        assertTrue(PokemonFilter.matches(null, pikachu))
        assertTrue(PokemonFilter.matches("any", pikachu))
        assertTrue(PokemonFilter.matches("pikachu", pikachu))
        assertTrue(PokemonFilter.matches("cobblemon:pikachu", pikachu))
        assertFalse(PokemonFilter.matches("raichu", pikachu))
        assertTrue(PokemonFilter.matches("type:electric", pikachu))
        assertFalse(PokemonFilter.matches("type:fire", pikachu))
        assertFalse(PokemonFilter.matches("shiny", pikachu))
        assertTrue(PokemonFilter.matches("shiny", mewtwo))
        assertTrue(PokemonFilter.matches("legendary", mewtwo))
        // pontuação e maiúsculas não atrapalham: "Mr. Mime" é o mesmo que mr_mime
        assertTrue(PokemonFilter.matches("mr_mime", PokemonFilter.Info("Mr. Mime", false, false, emptySet())))
    }

    @Test
    fun `barra de progresso`() {
        assertEquals("<green>" + "█".repeat(5) + "<dark_gray>" + "█".repeat(5), ProgressBar.render(50, 100))
        assertEquals("<green>" + "█".repeat(10) + "<dark_gray>", ProgressBar.render(100, 100))
        assertEquals("<green><dark_gray>" + "█".repeat(10), ProgressBar.render(0, 100))
        assertEquals("<green>" + "█".repeat(10) + "<dark_gray>", ProgressBar.render(500, 100)) // passou do total
    }

    @Test
    fun `periodo diario troca na hora configurada`() {
        val before = LocalDateTime.of(2026, 12, 5, 3, 59)
        val after = LocalDateTime.of(2026, 12, 5, 4, 0)
        assertEquals("2026-12-04", MissionRules.periodKey(MissionPeriod.DAILY, before, 4, "MONDAY"))
        assertEquals("2026-12-05", MissionRules.periodKey(MissionPeriod.DAILY, after, 4, "MONDAY"))
        assertEquals("", MissionRules.periodKey(MissionPeriod.NONE, after, 4, "MONDAY"))
        // a próxima troca é às 4h do dia seguinte
        assertEquals(LocalDateTime.of(2026, 12, 6, 4, 0), MissionRules.nextReset(MissionPeriod.DAILY, after, 4, "MONDAY"))
        assertNull(MissionRules.nextReset(MissionPeriod.NONE, after, 4, "MONDAY"))
    }

    @Test
    fun `periodo semanal comeca no dia escolhido`() {
        // 2026-12-05 é sábado; a segunda anterior é 2026-11-30
        assertEquals("2026-11-30", MissionRules.periodKey(MissionPeriod.WEEKLY, LocalDateTime.of(2026, 12, 5, 12, 0), 4, "MONDAY"))
        // segunda-feira 3h ainda é a semana passada (a troca é às 4h)
        assertEquals("2026-11-30", MissionRules.periodKey(MissionPeriod.WEEKLY, LocalDateTime.of(2026, 12, 7, 3, 0), 4, "MONDAY"))
        assertEquals("2026-12-07", MissionRules.periodKey(MissionPeriod.WEEKLY, LocalDateTime.of(2026, 12, 7, 4, 0), 4, "MONDAY"))
        assertEquals("2026-12-05", MissionRules.periodKey(MissionPeriod.WEEKLY, LocalDateTime.of(2026, 12, 5, 12, 0), 4, "saturday"))
        assertEquals(LocalDateTime.of(2026, 12, 7, 4, 0), MissionRules.nextReset(MissionPeriod.WEEKLY, LocalDateTime.of(2026, 12, 5, 12, 0), 4, "MONDAY"))
    }

    @Test
    fun `sorteio do periodo e sempre o mesmo para a mesma semente`() {
        val ids = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        val first = MissionRules.rotation(ids, 3, "2026-12-05/diarias")
        assertEquals(3, first.size)
        assertEquals(first, MissionRules.rotation(ids.reversed(), 3, "2026-12-05/diarias"))
        assertTrue(ids.containsAll(first))
        // outro dia, outro sorteio (com 8 opções e 3 vagas, é muito improvável repetir em 10 dias seguidos)
        val different = (6..15).map { MissionRules.rotation(ids, 3, "2026-12-$it/diarias") }.toSet()
        assertTrue(different.size > 1)
        assertEquals(ids.toSet(), MissionRules.rotation(ids, 0, "x"))
        assertEquals(ids.toSet(), MissionRules.rotation(ids, 20, "x"))
    }

    @Test
    fun `duracoes em texto`() {
        assertEquals(30_000L, Durations.parseMillis("30s"))
        assertEquals(900_000L, Durations.parseMillis("15m"))
        assertEquals(5_400_000L, Durations.parseMillis("1h30m"))
        assertEquals(86_400_000L, Durations.parseMillis("1d"))
        assertEquals(604_800_000L, Durations.parseMillis("1w"))
        assertEquals(45_000L, Durations.parseMillis("45"))
        assertEquals(7_200_000L, Durations.parseMillis(" 2H "))
        assertNull(Durations.parseMillis(""))
        assertNull(Durations.parseMillis(null))
        assertNull(Durations.parseMillis("abc"))
        assertNull(Durations.parseMillis("10x"))
        assertEquals("45s", Durations.format(45_000))
        assertEquals("1m 30s", Durations.format(90_000))
        assertEquals("1h 5m", Durations.format(3_900_000))
        assertEquals("2d", Durations.format(172_800_000))
        assertNotNull(Durations.parseMillis("24h"))
    }
}
