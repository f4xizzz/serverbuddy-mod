package com.f4xizzz.serverbuddy.core.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigDocsTest {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    private class Inner(
        @Doc("Texto interno") val value: Int = 3,
        val list: List<String> = listOf("a", "b")
    )

    @Doc("Cabeçalho do arquivo\nsegunda linha")
    private class Root(
        @Doc("Liga o módulo.") val enabled: Boolean = true,
        @Doc("Mensagens (a chave é livre).") val messages: Map<String, String> = linkedMapOf("hello" to "Olá <red>mundo"),
        val inner: Inner = Inner(),
        @SerializedName("snake_case") @Doc("Nome serializado\ncom duas linhas") val snakeCase: String = "x"
    )

    @Test
    fun `o arquivo gerado tem os comentarios e continua sendo lido pelo Gson`() {
        val text = ConfigDocs.render(Root(), gson)
        assertTrue(text.startsWith("// Cabeçalho do arquivo\n// segunda linha\n"), text)
        assertTrue(text.contains("  // Liga o módulo.\n  \"enabled\": true"), text)
        assertTrue(text.contains("  // Nome serializado\n  // com duas linhas\n  \"snake_case\": \"x\""), text)
        assertTrue(text.contains("    // Texto interno\n    \"value\": 3"), text)
        // não escapa o < do MiniMessage
        assertTrue(text.contains("\"hello\": \"Olá <red>mundo\""), text)

        val back = gson.fromJson(text, Root::class.java)
        assertEquals(true, back.enabled)
        assertEquals("x", back.snakeCase)
        assertEquals(3, back.inner.value)
        assertEquals(listOf("a", "b"), back.inner.list)
        assertEquals("Olá <red>mundo", back.messages["hello"])
    }

    private class Entry(@Doc("Nome da entrada") val name: String = "a", @Doc("Peso da entrada") val weight: Int = 1)

    private class Bag(
        val byName: Map<String, Entry> = linkedMapOf("um" to Entry("x"), "dois" to Entry("y"), "tres" to Entry("z")),
        val list: List<Entry> = listOf(Entry("p"), Entry("q"))
    )

    @Test
    fun `mapas e listas de objetos iguais so comentam o primeiro`() {
        val text = ConfigDocs.render(Bag(), gson)
        assertEquals(1, Regex("// Nome da entrada").findAll(text.substringBefore("\"list\"")).count(), text)
        assertEquals(1, Regex("// Peso da entrada").findAll(text.substringBefore("\"list\"")).count(), text)
        assertEquals(1, Regex("// Nome da entrada").findAll(text.substringAfter("\"list\"")).count(), text)
        // continua sendo um JSON que o Gson lê, com todas as entradas
        val back = gson.fromJson(text, Bag::class.java)
        assertEquals(listOf("um", "dois", "tres"), back.byName.keys.toList())
        assertEquals("z", back.byName.getValue("tres").name)
        assertEquals(listOf("p", "q"), back.list.map { it.name })
    }

    @Test
    fun `completa so as opcoes que faltam e nao mexe nos mapas do usuario`() {
        val defaults = gson.toJsonTree(Root()).asJsonObject
        val user = JsonParser.parseString(
            """
            // arquivo antigo, sem snake_case e sem inner.value
            { "enabled": false, "messages": { "outra": "x" }, "inner": { "list": ["z"] } }
            """.trimIndent()
        ).asJsonObject

        val added = ConfigDocs.mergeMissing(user, defaults, Root::class.java)

        assertEquals(listOf("inner.value", "snake_case"), added.sorted())
        assertEquals(false, user.get("enabled").asBoolean)
        assertEquals(setOf("outra"), user.getAsJsonObject("messages").keySet())
        assertEquals(3, user.getAsJsonObject("inner").get("value").asInt)
        assertEquals("z", user.getAsJsonObject("inner").getAsJsonArray("list")[0].asString)
    }
}
