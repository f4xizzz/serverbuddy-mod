package com.f4xizzz.serverbuddy.core

/**
 * Tempos escritos em texto, como nas configs e nos comandos: `30s`, `15m`, `2h`, `7d`, `1w` e combinações (`1h30m`).
 * Um número sozinho conta como segundos.
 */
object Durations {
    private val PART = Regex("""(\d+)\s*([smhdw])""", RegexOption.IGNORE_CASE)

    /** O texto em milissegundos, ou null se não dá para entender (ou é vazio). */
    @JvmStatic
    fun parseMillis(text: String?): Long? {
        val raw = text?.trim()?.lowercase() ?: return null
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { return it * 1000 }
        var total = 0L
        var consumed = 0
        for (match in PART.findAll(raw)) {
            val n = match.groupValues[1].toLong()
            total += n * when (match.groupValues[2]) {
                "s" -> 1_000L
                "m" -> 60_000L
                "h" -> 3_600_000L
                "d" -> 86_400_000L
                else -> 604_800_000L
            }
            consumed += match.value.length
        }
        // sobrou texto que não é espaço: está mal escrito
        if (consumed == 0 || raw.replace(" ", "").length != PART.findAll(raw).sumOf { it.value.replace(" ", "").length }) return null
        return total
    }

    /** 90_000 → "1m 30s"; 3_900_000 → "1h 5m"; 172_800_000 → "2d". */
    @JvmStatic
    fun format(millis: Long): String {
        var s = (millis.coerceAtLeast(0) + 500) / 1000
        if (s < 60) return "${s}s"
        val d = s / 86400
        s %= 86400
        val h = s / 3600
        s %= 3600
        val m = s / 60
        s %= 60
        val parts = ArrayList<String>()
        if (d > 0) parts.add("${d}d")
        if (h > 0) parts.add("${h}h")
        if (m > 0) parts.add("${m}m")
        if (s > 0 && d == 0L && h == 0L) parts.add("${s}s")
        return parts.take(2).joinToString(" ")
    }
}
