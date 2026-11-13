package com.f4xizzz.serverbuddy.core.rewards

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/** Parte das recompensas que usa o Cobblemon (só é carregada quando ele está instalado). */
internal object CobblemonRewards {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Rewards")

    /** Cria o Pokémon descrito por [spec] (o mesmo texto do /pokegive) e põe no time; se o time estiver cheio, vai para o PC. */
    fun give(player: ServerPlayer, spec: String) {
        val pokemon = PokemonProperties.parse(spec).create()
        if (!Cobblemon.storage.getParty(player).add(pokemon) && !Cobblemon.storage.getPC(player).add(pokemon)) {
            LOGGER.warn("Could not store the Pokémon '{}' of {} (party and PC full).", spec, player.gameProfile.name)
        }
    }
}
