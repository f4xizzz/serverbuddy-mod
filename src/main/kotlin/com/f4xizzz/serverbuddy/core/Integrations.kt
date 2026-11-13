package com.f4xizzz.serverbuddy.core

import net.fabricmc.loader.api.FabricLoader

/**
 * Integracoes opcionais. O ServerBuddy funciona sozinho; tudo que usa a API de outro mod so e carregado quando
 * esse mod esta instalado (sempre confira aqui ANTES de tocar numa classe dele).
 */
object Integrations {
    private val cobblemon: Boolean by lazy { FabricLoader.getInstance().isModLoaded("cobblemon") }

    /** Cobblemon instalado: liga cacadas, bosses, areas de captura, combo, Pokedex, editor/builder e o que mais usar Pokemon. */
    @JvmStatic
    fun cobblemon(): Boolean = cobblemon

    /**
     * Cobblemon installed AND this jar's plan includes the Pokémon features (hunts, bosses, capture areas, combo, Pokédex tools,
     * editor...). They live in the Plus and Pro jars; the Basic jar does not contain their code.
     */
    @JvmStatic
    fun cobblemonFeatures(): Boolean = cobblemon

    @JvmStatic
    fun isLoaded(modId: String): Boolean = FabricLoader.getInstance().isModLoaded(modId)
}
