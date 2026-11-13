package com.f4xizzz.serverbuddy.core

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Enums nas configs aceitam qualquer combinação de maiúsculas e respeitam @SerializedName. */
class LenientEnumTest {
    private enum class Mode { NONE, DAILY, @SerializedName("semana") WEEKLY }

    private class Holder(val mode: Mode? = Mode.NONE, val modes: List<Mode> = emptyList())

    private val gson = GsonBuilder().registerTypeAdapterFactory(LenientEnumFactory).create()

    @Test
    fun `le em minusculo, maiusculo e misturado`() {
        assertEquals(Mode.DAILY, gson.fromJson("""{ "mode": "daily" }""", Holder::class.java).mode)
        assertEquals(Mode.DAILY, gson.fromJson("""{ "mode": "DAILY" }""", Holder::class.java).mode)
        assertEquals(Mode.DAILY, gson.fromJson("""{ "mode": " Daily " }""", Holder::class.java).mode)
        assertEquals(listOf(Mode.NONE, Mode.DAILY), gson.fromJson("""{ "modes": ["none", "Daily"] }""", Holder::class.java).modes)
    }

    @Test
    fun `respeita o nome serializado e escreve igual ao padrao`() {
        assertEquals(Mode.WEEKLY, gson.fromJson("""{ "mode": "semana" }""", Holder::class.java).mode)
        assertEquals(Mode.WEEKLY, gson.fromJson("""{ "mode": "weekly" }""", Holder::class.java).mode)
        assertEquals("""{"mode":"semana","modes":[]}""", gson.toJson(Holder(Mode.WEEKLY)))
        assertEquals("""{"mode":"DAILY","modes":[]}""", gson.toJson(Holder(Mode.DAILY)))
    }

    @Test
    fun `valor que nao existe vira null`() {
        assertNull(gson.fromJson("""{ "mode": "mensal" }""", Holder::class.java).mode)
        assertNull(gson.fromJson("""{ "mode": null }""", Holder::class.java).mode)
    }
}
