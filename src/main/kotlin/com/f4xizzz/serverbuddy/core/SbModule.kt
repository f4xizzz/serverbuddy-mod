package com.f4xizzz.serverbuddy.core

/**
 * Um módulo do ServerBuddy (Clear, Kits, Shop...). Cada módulo tem a própria pasta em
 * `config/ServerBuddy/<folder>` e os próprios comandos em `/serverbuddy <id>`.
 */
interface SbModule {
    /** Nome curto usado em `/serverbuddy <id>`, nos nós de permissão e nos logs (ex.: "kits"). */
    val id: String

    /** Pasta dentro de `config/ServerBuddy` (ex.: "Kits"). */
    val folder: String

    /** Pastas antigas dentro de `config/` que são importadas na primeira execução (ex.: "skieskits"). */
    val legacyFolders: List<String>

    /** Uma frase sobre o que o módulo faz (aparece em `/serverbuddy modules`). */
    val description: String get() = ""

    /**
     * Se o módulo subiu mas não está fazendo nada (outro mod no lugar dele, ou o mod de que ele depende não está instalado),
     * o motivo, que `/serverbuddy modules` mostra; `null` quando está funcionando.
     */
    fun idleReason(): String? = null

    fun init()

    fun reload()
}
