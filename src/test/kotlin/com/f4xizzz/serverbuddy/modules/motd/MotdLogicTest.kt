package com.f4xizzz.serverbuddy.modules.motd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MotdLogicTest {
    // sábado, 2026-12-05, 21:30
    private fun ctx(online: Int = 5, max: Int = 20, whitelist: Boolean = false, maintenance: Boolean = false, at: LocalDateTime = LocalDateTime.of(2026, 12, 5, 21, 30)) =
        MotdContext(online, max, whitelist, maintenance, at)

    @Test
    fun `condicoes simples e negacao`() {
        assertTrue(MotdConditions.matches("whitelist", ctx(whitelist = true)))
        assertFalse(MotdConditions.matches("whitelist", ctx()))
        assertTrue(MotdConditions.matches("!whitelist", ctx()))
        assertTrue(MotdConditions.matches("maintenance", ctx(maintenance = true)))
        assertTrue(MotdConditions.matches("full", ctx(online = 20)))
        assertFalse(MotdConditions.matches("full", ctx(online = 19)))
        assertFalse(MotdConditions.matches("full", ctx(online = 0, max = 0)))
    }

    @Test
    fun `comparacao de jogadores`() {
        assertTrue(MotdConditions.matches("players>=5", ctx(online = 5)))
        assertFalse(MotdConditions.matches("players>5", ctx(online = 5)))
        assertTrue(MotdConditions.matches("players<=5", ctx(online = 5)))
        assertTrue(MotdConditions.matches("players == 5", ctx(online = 5)))
        assertTrue(MotdConditions.matches("players<6", ctx(online = 5)))
    }

    @Test
    fun `horario, dia e data`() {
        assertTrue(MotdConditions.matches("time:18:00-23:30", ctx()))
        assertFalse(MotdConditions.matches("time:08:00-12:00", ctx()))
        // faixa que vira a noite
        assertTrue(MotdConditions.matches("time:22:00-06:00", ctx(at = LocalDateTime.of(2026, 12, 5, 2, 0))))
        assertFalse(MotdConditions.matches("time:22:00-06:00", ctx(at = LocalDateTime.of(2026, 12, 5, 12, 0))))
        assertTrue(MotdConditions.matches("day:sat,sun", ctx()))
        assertTrue(MotdConditions.matches("day:sab", ctx()))
        assertFalse(MotdConditions.matches("day:mon,tue", ctx()))
        assertTrue(MotdConditions.matches("date:2026-12-01..2026-12-10", ctx()))
        assertFalse(MotdConditions.matches("date:2026-12-06..2026-12-10", ctx()))
        assertTrue(MotdConditions.matches("date:2026-12-05", ctx()))
    }

    @Test
    fun `condicao desconhecida conta como falsa e nao derruba`() {
        assertFalse(MotdConditions.matches("banana:42", ctx()))
        assertFalse(MotdConditions.matches("time:abc", ctx()))
    }

    @Test
    fun `when e when_any`() {
        val c = ctx(whitelist = true)
        assertTrue(MotdConditions.profileMatches(listOf("whitelist"), emptyList(), c))
        assertTrue(MotdConditions.profileMatches(emptyList(), emptyList(), c))
        assertTrue(MotdConditions.profileMatches(listOf("whitelist"), listOf("maintenance", "players>=5"), c))
        assertFalse(MotdConditions.profileMatches(listOf("whitelist"), listOf("maintenance", "full"), c))
    }

    @Test
    fun `condicao registrada por outro modulo`() {
        MotdConditions.register("teste") { arg, _ -> arg == "sim" }
        assertTrue(MotdConditions.matches("teste:sim", ctx()))
        assertFalse(MotdConditions.matches("teste:nao", ctx()))
        assertTrue(MotdConditions.matches("!teste:nao", ctx()))
    }

    @Test
    fun `jogadores falsos`() {
        assertEquals(8, PlayerCountModifier.parse("+3")!!(5))
        assertEquals(8, PlayerCountModifier.parse("3")!!(5))
        assertEquals(3, PlayerCountModifier.parse("-2")!!(5))
        assertEquals(50, PlayerCountModifier.parse("=50")!!(5))
        assertEquals(10, PlayerCountModifier.parse("10+")!!(5))
        assertEquals(12, PlayerCountModifier.parse("10+")!!(12))
        assertEquals(7, PlayerCountModifier.parse("40%")!!(5)) // ceil(1.4 * 5)
        repeat(50) { assertTrue(PlayerCountModifier.parse("3:6")!!(5) in 8..11) }
        assertNull(PlayerCountModifier.parse("banana"))
        assertNull(PlayerCountModifier.parse(""))
    }
}
