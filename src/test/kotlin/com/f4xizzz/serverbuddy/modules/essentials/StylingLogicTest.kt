package com.f4xizzz.serverbuddy.modules.essentials

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StylingLogicTest {
    // ---------------------------------------------------------------- &c, §c e hexadecimal

    @Test
    fun `os codigos & viram tags do MiniMessage`() {
        assertEquals("<red>Olá <bold>mundo", StylingLogic.legacyToMiniMessage("&cOlá &lmundo"))
        assertEquals("<gold>a<reset>b", StylingLogic.legacyToMiniMessage("&6a&rb"))
        assertEquals("<dark_gray>x", StylingLogic.legacyToMiniMessage("&8x"))
    }

    @Test
    fun `o simbolo de paragrafo e as letras maiusculas tambem valem`() {
        assertEquals("<red>x<green>y", StylingLogic.legacyToMiniMessage("§cx§Ay"))
    }

    @Test
    fun `a cor hexadecimal vira uma tag de cor`() {
        assertEquals("<#ff8800>laranja", StylingLogic.legacyToMiniMessage("&#ff8800laranja"))
        assertEquals("<#FFAA00>x", StylingLogic.legacyToMiniMessage("&#FFAA00x"))
    }

    @Test
    fun `um & que nao e codigo fica como esta`() {
        assertEquals("Tom & Jerry", StylingLogic.legacyToMiniMessage("Tom & Jerry"))
        assertEquals("a&", StylingLogic.legacyToMiniMessage("a&"))
        assertEquals("&zx", StylingLogic.legacyToMiniMessage("&zx"))
        assertEquals("&#12345", StylingLogic.legacyToMiniMessage("&#12345"))
        assertEquals("&#gggggg", StylingLogic.legacyToMiniMessage("&#gggggg"))
    }

    // ---------------------------------------------------------------- trocas

    @Test
    fun `as trocas valem em todo o texto e ignoram chave vazia`() {
        val map = linkedMapOf(":shrug:" to "(shrug)", ":coracao:" to "❤", "" to "x")
        assertEquals("oi (shrug) e ❤ e ❤", StylingLogic.applyReplacements("oi :shrug: e :coracao: e :coracao:", map))
        assertEquals("sem troca", StylingLogic.applyReplacements("sem troca", map))
    }

    // ---------------------------------------------------------------- formato por tipo de jogador

    @Test
    fun `vale o primeiro formato que casa, na ordem do arquivo`() {
        val formats = linkedMapOf("default" to "D", "admin" to "A", "vip" to "V")
        assertEquals("A", StylingLogic.pickFormat(formats) { it == "admin" || it == "vip" })
        assertEquals("V", StylingLogic.pickFormat(formats) { it == "vip" })
    }

    @Test
    fun `sem nenhum que case vale o default, onde quer que ele esteja`() {
        val formats = linkedMapOf("admin" to "A", "default" to "D")
        assertEquals("D", StylingLogic.pickFormat(formats) { false })
        assertEquals("D", StylingLogic.pickFormat(linkedMapOf("DEFAULT" to "D")) { false })
    }

    @Test
    fun `o default nunca e perguntado e sem default pode nao haver formato`() {
        val asked = ArrayList<String>()
        StylingLogic.pickFormat(linkedMapOf("default" to "D", "vip" to "V")) { asked += it; false }
        assertEquals(listOf("vip"), asked)
        assertNull(StylingLogic.pickFormat(linkedMapOf("vip" to "V")) { false })
    }

    // ---------------------------------------------------------------- menções

    @Test
    fun `o nome citado como palavra inteira conta, sem diferenca de maiusculas`() {
        assertTrue(StylingLogic.mentions("oi Steve, tudo bem?", "steve"))
        assertTrue(StylingLogic.mentions("STEVE!", "Steve"))
        assertTrue(StylingLogic.mentions("@Steve vem aqui", "Steve"))
        assertTrue(StylingLogic.mentions("steve", "Steve"))
    }

    @Test
    fun `um pedaco de outra palavra nao conta`() {
        assertFalse(StylingLogic.mentions("alguém viu?", "Al"))
        assertFalse(StylingLogic.mentions("Steve2 chegou", "Steve"))
        assertFalse(StylingLogic.mentions("o_Steve", "Steve"))
        assertFalse(StylingLogic.mentions("nada a ver", ""))
    }

    @Test
    fun `acha a citacao mesmo depois de um pedaco que nao serve`() {
        assertTrue(StylingLogic.mentions("alguém viu o al?", "Al"))
    }

    // ---------------------------------------------------------------- PlaceholderAPI

    @Test
    fun `acha os tokens do PlaceholderAPI e ignora as variaveis simples`() {
        val text = "%tag%<white>%player%</white> %luckperms:prefix% e %serverbuddy:nick% e de novo %luckperms:prefix%"
        assertEquals(listOf("%luckperms:prefix%", "%serverbuddy:nick%"), StylingLogic.placeholderTokens(text))
    }

    @Test
    fun `um texto com porcentagem solta nao confunde`() {
        assertEquals(emptyList<String>(), StylingLogic.placeholderTokens("50% de desconto e 100% garantido"))
        assertEquals(listOf("%player:name%"), StylingLogic.placeholderTokens("100% %player:name%"))
    }

    // ---------------------------------------------------------------- números dos ajustes

    @Test
    fun `o numero fica dentro dos limites e aceita virgula`() {
        assertEquals(1.5, StylingLogic.clampedNumber("1,5", 0.0, 2.0))
        assertEquals(2.0, StylingLogic.clampedNumber("9", 0.0, 2.0))
        assertEquals(0.5, StylingLogic.clampedNumber("0.1", 0.5, 2.0))
        assertNull(StylingLogic.clampedNumber("abc", 0.0, 2.0))
        assertNull(StylingLogic.clampedNumber("NaN", 0.0, 2.0))
    }
}
