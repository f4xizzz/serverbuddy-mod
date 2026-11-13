package com.f4xizzz.serverbuddy.modules.blacklist

import com.cobblemon.mod.common.api.moves.Moves
import com.f4xizzz.serverbuddy.core.Integrations

/**
 * Consulta a lista de golpes do Cobblemon (para sugerir nomes e achar erros de digitação na config).
 * Só toca no Cobblemon quando ele está instalado; sem ele tudo devolve "não sei".
 */
object CobblemonMoves {
    /** true/false se o golpe [id] existe; null se não dá para saber (sem Cobblemon ou golpes ainda não carregados). */
    @JvmStatic
    fun exists(id: String): Boolean? = try {
        if (!Integrations.cobblemon() || Moves.all().isEmpty()) null else Moves.getByName(id) != null
    } catch (e: Throwable) {
        null
    }

    /** Os nomes (ids do Showdown) de todos os golpes, para o autocompletar dos comandos. */
    @JvmStatic
    fun names(): Collection<String> = try {
        if (Integrations.cobblemon()) Moves.names() else emptyList()
    } catch (e: Throwable) {
        emptyList()
    }
}
