package com.f4xizzz.serverbuddy.modules.commandblock

/** Casa o que o jogador digitou com as entradas da lista "commands" de uma regra (lógica pura, para testar sozinha). */
object CommandMatcher {
    /** O texto do comando em palavras minúsculas, sem a barra. */
    @JvmStatic
    fun words(command: String): List<String> =
        command.trim().removePrefix("/").trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Sem o prefixo de mod: "minecraft:tp" vira "tp". */
    @JvmStatic
    fun bare(word: String): String = word.substringAfter(':')

    /**
     * A entrada [entry] ("back", "home set", "minecraft:tp", "*") casa com o comando digitado?
     *
     * - [typed]: as palavras digitadas (veja [words]);
     * - [firstNames]: os nomes que a primeira palavra pode ter (o digitado, o nome de verdade quando é um apelido, com e sem prefixo);
     * - [rename]: o nome que o admin deu ao comando em modules.conf (o padrão é o próprio nome).
     */
    @JvmStatic
    fun matches(entry: String, typed: List<String>, firstNames: Set<String>, rename: (String) -> String = { it }): Boolean {
        val wanted = words(entry)
        if (wanted.isEmpty() || typed.isEmpty()) return false
        if (wanted[0] == "*") return wanted.size == 1
        val first = wanted[0]
        if (first !in firstNames && rename(first) !in firstNames) return false
        // as palavras seguintes da entrada precisam ser o começo do que foi digitado ("home set" casa com "home set casa")
        for (i in 1 until wanted.size) {
            if (typed.getOrNull(i) != wanted[i]) return false
        }
        return true
    }
}
