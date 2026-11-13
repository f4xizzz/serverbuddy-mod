package com.f4xizzz.serverbuddy.modules.commandblock

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandBlockLogicTest {
    private fun names(vararg n: String) = n.toSet()

    @Test
    fun `o texto digitado vira palavras minusculas sem a barra`() {
        assertEquals(listOf("back"), CommandMatcher.words("/Back"))
        assertEquals(listOf("home", "set", "casa"), CommandMatcher.words("  home   SET casa "))
        assertEquals(emptyList<String>(), CommandMatcher.words("   "))
    }

    @Test
    fun `um nome simples casa com o comando e com o apelido`() {
        // /return é um apelido (redirect) de /back: quem monta os nomes põe os dois
        assertTrue(CommandMatcher.matches("back", CommandMatcher.words("return"), names("return", "back")))
        assertTrue(CommandMatcher.matches("back", CommandMatcher.words("back"), names("back")))
        assertFalse(CommandMatcher.matches("back", CommandMatcher.words("home"), names("home")))
    }

    @Test
    fun `o nome trocado em modules conf tambem vale`() {
        val rename: (String) -> String = { if (it == "back") "voltar" else it }
        assertTrue(CommandMatcher.matches("back", CommandMatcher.words("voltar"), names("voltar"), rename))
        assertTrue(CommandMatcher.matches("back", CommandMatcher.words("back"), names("back"), rename))
    }

    @Test
    fun `varias palavras valem como o comeco do que foi digitado`() {
        assertTrue(CommandMatcher.matches("home set", CommandMatcher.words("home set casa"), names("home")))
        assertTrue(CommandMatcher.matches("home set", CommandMatcher.words("HOME SET"), names("home")))
        assertFalse(CommandMatcher.matches("home set", CommandMatcher.words("home casa"), names("home")))
        assertFalse(CommandMatcher.matches("home set", CommandMatcher.words("home"), names("home")))
    }

    @Test
    fun `o prefixo do mod so casa quando a entrada tambem tem prefixo`() {
        val typed = CommandMatcher.words("minecraft:tp 0 0 0")
        val firstNames = names("minecraft:tp", "tp")
        assertTrue(CommandMatcher.matches("tp", typed, firstNames))
        assertTrue(CommandMatcher.matches("minecraft:tp", typed, firstNames))
        assertFalse(CommandMatcher.matches("minecraft:tp", CommandMatcher.words("tp 0 0 0"), names("tp")))
    }

    @Test
    fun `asterisco casa com todo comando, mas so sozinho`() {
        assertTrue(CommandMatcher.matches("*", CommandMatcher.words("qualquer coisa"), names("qualquer")))
        assertFalse(CommandMatcher.matches("* x", CommandMatcher.words("qualquer x"), names("qualquer")))
    }

    @Test
    fun `entrada vazia ou comando vazio nunca casa`() {
        assertFalse(CommandMatcher.matches("", CommandMatcher.words("back"), names("back")))
        assertFalse(CommandMatcher.matches("back", emptyList(), names("back")))
    }

    @Test
    fun `json editado a mao com nulos e arrumado`() {
        val json = """{ "rules": { "x": { "commands": null, "when_any": ["tag:a", null], "except": null, "message": null }, "y": null, " ": {} },
            |  "messages": { "blocked": null } }""".trimMargin()
        val raw = SbGson.pretty.fromJson(json, CommandBlockConfig::class.java)
        val config = CommandBlockService.sanitize(raw)
        assertEquals(listOf("x"), config.rules.keys.toList())
        val x = config.rules.getValue("x")
        assertTrue(x.commands.isEmpty())
        assertEquals(listOf("tag:a"), x.whenAny)
        assertTrue(x.except.isEmpty())
        assertEquals(CommandBlockMessages().blocked, config.messages.blocked)
    }

    @Test
    fun `a config de exemplo nasce comentada e com as regras de exemplo`() {
        val text = ConfigDocs.render(CommandBlockConfig(), SbGson.defaultsWriter)
        assertTrue(text.contains("\"no_back\""))
        assertTrue(text.contains("\"when_any\""))
        assertTrue(text.lines().any { it.trim().startsWith("//") && it.contains("HOW IT WORKS") })
        // volta a ser lida pelo Gson do mesmo jeito
        val again = SbGson.pretty.fromJson(text, CommandBlockConfig::class.java)
        assertEquals(CommandBlockConfig().rules.keys, again.rules.keys)
        assertEquals(listOf("back"), again.rules.getValue("no_back").commands)
    }
}
