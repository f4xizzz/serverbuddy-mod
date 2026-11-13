package com.f4xizzz.serverbuddy.core

import java.util.UUID

/** Recarga por jogador e por tipo de uso (shouts). Fica só na memória. */
object Cooldowns {
    private val last = HashMap<String, Long>()

    /**
     * Tenta usar [key]. Devolve 0 se estava liberado (e já marca o uso) ou os segundos que faltam.
     * `seconds <= 0` desliga a recarga.
     */
    @JvmStatic
    fun use(key: String, player: UUID, seconds: Int): Long {
        if (seconds <= 0) return 0
        val now = System.currentTimeMillis()
        val id = "$key:$player"
        val until = (last[id] ?: 0L) + seconds * 1000L
        if (now < until) return (until - now + 999) / 1000
        last[id] = now
        return 0
    }
}
