package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.logs.ServerConfig
import com.f4xizzz.serverbuddy.logs.ServerConfigManager

/**
 * Chaves de compatibilidade com os mods que o ServerBuddy substituiu. Ficam em
 * `config/ServerBuddy/serverconfig.conf` (`legacyCommands` e `legacyPermissions`, ambas ligadas por padrão).
 */
object LegacyCompat {
    /** Mantém os comandos raiz antigos (ex.: `/skieskits`, `/crates`) funcionando. */
    val commands: Boolean get() = config()?.legacyCommands ?: true

    /** Aceita os nós de permissão antigos (ex.: `skieskits.kit.diario`) além dos novos. */
    val permissions: Boolean get() = config()?.legacyPermissions ?: true

    private fun config(): ServerConfig? = try {
        ServerConfigManager.getConfig()
    } catch (_: Throwable) {
        null
    }
}
