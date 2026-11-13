package com.f4xizzz.serverbuddy.modules.missions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventObjectivesTest {
    private fun info(species: String, shiny: Boolean = false, legendary: Boolean = false, vararg types: String) =
        PokemonFilter.Info(species, shiny, legendary, types.toSet())

    // ---------------------------------------------------------------- os verbos novos

    @Test
    fun `os objetivos novos do Cobblemon sao lidos como eventos`() {
        for (text in listOf("fossil any 1", "berry oran 10", "apricorn red 5", "candy cobblemon:rare_candy 3", "held_item cobblemon:leftovers 1", "scan pikachu 3", "level_up any 5", "gained legendary 1", "pokefish any 3")) {
            val objective = Objective.parse(text)
            assertNotNull(objective, text)
            assertEquals(Kind.EVENT, objective!!.kind, text)
        }
    }

    @Test
    fun `os apelidos dos verbos novos valem`() {
        assertEquals("fossil", Objective.parse("revive any 1")!!.verb.canonical)
        assertEquals("berry", Objective.parse("berries any 2")!!.verb.canonical)
        assertEquals("candy", Objective.parse("candy_use any 2")!!.verb.canonical)
        assertEquals("held_item", Objective.parse("helditem any 1")!!.verb.canonical)
        assertEquals("scan", Objective.parse("pokedex_scan any 1")!!.verb.canonical)
        assertEquals("level_up", Objective.parse("levelup any 1")!!.verb.canonical)
        assertEquals("gained", Objective.parse("gain any 1")!!.verb.canonical)
        assertEquals("pokefish", Objective.parse("fish_pokemon any 1")!!.verb.canonical)
    }

    @Test
    fun `o alvo pode faltar nos verbos de evento`() {
        val objective = Objective.parse("berry 5")
        assertNotNull(objective)
        assertNull(objective!!.target)
        assertEquals(5L, objective.amount)
    }

    @Test
    fun `os verbos antigos continuam como eram`() {
        assertEquals(Kind.STAT, Objective.parse("fish 3")!!.kind)
        assertEquals(Kind.EVENT, Objective.parse("capture shiny 1")!!.kind)
        assertNull(Objective.parse("berry"))
    }

    // ---------------------------------------------------------------- ids

    @Test
    fun `um id e comparado sem o prefixo, as maiusculas e os sufixos`() {
        assertEquals("oran", EventTargets.normalizeId("Cobblemon:Oran_Berry", "_berry", "_block"))
        assertEquals("rare_candy", EventTargets.normalizeId("cobblemon:rare_candy"))
        assertEquals("red", EventTargets.normalizeId("RED", "_apricorn"))
        assertEquals("diamond", EventTargets.normalizeId("minecraft:diamond"))
    }

    @Test
    fun `any, vazio e asterisco valem para tudo`() {
        assertTrue(EventTargets.matchesId("any", "cobblemon:oran", "_berry"))
        assertTrue(EventTargets.matchesId("ANY", "x"))
        assertTrue(EventTargets.matchesId("*", "x"))
        assertTrue(EventTargets.matchesId(null, "x"))
        assertTrue(EventTargets.matchesId("  ", "x"))
    }

    @Test
    fun `a frutinha casa pelo nome em qualquer escrita`() {
        assertTrue(EventTargets.matchesId("oran", "cobblemon:oran", "_berry", "_block"))
        assertTrue(EventTargets.matchesId("oran_berry", "cobblemon:oran", "_berry", "_block"))
        assertTrue(EventTargets.matchesId("cobblemon:oran_berry", "cobblemon:oran", "_berry", "_block"))
        assertFalse(EventTargets.matchesId("pecha", "cobblemon:oran", "_berry", "_block"))
    }

    @Test
    fun `o apricorn casa pela cor`() {
        assertTrue(EventTargets.matchesId("red", "RED", "_apricorn"))
        assertTrue(EventTargets.matchesId("red_apricorn", "RED", "_apricorn"))
        assertFalse(EventTargets.matchesId("blue", "RED", "_apricorn"))
    }

    @Test
    fun `o doce e o item casam pelo id do item`() {
        assertTrue(EventTargets.matchesId("rare_candy", "cobblemon:rare_candy"))
        assertTrue(EventTargets.matchesId("cobblemon:rare_candy", "cobblemon:rare_candy"))
        assertFalse(EventTargets.matchesId("cobblemon:exp_candy_m", "cobblemon:rare_candy"))
        assertTrue(EventTargets.matchesId("cobblemon:leftovers", "cobblemon:leftovers"))
    }

    // ---------------------------------------------------------------- subir de nível

    @Test
    fun `o alvo de nivel separa o filtro do nivel minimo`() {
        assertEquals("pikachu" to 50, EventTargets.splitLevel("pikachu@50"))
        assertEquals("any" to 30, EventTargets.splitLevel("any@30"))
        assertEquals("any" to 30, EventTargets.splitLevel("@30"))
        assertEquals("shiny" to 0, EventTargets.splitLevel("shiny"))
        assertEquals("any" to 0, EventTargets.splitLevel(null))
        assertEquals("pikachu" to 0, EventTargets.splitLevel("pikachu@abc"))
    }

    @Test
    fun `subir de nivel respeita o filtro e o nivel minimo`() {
        val pikachu = info("pikachu")
        assertTrue(EventTargets.matchesLevelUp("pikachu@50", pikachu, 50))
        assertTrue(EventTargets.matchesLevelUp("pikachu@50", pikachu, 51))
        assertFalse(EventTargets.matchesLevelUp("pikachu@50", pikachu, 49))
        assertFalse(EventTargets.matchesLevelUp("eevee@10", pikachu, 99))
        assertTrue(EventTargets.matchesLevelUp("any", pikachu, 2))
        assertTrue(EventTargets.matchesLevelUp(null, pikachu, 2))
        assertTrue(EventTargets.matchesLevelUp("type:electric@5", info("pikachu", types = arrayOf("electric")), 5))
        assertFalse(EventTargets.matchesLevelUp("type:fire@5", info("pikachu", types = arrayOf("electric")), 5))
    }
}
