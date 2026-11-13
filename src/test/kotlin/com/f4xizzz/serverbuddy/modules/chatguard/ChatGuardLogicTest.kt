package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatGuardLogicTest {
    private val dup = DuplicateSettings()
    private val warn = WarningSettings()
    private val ban = ChatBanSettings()
    private val minute = 60_000L

    private fun history(vararg texts: Pair<String, Long>) = texts.map { RecentMessage(ChatGuardLogic.normalize(it.first), it.second) }

    // ---------------------------------------------------------------- texto

    @Test
    fun `a limpeza ignora maiusculas, acentos, pontuacao, espacos e letras repetidas`() {
        assertEquals("oipessoal", ChatGuardLogic.normalize("Oi, pessoal!!!"))
        assertEquals(ChatGuardLogic.normalize("oi pessoal"), ChatGuardLogic.normalize("  OI    PESSOAL "))
        assertEquals("olavoce", ChatGuardLogic.normalize("Olá, você?"))
        assertEquals("ooi", ChatGuardLogic.normalize("ooooooi"))
        assertEquals("hello", ChatGuardLogic.normalize("hellllllo"))
        assertEquals("", ChatGuardLogic.normalize("!!! ??? ..."))
    }

    @Test
    fun `semelhanca vai de zero a um`() {
        assertEquals(1.0, ChatGuardLogic.similarity("abc", "abc"), 0.0001)
        assertEquals(0.0, ChatGuardLogic.similarity("abcd", "wxyz"), 0.0001)
        assertEquals(0.0, ChatGuardLogic.similarity("a", "b"), 0.0001)
        assertEquals(1.0, ChatGuardLogic.similarity(ChatGuardLogic.normalize("vendo todos os pokemons"), ChatGuardLogic.normalize("Vendo todos os pokemons!!!")), 0.0001)
        val close = ChatGuardLogic.similarity(ChatGuardLogic.normalize("comprando pokemon shiny barato"), ChatGuardLogic.normalize("comprando pokemon shiny baratos"))
        assertTrue(close > 0.9, "variação pequena deveria parecer: $close")
        val far = ChatGuardLogic.similarity(ChatGuardLogic.normalize("bom dia a todos"), ChatGuardLogic.normalize("quem quer trocar pokemon"))
        assertTrue(far < 0.5, "textos diferentes não podem parecer: $far")
    }

    // ---------------------------------------------------------------- repetição

    @Test
    fun `a mesma mensagem dentro da janela e repetida`() {
        val h = history("Olá pessoal do servidor" to 0L)
        assertTrue(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("olá, pessoal do servidor!!!"), 10_000, dup))
        assertFalse(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("outra coisa bem diferente"), 10_000, dup))
    }

    @Test
    fun `depois da janela a mensagem pode voltar`() {
        val h = history("Olá pessoal do servidor" to 0L)
        assertFalse(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("olá pessoal do servidor"), 61_000, dup))
    }

    @Test
    fun `quem insiste mantem a janela aberta (a tentativa entra no historico)`() {
        // o 1º aos 0s, tentativas aos 40s e 80s: a de 80s ainda vê a de 40s dentro dos 60s
        val h = history("vendo pokemon barato" to 0L, "vendo pokemon barato" to 40_000L)
        assertTrue(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("vendo pokemon barato"), 80_000, dup))
    }

    @Test
    fun `mensagem curta nunca e repetida`() {
        val h = history("oi" to 0L, "gg" to 0L)
        assertFalse(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("oi!"), 1_000, dup))
        assertFalse(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("GG"), 1_000, dup))
    }

    @Test
    fun `max_repeats deixa repetir algumas vezes`() {
        val relaxed = DuplicateSettings(maxRepeats = 3)
        val one = history("mensagem de teste aqui" to 0L)
        val three = history("mensagem de teste aqui" to 0L, "mensagem de teste aqui" to 1_000L, "mensagem de teste aqui" to 2_000L)
        val n = ChatGuardLogic.normalize("mensagem de teste aqui")
        assertFalse(ChatGuardLogic.isDuplicate(one, n, 3_000, relaxed))
        assertTrue(ChatGuardLogic.isDuplicate(three, n, 3_000, relaxed))
    }

    @Test
    fun `a semelhanca configurada decide se variacoes contam`() {
        val h = history("comprando pokemon shiny barato" to 0L)
        val variation = ChatGuardLogic.normalize("comprando pokemon shiny baratos agora")
        assertTrue(ChatGuardLogic.isDuplicate(h, variation, 1_000, DuplicateSettings(similarity = 0.8)))
        assertFalse(ChatGuardLogic.isDuplicate(h, variation, 1_000, DuplicateSettings(similarity = 1.0)))
    }

    @Test
    fun `desligado nunca barra`() {
        val h = history("mensagem igual aqui" to 0L)
        assertFalse(ChatGuardLogic.isDuplicate(h, ChatGuardLogic.normalize("mensagem igual aqui"), 1_000, DuplicateSettings(enabled = false)))
    }

    @Test
    fun `flood conta mensagens no intervalo`() {
        val flood = FloodSettings(enabled = true, messages = 3, seconds = 5)
        assertFalse(ChatGuardLogic.isFlooding(listOf(0L, 1_000L), 2_000, flood))
        assertTrue(ChatGuardLogic.isFlooding(listOf(0L, 1_000L, 2_000L), 3_000, flood))
        assertFalse(ChatGuardLogic.isFlooding(listOf(0L, 1_000L, 2_000L), 9_000, flood)) // já saíram da janela
        assertFalse(ChatGuardLogic.isFlooding(listOf(0L, 1L, 2L, 3L), 4, FloodSettings(enabled = false)))
    }

    // ---------------------------------------------------------------- avisos e ban

    @Test
    fun `tres avisos e o quarto erro vira ban`() {
        val state = GuardState()
        val outcomes = (1..4).map { ChatGuardLogic.violation(state, it * 1_000L, warn, ban) }
        for (i in 0..2) {
            val o = outcomes[i] as Outcome.Warned
            assertEquals(i + 1, o.count)
            assertEquals(3, o.max)
        }
        val banned = outcomes[3] as Outcome.Banned
        assertEquals(1, banned.bans)
        assertEquals(3, banned.totalWarnings)
        assertTrue(state.bannedAt(4_000))
        assertEquals(0, state.warnings) // os avisos de agora zeram no ban
    }

    @Test
    fun `max_warnings zero bane no primeiro erro`() {
        val state = GuardState()
        assertTrue(ChatGuardLogic.violation(state, 0, WarningSettings(maxWarnings = 0), ban) is Outcome.Banned)
    }

    @Test
    fun `o tempo do ban cresce com os bans e com os avisos acumulados`() {
        // padrão: 5m, 30m, 2h... + 1m por aviso acumulado
        assertEquals(5 * minute + 3 * minute, ChatGuardLogic.banDuration(ban, 0, 3))
        assertEquals(30 * minute + 6 * minute, ChatGuardLogic.banDuration(ban, 1, 6))
        assertEquals(2 * 60 * minute + 9 * minute, ChatGuardLogic.banDuration(ban, 2, 9))
    }

    @Test
    fun `passando do fim da escada o ultimo valor se repete, e o maximo corta`() {
        val settings = ChatBanSettings(durations = listOf("1m", "2m"), extraPerWarning = "0", max = "5m")
        assertEquals(2 * minute, ChatGuardLogic.banDuration(settings, 5, 0))
        val capped = ChatBanSettings(durations = listOf("1h"), extraPerWarning = "10m", max = "90m")
        assertEquals(90 * minute, ChatGuardLogic.banDuration(capped, 0, 9))
        val noCap = ChatBanSettings(durations = listOf("1h"), extraPerWarning = "10m", max = "0")
        assertEquals(60 * minute + 90 * minute, ChatGuardLogic.banDuration(noCap, 0, 9))
    }

    @Test
    fun `lista de tempos vazia ou invalida cai num padrao seguro`() {
        assertEquals(5 * minute, ChatGuardLogic.banDuration(ChatBanSettings(durations = emptyList(), extraPerWarning = "0", max = "0"), 0, 0))
        assertEquals(5 * minute, ChatGuardLogic.banDuration(ChatBanSettings(durations = listOf("abc", "-"), extraPerWarning = "0", max = "0"), 0, 0))
    }

    @Test
    fun `o segundo ciclo bane por mais tempo que o primeiro`() {
        val state = GuardState()
        var now = 0L
        val firstBan = run {
            var outcome: Outcome? = null
            repeat(4) { now += 1_000; outcome = ChatGuardLogic.violation(state, now, warn, ban) }
            outcome as Outcome.Banned
        }
        state.banUntil = 0 // o ban acabou
        val secondBan = run {
            var outcome: Outcome? = null
            repeat(4) { now += 1_000; outcome = ChatGuardLogic.violation(state, now, warn, ban) }
            outcome as Outcome.Banned
        }
        assertEquals(8 * minute, firstBan.millis)
        assertEquals(36 * minute, secondBan.millis)
        assertEquals(2, secondBan.bans)
        assertEquals(6, secondBan.totalWarnings)
    }

    // ---------------------------------------------------------------- esquecimento

    @Test
    fun `sem erros por um tempo os avisos somem`() {
        val state = GuardState()
        ChatGuardLogic.violation(state, 0, warn, ban)
        ChatGuardLogic.violation(state, 1_000, warn, ban)
        assertEquals(2, state.warnings)
        ChatGuardLogic.decay(state, 5 * minute, warn, ban) // ainda dentro dos 10 minutos
        assertEquals(2, state.warnings)
        ChatGuardLogic.decay(state, 12 * minute, warn, ban)
        assertEquals(0, state.warnings)
        assertEquals(2, state.totalWarnings) // o acumulado fica na memória do ban
    }

    @Test
    fun `reset_after vazio nunca esquece os avisos`() {
        val state = GuardState()
        ChatGuardLogic.violation(state, 0, warn, ban)
        ChatGuardLogic.decay(state, 99_999 * minute, WarningSettings(resetAfter = ""), ChatBanSettings(memoryDays = 0))
        assertEquals(1, state.warnings)
    }

    @Test
    fun `depois de muitos dias sem nada o historico de bans zera`() {
        val state = GuardState(warnings = 0, lastViolation = 0, bans = 3, totalWarnings = 9, banUntil = 0, lastBan = 0)
        ChatGuardLogic.decay(state, 10L * 86_400_000L, warn, ban)
        assertEquals(3, state.bans) // 10 dias ainda é memória
        ChatGuardLogic.decay(state, 31L * 86_400_000L, warn, ban)
        assertEquals(0, state.bans)
        assertEquals(0, state.totalWarnings)
    }

    // ---------------------------------------------------------------- config

    @Test
    fun `a config nasce comentada, volta a ser lida e explica a conta`() {
        val text = ConfigDocs.render(ChatGuardConfig(), SbGson.defaultsWriter)
        assertTrue(text.lines().any { it.trim().startsWith("//") && it.contains("HOW IT WORKS") })
        assertTrue(text.contains("\"max_warnings\": 3"))
        val again = SbGson.pretty.fromJson(text, ChatGuardConfig::class.java)
        assertEquals(3, again.warnings.maxWarnings)
        assertEquals(listOf("5m", "30m", "2h", "12h", "1d", "3d", "7d"), again.ban.durations)
        assertNotNull(again.messages.bannedNow)
    }

    @Test
    fun `json editado a mao com nulos e arrumado`() {
        val raw = SbGson.pretty.fromJson("""{ "ban": { "durations": ["1m", null], "blocked_commands": null }, "messages": null, "flood": null }""", ChatGuardConfig::class.java)
        val config = ChatGuardService.sanitize(raw)
        assertEquals(listOf("1m"), config.ban.durations)
        assertTrue(config.ban.blockedCommands.isEmpty())
        assertEquals(ChatGuardMessages().warning, config.messages.warning)
        assertFalse(config.flood.enabled)
    }
}
