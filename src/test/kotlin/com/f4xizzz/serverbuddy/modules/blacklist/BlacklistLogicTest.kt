package com.f4xizzz.serverbuddy.modules.blacklist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BlacklistLogicTest {
    // sábado, 2026-12-05, 21:30
    private val saturday = LocalDateTime.of(2026, 12, 5, 21, 30)

    private fun situation(
        tags: Set<String> = emptySet(),
        dimension: String = "minecraft:overworld",
        battle: String? = "pvp",
        permissions: Set<String> = emptySet(),
        now: LocalDateTime = saturday
    ) = BlacklistService.Situation(tags, dimension, battle, { it in permissions }, now)

    private val config = BlacklistConfig(
        linkedMapOf(
            "default" to BlacklistContext(moves = listOf("Sheer Cold")),
            "pvp" to BlacklistContext(conditions = listOf("battle:pvp"), moves = listOf("fissure", "hiddenpower")),
            "mode_tower" to BlacklistContext(moves = listOf("explosion")),
            "ranked" to BlacklistContext(conditions = listOf("permission:server.ranked", "battle:pvp"), moves = listOf("recover")),
            "fim-de-semana" to BlacklistContext(whenAny = listOf("day:sat", "day:sun"), moves = listOf("toxic")),
            "noite" to BlacklistContext(conditions = listOf("time:20:00-23:00", "!dimension:minecraft:the_nether"), moves = listOf("spore"))
        )
    )

    @Test
    fun `normaliza o nome do golpe como no Showdown`() {
        assertEquals("sheercold", BlacklistService.normalize("Sheer Cold"))
        assertEquals("willowisp", BlacklistService.normalize("will-o-wisp"))
        assertEquals("thunderbolt", BlacklistService.normalize("  THUNDERBOLT "))
        assertEquals("", BlacklistService.normalize(" - "))
    }

    @Test
    fun `default vale sempre e o resto so com condicao`() {
        val names = BlacklistService.contextsFor(config, situation(battle = "wild", now = LocalDateTime.of(2026, 12, 7, 10, 0)))
        assertEquals(listOf("default"), names)
    }

    @Test
    fun `sem condicao o contexto vale com a tag de mesmo nome`() {
        assertTrue("mode_tower" in BlacklistService.contextsFor(config, situation(tags = setOf("mode_tower"))))
        assertFalse("mode_tower" in BlacklistService.contextsFor(config, situation(tags = setOf("outra"))))
    }

    @Test
    fun `when exige todas e when_any exige uma`() {
        // ranked: permissão E pvp
        assertTrue("ranked" in BlacklistService.contextsFor(config, situation(permissions = setOf("server.ranked"))))
        assertFalse("ranked" in BlacklistService.contextsFor(config, situation()))
        assertFalse("ranked" in BlacklistService.contextsFor(config, situation(permissions = setOf("server.ranked"), battle = "npc")))
        // fim-de-semana: sábado OU domingo
        assertTrue("fim-de-semana" in BlacklistService.contextsFor(config, situation()))
        assertFalse("fim-de-semana" in BlacklistService.contextsFor(config, situation(now = LocalDateTime.of(2026, 12, 4, 21, 30))))
    }

    @Test
    fun `negacao e horario`() {
        assertTrue("noite" in BlacklistService.contextsFor(config, situation()))
        assertFalse("noite" in BlacklistService.contextsFor(config, situation(dimension = "minecraft:the_nether")))
        assertFalse("noite" in BlacklistService.contextsFor(config, situation(now = LocalDateTime.of(2026, 12, 5, 12, 0))))
    }

    @Test
    fun `condicao desconhecida conta como falsa`() {
        val odd = BlacklistConfig(linkedMapOf("default" to BlacklistContext(), "x" to BlacklistContext(conditions = listOf("lua-cheia"), moves = listOf("a"))))
        assertEquals(listOf("default"), BlacklistService.contextsFor(odd, situation()))
    }

    @Test
    fun `golpe proibido so nos contextos que valem`() {
        val pvp = BlacklistService.check(config, situation(), "Fissure")
        assertNotNull(pvp)
        assertEquals(listOf("pvp"), pvp!!.contexts)
        assertEquals("fissure", pvp.move)
        assertNull(BlacklistService.check(config, situation(battle = "wild"), "fissure"))
        // o default vale em qualquer batalha
        assertEquals(listOf("default"), BlacklistService.check(config, situation(battle = "wild"), "sheercold")!!.contexts)
        assertNull(BlacklistService.check(config, situation(), "thunderbolt"))
        assertNull(BlacklistService.check(config, situation(), ""))
    }

    @Test
    fun `banir hiddenpower bane as variacoes`() {
        assertNotNull(BlacklistService.check(config, situation(), "hiddenpower"))
        assertNotNull(BlacklistService.check(config, situation(), "hiddenpowerfire"))
        assertNull(BlacklistService.check(config, situation(), "hidden"))
    }

    @Test
    fun `listar considera qualquer tipo de batalha`() {
        val any = situation(battle = BlacklistService.ANY_BATTLE)
        assertTrue("pvp" in BlacklistService.contextsFor(config, any))
        assertEquals(listOf("sheercold", "fissure", "hiddenpower", "toxic", "spore"), BlacklistService.bannedMoves(config, any))
    }

    @Test
    fun `config com campos nulos e sem default e arrumada`() {
        val raw = BlacklistConfig(linkedMapOf("pvp" to BlacklistContext(conditions = listOf("battle:pvp"), moves = listOf("fissure"))))
        val fixed = BlacklistService.sanitize(raw)
        assertTrue(fixed.contexts.keys.any { it == "default" })
        assertEquals(listOf("fissure"), fixed.contexts.getValue("pvp").moves)
    }

    @Test
    fun `le o blacklist json do mod original`() {
        val parsed = BlacklistImport.parse("""{"contexts": {"default": ["Thunder Bolt", "fissure"], "Mode_Tower": ["explosion", "explosion"]}}""")!!
        assertEquals(listOf("thunderbolt", "fissure"), parsed["default"])
        assertEquals(listOf("explosion"), parsed["mode_tower"])
        // formato antigo: só uma lista = o default
        assertEquals(mapOf("default" to listOf("sheercold")), BlacklistImport.parse("""["sheer cold"]"""))
        assertNull(BlacklistImport.parse("isto não é json {"))
        assertNull(BlacklistImport.parse("""{"outra": 1}"""))
    }
}
