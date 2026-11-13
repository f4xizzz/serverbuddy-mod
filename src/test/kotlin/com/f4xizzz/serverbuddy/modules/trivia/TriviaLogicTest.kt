package com.f4xizzz.serverbuddy.modules.trivia

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TriviaLogicTest {
    @Test
    fun `normaliza sem acento, maiuscula e simbolo`() {
        assertEquals("pokemongo", AnswerMatcher.normalize("  Pokémon   GO! "))
        assertEquals("dragaodoend", AnswerMatcher.normalize("Dragão do End"))
        assertEquals("h2o", AnswerMatcher.normalize("H₂O".replace('₂', '2')))
    }

    @Test
    fun `resposta certa ignora acento e aceita um errinho em palavra longa`() {
        assertTrue(AnswerMatcher.matches("JUPITER", listOf("júpiter"), 0))
        assertTrue(AnswerMatcher.matches("ancient debris", listOf("Ancient Debris"), 0))
        // um erro em palavra de 6+ letras só passa com tolerância
        assertFalse(AnswerMatcher.matches("obsidiam", listOf("obsidian"), 0))
        assertTrue(AnswerMatcher.matches("obsidiam", listOf("obsidian"), 1))
        // palavra curta nunca tem tolerância
        assertFalse(AnswerMatcher.matches("creepr", listOf("creeper"), 0))
        assertFalse(AnswerMatcher.matches("cat", listOf("car"), 1))
        // número tem que ser exato
        assertFalse(AnswerMatcher.matches("1913", listOf("1912"), 2))
        assertTrue(AnswerMatcher.matches("1912", listOf("1912"), 2))
        // um erro de uma letra em "charmander" (10 letras) passa com tolerância 1; dois erros só com tolerância 2 e 12+ letras
        assertTrue(AnswerMatcher.matches("charmandar", listOf("charmander"), 1))
        assertFalse(AnswerMatcher.matches("charmondar", listOf("charmander"), 1))
        assertTrue(AnswerMatcher.matches("squirtle-evolucao", listOf("squirtle-evolucao"), 0))
        assertTrue(AnswerMatcher.matches("blastoixe tipo agua", listOf("blastoise tipo agua"), 2))
        assertFalse(AnswerMatcher.matches("", listOf("a"), 1))
    }

    @Test
    fun `distancia de edicao`() {
        assertEquals(0, AnswerMatcher.distance("pikachu", "pikachu"))
        assertEquals(1, AnswerMatcher.distance("pikachu", "pikachi"))
        assertEquals(3, AnswerMatcher.distance("gato", "cach"))
        assertEquals(2, AnswerMatcher.distance("abc", "abcde"))
    }

    @Test
    fun `dica revela letras sem mudar entre uma e outra`() {
        val seed = 42L
        val first = AnswerMatcher.hint("Pikachu", 1, 2, seed)
        val second = AnswerMatcher.hint("Pikachu", 2, 2, seed)
        assertEquals(7, first.count { it == '_' } + first.count { it.isLetter() })
        assertTrue(first.count { it == '_' } > second.count { it == '_' }, "$first / $second")
        // a segunda dica só acrescenta letras: o que a primeira mostrou continua lá
        val shownFirst = first.split(' ').withIndex().filter { it.value != "_" && it.value.isNotEmpty() }
        for ((i, letter) in shownFirst) assertEquals(letter, second.split(' ')[i])
        // sempre sobra pelo menos uma letra escondida
        assertTrue(AnswerMatcher.hint("ab", 2, 2, seed).contains('_'))
        // espaços e símbolos ficam visíveis
        assertTrue(AnswerMatcher.hint("Mr. Mime", 1, 2, seed).contains('.'))
    }

    @Test
    fun `formata tempos`() {
        assertEquals("45s", AnswerMatcher.formatSeconds(45))
        assertEquals("1m 30s", AnswerMatcher.formatSeconds(90))
        assertEquals("2m", AnswerMatcher.formatSeconds(120))
        assertEquals("1h 5m", AnswerMatcher.formatSeconds(3900))
        assertEquals("4.2s", AnswerMatcher.formatMillis(4230))
        assertEquals("0.0s", AnswerMatcher.formatMillis(0))
    }

    @Test
    fun `perguntas manuais e geradores simples`() {
        Questions.registerBuiltIn()
        val manual = JsonParser.parseString("""{ "question": "Qual é o maior planeta?", "answers": ["júpiter", "jupiter"], "hint": "começa com J" }""").asJsonObject
        val q = Questions.create(manual)!!
        assertEquals("Qual é o maior planeta?", q.text)
        assertEquals(listOf("júpiter", "jupiter"), q.answers)
        assertEquals("começa com J", q.hint)
        assertEquals("", Questions.create(JsonParser.parseString("""{ "question": "x?", "answer": "y", "hint": false }""").asJsonObject)!!.hint)
        assertNull(Questions.create(JsonParser.parseString("""{ "question": "sem resposta" }""").asJsonObject))
        assertNull(Questions.create(JsonParser.parseString("""{ "generator": "nao_existe" }""").asJsonObject))
        assertFalse(Questions.isValid(JsonParser.parseString("""{ "generator": "nao_existe" }""").asJsonObject))
        assertTrue(Questions.isValid(JsonParser.parseString("""{ "generator": "equation" }""").asJsonObject))

        repeat(50) {
            val eq = Questions.create(JsonParser.parseString("""{ "generator": "equation", "difficulty": "hard" }""").asJsonObject, Random(it))!!
            assertEquals(1, eq.answers.size)
            assertTrue(eq.answers[0].toIntOrNull() != null, eq.toString())
        }
        val typing = Questions.create(JsonParser.parseString("""{ "generator": "typing", "length": 8 }""").asJsonObject)!!
        assertEquals(8, typing.answers[0].length)
        assertTrue(typing.text.contains(typing.answers[0]))
        val scrambled = Questions.create(JsonParser.parseString("""{ "generator": "unscramble", "words": ["diamante"] }""").asJsonObject)!!
        assertEquals(listOf("diamante"), scrambled.answers)
        assertEquals("diamante".toList().sorted(), scrambled.text.substringAfterLast(' ').toList().sorted())
        assertNotNull(Questions.create(JsonParser.parseString("""{ "generator": "unscramble" }""").asJsonObject))
    }

    @Test
    fun `converte uma pool do SkiesTrivia`() {
        val old = JsonParser.parseString(
            """
            {
              "enabled": true,
              "settings": { "weight": 4, "answer_time": 15, "messages": { "start": ["a %question%"], "generated": { "cobblemon:dex_num_to_species": "Qual é o %dex_number%?" } } },
              "rewards": [
                { "type": "COMMAND_CONSOLE", "weight": 5, "commands": ["give %player% dirt 1"] },
                { "type": "COMMAND_PLAYER", "weight": 1, "commands": ["spawn"] }
              ],
              "questions": [
                { "question": "Capital da França?", "answers": ["paris"] },
                { "type": "GENERATED", "id": "skiestrivia:equation_easy" },
                { "type": "GENERATED", "id": "cobblemon:dex_num_to_species", "blacklist": ["cobblemon:mew"] }
              ]
            }
            """.trimIndent()
        ).asJsonObject
        val out = TriviaImport.convertPool(old)!!
        assertEquals(4, out.get("weight").asInt)
        assertEquals(15, out.get("answer_time").asInt)
        val rewards = out.getAsJsonArray("rewards")
        assertEquals("command", rewards[0].asJsonObject.get("type").asString)
        assertEquals("give %player% dirt 1", rewards[0].asJsonObject.getAsJsonArray("command")[0].asString)
        assertEquals("player", rewards[1].asJsonObject.get("as").asString)
        val questions = out.getAsJsonArray("questions")
        assertEquals("paris", questions[0].asJsonObject.getAsJsonArray("answers")[0].asString)
        assertEquals("equation", questions[1].asJsonObject.get("generator").asString)
        assertEquals("easy", questions[1].asJsonObject.get("difficulty").asString)
        assertEquals("dex_number_to_species", questions[2].asJsonObject.get("generator").asString)
        assertEquals("cobblemon:mew", questions[2].asJsonObject.getAsJsonArray("exclude")[0].asString)
        assertEquals("Qual é o %dex_number%?", questions[2].asJsonObject.get("text").asString)
        assertEquals("a %question%", out.getAsJsonObject("messages").getAsJsonArray("start")[0].asString)
        // um arquivo que já está no formato daqui não é convertido de novo
        assertNull(TriviaImport.convertPool(JsonParser.parseString("""{ "enabled": true, "weight": 2, "questions": [ { "question": "x", "answers": ["y"] } ], "rewards": [] }""").asJsonObject))
    }
}
