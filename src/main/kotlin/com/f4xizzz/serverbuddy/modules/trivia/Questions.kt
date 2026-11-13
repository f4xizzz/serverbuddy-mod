package com.f4xizzz.serverbuddy.modules.trivia

import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Uma pergunta pronta para ser feita. [hint] = null (dica automática), "" (sem dica) ou um texto fixo. */
class Question(val text: String, val answers: List<String>, val hint: String? = null)

/** Cria perguntas na hora, a partir das opções escritas na pool (`{ "generator": "equation", "difficulty": "easy" }`). */
fun interface QuestionGenerator {
    /** Uma pergunta nova, ou null se não deu (por exemplo, sem dados do Cobblemon). */
    fun generate(options: JsonObject, random: Random): Question?
}

/**
 * Onde as perguntas de uma pool viram [Question]. As manuais (`"question"` + `"answers"`) são lidas aqui; as geradas
 * (`"generator": "id"`) vêm dos geradores registrados. Outros módulos podem registrar os seus com [register].
 */
object Questions {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Trivia")
    private val generators = ConcurrentHashMap<String, QuestionGenerator>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun register(id: String, generator: QuestionGenerator) {
        generators[id.lowercase()] = generator
    }

    fun ids(): List<String> = generators.keys.sorted()

    /** O gerador existe (ou a pergunta é manual)? Usado para avisar de erros de digitação ao carregar. */
    fun isValid(entry: JsonObject): Boolean {
        val id = entry.get("generator")?.takeIf { it.isJsonPrimitive }?.asString
        if (id != null) return generators.containsKey(id.lowercase())
        return entry.get("question")?.isJsonPrimitive == true && answersOf(entry).isNotEmpty()
    }

    /** Monta a pergunta de [entry]. null se está mal escrita ou o gerador não conseguiu. */
    fun create(entry: JsonObject, random: Random = Random.Default): Question? {
        val id = entry.get("generator")?.takeIf { it.isJsonPrimitive }?.asString
        if (id != null) {
            val generator = generators[id.lowercase()]
            if (generator == null) {
                if (warned.add(id)) LOGGER.warn("Unknown question generator: '{}'. Available generators: {}", id, ids())
                return null
            }
            return try {
                generator.generate(entry, random)
            } catch (e: Exception) {
                if (warned.add("err:$id")) LOGGER.warn("O gerador '{}' falhou: {}", id, e.toString())
                null
            }
        }
        val text = entry.get("question")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val answers = answersOf(entry)
        if (answers.isEmpty()) return null
        val hint = entry.get("hint")?.takeIf { it.isJsonPrimitive }?.let { if (it.asJsonPrimitive.isBoolean) (if (it.asBoolean) null else "") else it.asString }
        return Question(text, answers, hint)
    }

    private fun answersOf(entry: JsonObject): List<String> {
        val raw = entry.get("answers") ?: entry.get("answer") ?: return emptyList()
        if (raw.isJsonArray) return raw.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }.filter { it.isNotBlank() }
        return if (raw.isJsonPrimitive && raw.asString.isNotBlank()) listOf(raw.asString) else emptyList()
    }

    /** Texto da opção `"text"` da pergunta gerada (o jogador pode trocar o enunciado) ou o [default]. */
    fun template(options: JsonObject, default: String): String = options.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: default

    fun registerBuiltIn() {
        register("equation", EquationGenerator)
        register("typing", TypingGenerator)
        register("unscramble", UnscrambleGenerator)
    }
}

/** `{ "generator": "equation", "difficulty": "easy|medium|hard" }`: uma conta para resolver. */
object EquationGenerator : QuestionGenerator {
    override fun generate(options: JsonObject, random: Random): Question {
        val difficulty = options.get("difficulty")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "easy"
        val (expression, result) = when (difficulty) {
            "hard" -> hard(random)
            "medium" -> medium(random)
            else -> easy(random)
        }
        return Question(Questions.template(options, "What is %equation%?").replace("%equation%", expression), listOf(result.toString()), hint = "")
    }

    private fun easy(r: Random): Pair<String, Int> = when (r.nextInt(3)) {
        0 -> r.nextInt(1, 51).let { a -> r.nextInt(1, 51).let { b -> "$a + $b" to a + b } }
        1 -> r.nextInt(10, 61).let { a -> r.nextInt(1, a).let { b -> "$a - $b" to a - b } }
        else -> r.nextInt(2, 11).let { a -> r.nextInt(2, 11).let { b -> "$a × $b" to a * b } }
    }

    private fun medium(r: Random): Pair<String, Int> = when (r.nextInt(4)) {
        0 -> r.nextInt(20, 201).let { a -> r.nextInt(20, 201).let { b -> "$a + $b" to a + b } }
        1 -> r.nextInt(50, 301).let { a -> r.nextInt(10, a).let { b -> "$a - $b" to a - b } }
        2 -> r.nextInt(6, 21).let { a -> r.nextInt(6, 21).let { b -> "$a × $b" to a * b } }
        else -> r.nextInt(3, 21).let { answer -> r.nextInt(3, 13).let { b -> "${answer * b} ÷ $b" to answer } }
    }

    private fun hard(r: Random): Pair<String, Int> = when (r.nextInt(3)) {
        0 -> r.nextInt(3, 16).let { a -> r.nextInt(3, 16).let { b -> r.nextInt(5, 60).let { c -> "$a × $b + $c" to a * b + c } } }
        1 -> r.nextInt(2, 15).let { a -> r.nextInt(2, 15).let { b -> r.nextInt(2, 10).let { c -> "($a + $b) × $c" to (a + b) * c } } }
        else -> r.nextInt(10, 60).let { a -> r.nextInt(10, 60).let { b -> r.nextInt(2, 10).let { c -> "$a + $b - $c" to a + b - c } } }
    }
}

/** `{ "generator": "typing", "length": 6 }`: uma corrida de digitação (vence quem copiar o texto primeiro). */
object TypingGenerator : QuestionGenerator {
    // sem letras e números que se confundem (l, 1, i, 0, o)
    private const val CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

    override fun generate(options: JsonObject, random: Random): Question {
        val length = (options.get("length")?.takeIf { it.isJsonPrimitive }?.asInt ?: 6).coerceIn(3, 24)
        val code = (1..length).map { CHARS[random.nextInt(CHARS.length)] }.joinToString("")
        return Question(Questions.template(options, "Type as fast as you can: %code%").replace("%code%", code), listOf(code), hint = "")
    }
}

/** `{ "generator": "unscramble", "words": ["diamond", "creeper"] }`: descobrir a palavra embaralhada. Sem "words", usa uma lista de Minecraft. */
object UnscrambleGenerator : QuestionGenerator {
    private val DEFAULT_WORDS = listOf(
        "diamond", "emerald", "redstone", "creeper", "skeleton", "villager", "enderman", "pickaxe", "sword", "armor",
        "enchantment", "potion", "furnace", "mining", "compass", "obsidian", "portal", "dragon", "trident", "elytra",
        "anvil", "torch", "bucket", "arrow", "blacksmith", "crops", "horse", "witch", "looting", "lantern"
    )

    override fun generate(options: JsonObject, random: Random): Question? {
        val custom = options.get("words")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonPrimitive }?.map { it.asString }?.filter { it.isNotBlank() }
        val word = (custom?.takeIf { it.isNotEmpty() } ?: DEFAULT_WORDS).random(random)
        return Question(Questions.template(options, "Unscramble the word: %word%").replace("%word%", scramble(word, random)), listOf(word))
    }

    /** Embaralha cada palavra do texto (garante que mudou, quando dá). */
    @JvmStatic
    fun scramble(text: String, random: Random = Random.Default): String =
        text.split(' ').joinToString(" ") { word ->
            if (word.length <= 2 || word.toSet().size == 1) return@joinToString word
            var shuffled = word
            repeat(8) {
                shuffled = word.toList().shuffled(random).joinToString("")
                if (shuffled != word) return@joinToString shuffled
            }
            shuffled
        }
}
