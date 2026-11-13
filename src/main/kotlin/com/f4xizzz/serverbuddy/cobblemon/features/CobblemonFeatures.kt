package com.f4xizzz.serverbuddy.cobblemon.features

import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.cobblemon.features.odds.PokeOddsCommand
import com.f4xizzz.serverbuddy.cobblemon.features.shout.PokeShoutCommands
import com.f4xizzz.serverbuddy.cobblemon.features.tokens.TokenListeners
import com.f4xizzz.serverbuddy.cobblemon.features.tokens.TokensCommands
import com.f4xizzz.serverbuddy.cobblemon.features.tokens.TokensConfigManager
import com.f4xizzz.serverbuddy.cobblemon.features.view.PokeViewCommands
import com.f4xizzz.serverbuddy.cobblemon.features.view.PokeViewConfigManager
import com.f4xizzz.serverbuddy.core.Modules

/**
 * Recursos de Pokémon que usam a API do Cobblemon: `/pokesee`, `/compsee`, `/pokeshout`, `/pokeshoutall`, as fichas
 * (`/poketoken`, Shiny Ball) e `/pokeodds`. Só é carregado quando o Cobblemon está instalado e cada parte pode ser
 * desligada em `modules.conf`.
 */
object CobblemonFeatures {
    @JvmStatic
    fun init() {
        val tokens = ModuleSettings.enabled("poketokens")
        val view = ModuleSettings.enabled("pokeview")
        val odds = ModuleSettings.enabled("pokeodds")
        val shout = Modules.isRunning("shout")

        if (tokens) {
            TokensConfigManager.load()
            TokenListeners.register()
        }
        if (view) PokeViewConfigManager.load()

        SbCommands.onRegister { dispatcher, _ ->
            if (view) SbCommands.owned("pokeview") { PokeViewCommands.register(dispatcher) }
            if (shout) SbCommands.owned("shout") { PokeShoutCommands.register(dispatcher) }
            if (tokens) SbCommands.owned("poketokens") { TokensCommands.register(dispatcher) }
            if (odds) SbCommands.owned("pokeodds") { PokeOddsCommand.register(dispatcher) }
        }
    }

    /** Chamado pelo `/serverbuddy reload`. */
    @JvmStatic
    fun reload() {
        if (ModuleSettings.enabled("poketokens")) TokensConfigManager.load()
        if (ModuleSettings.enabled("pokeview")) PokeViewConfigManager.load()
    }
}
