package com.f4xizzz.serverbuddy.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ConditionsTest {
    // segunda-feira, 10:00
    private val monday = LocalDateTime.of(2026, 11, 9, 10, 0)

    private fun subject(
        tags: Set<String> = emptySet(),
        permissions: Set<String> = emptySet(),
        equipped: String? = null,
        dimension: String = "minecraft:overworld",
        now: LocalDateTime = monday
    ) = Conditions.Subject({ it in tags }, { it in permissions }, equipped, dimension, now)

    @Test
    fun `tag e permissao olham o jogador`() {
        val s = subject(tags = setOf("sem_back"), permissions = setOf("a.b"))
        assertEquals(true, Conditions.evaluate("tag:sem_back", s))
        assertEquals(false, Conditions.evaluate("tag:outra", s))
        assertEquals(true, Conditions.evaluate("permission:a.b", s))
        assertEquals(false, Conditions.evaluate("permission:a.c", s))
    }

    @Test
    fun `tag equipada compara sem diferenciar maiusculas`() {
        val s = subject(equipped = "VIP")
        assertEquals(true, Conditions.evaluate("equipped:vip", s))
        assertEquals(true, Conditions.evaluate("tag_equipped:Vip", s))
        assertEquals(false, Conditions.evaluate("equipped:outra", s))
        assertEquals(false, Conditions.evaluate("equipped:vip", subject()))
    }

    @Test
    fun `dimensao e horario`() {
        val s = subject(dimension = "minecraft:the_nether")
        assertEquals(true, Conditions.evaluate("dimension:minecraft:the_nether", s))
        assertEquals(false, Conditions.evaluate("dimension:minecraft:overworld", s))
        assertEquals(true, Conditions.evaluate("time:09:00-11:00", s))
        assertEquals(false, Conditions.evaluate("time:18:00-23:30", s))
        assertEquals(true, Conditions.evaluate("day:mon,tue", s))
        assertEquals(false, Conditions.evaluate("day:sat,sun", s))
        assertEquals(true, Conditions.evaluate("date:2026-11-01..2026-11-30", s))
    }

    @Test
    fun `o ponto de exclamacao nega`() {
        val s = subject(tags = setOf("vip"))
        assertEquals(false, Conditions.evaluate("!tag:vip", s))
        assertEquals(true, Conditions.evaluate("!tag:outra", s))
        assertEquals(true, Conditions.evaluate("!!tag:vip", s))
        assertEquals(true, Conditions.evaluate("  ! tag:outra", s))
    }

    @Test
    fun `condicao inexistente ou mal escrita e nula e nunca vale nem negada`() {
        val s = subject()
        assertNull(Conditions.evaluate("nada:1", s))
        assertNull(Conditions.evaluate("!nada:1", s))
        assertNull(Conditions.evaluate("time:abc", s))
        val unknown = ArrayList<String>()
        assertFalse(Conditions.matches("!nada:1", s) { unknown.add(it) })
        assertEquals(listOf("!nada:1"), unknown)
    }

    @Test
    fun `argumento vazio nunca vale`() {
        val s = subject(tags = setOf(""), permissions = setOf(""))
        assertEquals(false, Conditions.evaluate("tag:", s))
        assertEquals(false, Conditions.evaluate("permission:", s))
    }

    @Test
    fun `todas as de when e uma das de when_any`() {
        val s = subject(tags = setOf("a", "c"))
        assertTrue(Conditions.test(listOf("tag:a", "tag:c"), emptyList(), s))
        assertFalse(Conditions.test(listOf("tag:a", "tag:b"), emptyList(), s))
        assertTrue(Conditions.test(emptyList(), listOf("tag:x", "tag:c"), s))
        assertFalse(Conditions.test(emptyList(), listOf("tag:x", "tag:y"), s))
        assertTrue(Conditions.test(listOf("tag:a"), listOf("tag:x", "tag:c"), s))
        assertFalse(Conditions.test(listOf("tag:a"), listOf("tag:x"), s))
    }

    @Test
    fun `sem nenhuma condicao a regra nunca vale`() {
        assertFalse(Conditions.test(emptyList(), emptyList(), subject(tags = setOf("a"))))
    }
}
