package com.f4xizzz.serverbuddy.modules.motd

import java.util.concurrent.ThreadLocalRandom

/**
 * Jogadores "falsos" na contagem da lista de servidores. Formatos:
 * `+3` ou `3` soma 3 · `-2` tira 2 · `=50` sempre 50 · `10+` no mínimo 10 · `3:6` soma um número entre 3 e 6 · `25%` soma 25%.
 */
object PlayerCountModifier {
    /** A função que transforma o número real no mostrado, ou null se o texto não faz sentido. */
    @JvmStatic
    fun parse(text: String): ((Int) -> Int)? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            when {
                t.startsWith("=") -> {
                    val value = t.substring(1).trim().toInt()
                    return { value }
                }
                t.endsWith("%") -> {
                    val factor = 1.0 + t.dropLast(1).trim().toDouble() / 100.0
                    return { actual -> Math.ceil(factor * actual).toInt() }
                }
                t.endsWith("+") -> {
                    val minimum = t.dropLast(1).trim().toInt()
                    return { actual -> maxOf(minimum, actual) }
                }
                t.contains(":") -> {
                    val (from, to) = t.split(":").map { it.trim().toInt() }
                    val low = minOf(from, to)
                    val high = maxOf(from, to)
                    return { actual -> actual + ThreadLocalRandom.current().nextInt(low, high + 1) }
                }
                else -> {
                    val add = t.removePrefix("+").trim().toInt()
                    return { actual -> actual + add }
                }
            }
        } catch (e: RuntimeException) {
            null
        }
    }
}
