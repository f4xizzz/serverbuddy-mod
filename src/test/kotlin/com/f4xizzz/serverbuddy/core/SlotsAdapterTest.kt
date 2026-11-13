package com.f4xizzz.serverbuddy.core

import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Os slots dos menus novos aceitam número, intervalo ("0-44") e lista misturada. */
class SlotsAdapterTest {
    private class Holder(@JsonAdapter(SlotsAdapter::class) val slots: List<Int> = emptyList())

    private fun parse(json: String): List<Int> = Gson().fromJson("{\"slots\": $json}", Holder::class.java).slots

    @Test
    fun `numero solto`() {
        assertEquals(listOf(5), parse("5"))
    }

    @Test
    fun `intervalo em texto`() {
        assertEquals(listOf(0, 1, 2, 3), parse("\"0-3\""))
    }

    @Test
    fun `lista mistura numeros e intervalos`() {
        assertEquals(listOf(1, 2, 10, 11, 12), parse("[1, 2, \"10-12\"]"))
    }

    @Test
    fun `texto invalido e ignorado sem derrubar o resto`() {
        assertEquals(listOf(4), parse("[\"abc\", \"9-2\", 4]"))
    }

    @Test
    fun `escrita em lista de numeros`() {
        assertEquals("{\"slots\":[1,2,3]}", Gson().toJson(Holder(listOf(1, 2, 3))))
    }

    @Test
    fun `sequencias longas viram intervalo e voltam iguais`() {
        val slots = (9..44).toList() + listOf(50, 52, 53)
        val json = Gson().toJson(Holder(slots))
        assertEquals("{\"slots\":[\"9-44\",50,52,53]}", json)
        assertEquals(slots, Gson().fromJson(json, Holder::class.java).slots)
        assertEquals("{\"slots\":[\"0-3\",10]}", Gson().toJson(Holder(listOf(0, 1, 2, 3, 10))))
    }
}
