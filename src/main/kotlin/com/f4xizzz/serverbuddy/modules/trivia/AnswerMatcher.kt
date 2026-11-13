package com.f4xizzz.serverbuddy.modules.trivia

import java.text.Normalizer
import kotlin.math.max
import kotlin.random.Random

/** Regras de resposta da trivia: comparação tolerante, dicas e formato de tempo. Tudo aqui é lógica pura (dá para testar). */
object AnswerMatcher {
    /** Minúsculo, sem acento e só com letras e números: "Pokémon  Go!" vira "pokemongo". */
    @JvmStatic
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
    }

    /**
     * [guess] é uma das [answers]? Compara sem maiúsculas, acentos, espaços e símbolos. Com [typoTolerance] 1 aceita uma letra
     * errada em respostas de 6 letras ou mais; com 2, até duas em respostas de 12 ou mais. Números têm que ser exatos.
     */
    @JvmStatic
    fun matches(guess: String, answers: List<String>, typoTolerance: Int): Boolean {
        val g = normalize(guess)
        if (g.isEmpty()) return false
        for (answer in answers) {
            val a = normalize(answer)
            if (a.isEmpty()) continue
            if (g == a) return true
            if (typoTolerance <= 0 || a.all { it.isDigit() }) continue
            val allowed = when {
                a.length >= 12 && typoTolerance >= 2 -> 2
                a.length >= 6 -> 1
                else -> 0
            }.coerceAtMost(typoTolerance)
            if (allowed > 0 && kotlin.math.abs(g.length - a.length) <= allowed && distance(g, a) <= allowed) return true
        }
        return false
    }

    /** Distância de edição (quantas letras trocar, pôr ou tirar). */
    @JvmStatic
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Dica da resposta: mostra [level] de [levels] partes das letras e esconde o resto com `_`. As letras reveladas são
     * as mesmas de uma dica para a outra (a segunda só acrescenta). Espaços e símbolos ficam como estão.
     * Ex.: `hint("Pikachu", 1, 2, seed)` → `P _ _ a _ _ _`.
     */
    @JvmStatic
    fun hint(answer: String, level: Int, levels: Int, seed: Long): String {
        val positions = answer.indices.filter { answer[it].isLetterOrDigit() }
        if (positions.isEmpty()) return answer
        val reveal = if (levels <= 0) 0 else (positions.size * level / (levels + 1)).coerceIn(0, positions.size - 1)
        val order = positions.shuffled(Random(seed))
        val shown = order.take(max(reveal, 1).coerceAtMost(positions.size - 1).coerceAtLeast(0)).toSet()
        val words = StringBuilder()
        for (i in answer.indices) {
            val c = answer[i]
            when {
                !c.isLetterOrDigit() -> words.append(if (c == ' ') "  " else c.toString()).append(' ')
                i in shown -> words.append(c).append(' ')
                else -> words.append("_ ")
            }
        }
        return words.toString().trim()
    }

    /** 45 → "45s", 90 → "1m 30s", 3900 → "1h 5m". */
    @JvmStatic
    fun formatSeconds(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> if (s % 60 == 0L) "${s / 60}m" else "${s / 60}m ${s % 60}s"
            else -> if ((s % 3600) / 60 == 0L) "${s / 3600}h" else "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }

    /** Tempo de resposta com décimos: 4230 ms → "4.2s". */
    @JvmStatic
    fun formatMillis(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0) + 50) / 100
        if (tenths < 600) return "${tenths / 10}.${tenths % 10}s"
        return formatSeconds(ms / 1000)
    }
}
