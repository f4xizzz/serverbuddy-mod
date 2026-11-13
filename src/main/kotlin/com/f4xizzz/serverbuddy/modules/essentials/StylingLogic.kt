package com.f4xizzz.serverbuddy.modules.essentials

/** A parte do estilo que é só conta com texto (sem Minecraft): dá para testar sem subir o servidor. */
object StylingLogic {
    private val LEGACY: Map<Char, String> = mapOf(
        '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua", '4' to "dark_red", '5' to "dark_purple",
        '6' to "gold", '7' to "gray", '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua", 'c' to "red",
        'd' to "light_purple", 'e' to "yellow", 'f' to "white", 'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough",
        'n' to "underlined", 'o' to "italic", 'r' to "reset"
    )

    /** "&cOlá &lmundo" vira "<red>Olá <bold>mundo". Vale & e §, e a cor em hexadecimal: &#ff8800. O resto do texto não muda. */
    @JvmStatic
    fun legacyToMiniMessage(text: String): String {
        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '&' || c == '§') && i + 1 < text.length) {
                val next = text[i + 1]
                if (next == '#' && i + 8 <= text.length && text.substring(i + 2, i + 8).all { it.isHex() }) {
                    out.append("<#").append(text, i + 2, i + 8).append('>')
                    i += 8
                    continue
                }
                val name = LEGACY[next.lowercaseChar()]
                if (name != null) {
                    out.append('<').append(name).append('>')
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** Troca os pedaços de [replacements] (a chave é o que o jogador escreve). Chave vazia é ignorada. */
    @JvmStatic
    fun applyReplacements(text: String, replacements: Map<String, String>): String {
        var out = text
        for ((from, to) in replacements) if (from.isNotEmpty()) out = out.replace(from, to)
        return out
    }

    /**
     * O formato que vale para um jogador: o primeiro da lista (na ordem em que foi escrito) cuja chave casa, e "default" se nenhuma casar.
     * [matches] diz se a chave casa com o jogador; "default" nunca é perguntado.
     */
    @JvmStatic
    fun pickFormat(formats: Map<String, String>, matches: (String) -> Boolean): String? {
        for ((key, value) in formats) if (!key.equals("default", ignoreCase = true) && matches(key)) return value
        return formats.entries.firstOrNull { it.key.equals("default", ignoreCase = true) }?.value
    }

    /** O nome aparece como palavra inteira no texto (sem diferença entre maiúsculas e minúsculas)? "Al" não é citado por "alguém". */
    @JvmStatic
    fun mentions(text: String, name: String): Boolean {
        if (name.isBlank()) return false
        var from = 0
        while (true) {
            val index = text.indexOf(name, from, ignoreCase = true)
            if (index < 0) return false
            val before = if (index > 0) text[index - 1] else ' '
            val afterIndex = index + name.length
            val after = if (afterIndex < text.length) text[afterIndex] else ' '
            if (!isWordChar(before) && !isWordChar(after)) return true
            from = index + 1
        }
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    /** Tokens do PlaceholderAPI no texto: %namespace:caminho% (com argumentos depois de um espaço, se houver). */
    private val PAPI = Regex("%([a-z0-9_.-]+:[a-z0-9_./-]+(?: [^%]*)?)%")

    @JvmStatic
    fun placeholderTokens(text: String): List<String> = PAPI.findAll(text).map { it.value }.distinct().toList()

    /** Quanto vale um som, volume ou tom guardado: devolve o número dentro dos limites, ou null se não é número. */
    @JvmStatic
    fun clampedNumber(text: String, min: Double, max: Double): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(min, max)
}
