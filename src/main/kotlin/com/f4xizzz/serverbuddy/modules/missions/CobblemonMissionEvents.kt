package com.f4xizzz.serverbuddy.modules.missions

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.core.ServerHolder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * Avisa as missões do que acontece no Cobblemon: capturas, batalhas, evoluções, ovos, fósseis, frutinhas, apricorns, doces de
 * experiência, itens dados a Pokémon, escaneamentos da Pokédex, subidas de nível, Pokémon ganhos e pescarias. Só é carregado com o
 * Cobblemon instalado (os nomes dos eventos são os do Cobblemon 1.8).
 */
internal object CobblemonMissionEvents {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Missions")

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event ->
            safe { MissionsService.eventProgress(event.player, "capture") { PokemonFilter.matches(it.target, info(event.pokemon)) } }
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            safe {
                val battle = event.battle
                for (winner in event.winners) {
                    val player = (winner as? PlayerBattleActor)?.entity ?: continue
                    if (battle.isPvW) {
                        // vencer o selvagem: conta um "defeat" para cada Pokémon derrotado
                        for (loser in event.losers) for (fainted in loser.pokemonList) {
                            val info = info(fainted.originalPokemon)
                            MissionsService.eventProgress(player, "defeat") { PokemonFilter.matches(it.target, info) }
                        }
                    } else {
                        MissionsService.eventProgress(player, "battle_win")
                    }
                }
            }
        }
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL) { event ->
            safe {
                val owner = event.pokemon.getOwnerPlayer() ?: return@safe
                MissionsService.eventProgress(owner, "evolve") { PokemonFilter.matches(it.target, info(event.pokemon)) }
            }
        }
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL) { event ->
            safe { MissionsService.eventProgress(event.player, "hatch") { PokemonFilter.matches(it.target, info(event.pokemon)) } }
        }

        // reviver um fóssil
        CobblemonEvents.FOSSIL_REVIVED.subscribe(Priority.NORMAL) { event ->
            safe {
                val player = event.player ?: return@safe
                MissionsService.eventProgress(player, "fossil") { PokemonFilter.matches(it.target, info(event.pokemon)) }
            }
        }
        // colher uma frutinha: o alvo é o nome dela ("oran", "cobblemon:oran_berry") ou "any"
        CobblemonEvents.BERRY_HARVEST.subscribe(Priority.NORMAL) { event ->
            safe {
                val player = event.player
                val id = event.berry.identifier.toString()
                MissionsService.eventProgress(player, "berry") { EventTargets.matchesId(it.target, id, "_berry", "_block") }
            }
        }
        // colher um apricorn: o alvo é a cor ("red", "blue"...) ou "any"
        CobblemonEvents.APRICORN_HARVESTED.subscribe(Priority.NORMAL) { event ->
            safe {
                val player = event.player
                val color = event.apricorn.name
                MissionsService.eventProgress(player, "apricorn") { EventTargets.matchesId(it.target, color, "_apricorn") }
            }
        }
        // usar um doce de experiência (só conta se ele fez efeito): o alvo é o item ("rare_candy", "exp_candy_m") ou "any"
        CobblemonEvents.EXPERIENCE_CANDY_USE_POST.subscribe(Priority.NORMAL) { event ->
            safe {
                if (!event.wasCandyConsumed()) return@safe
                val player = event.player
                val id = BuiltInRegistries.ITEM.getKey(event.item).toString()
                MissionsService.eventProgress(player, "candy") { EventTargets.matchesId(it.target, id) }
            }
        }
        // dar um item para segurar a um Pokémon: o alvo é o item ("cobblemon:leftovers") ou "any"
        CobblemonEvents.HELD_ITEM_POST.subscribe(Priority.NORMAL) { event ->
            safe {
                val owner = event.pokemon.getOwnerPlayer() ?: return@safe
                val received = event.received
                if (received.isEmpty) return@safe
                val id = BuiltInRegistries.ITEM.getKey(received.item).toString()
                MissionsService.eventProgress(owner, "held_item") { EventTargets.matchesId(it.target, id) }
            }
        }
        // escanear um Pokémon com a Pokédex
        CobblemonEvents.POKEMON_SCANNED.subscribe(Priority.NORMAL) { event ->
            safe {
                val pokemon = event.scannedPokemonEntityData.pokemon
                MissionsService.eventProgress(event.player, "scan") { PokemonFilter.matches(it.target, info(pokemon)) }
            }
        }
        // um Pokémon subiu de nível: "level_up pikachu@50 1" = um Pikachu chegar ao nível 50 (ou mais)
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL) { event ->
            safe {
                val owner = event.pokemon.getOwnerPlayer() ?: return@safe
                val info = info(event.pokemon)
                val level = event.newLevel
                MissionsService.eventProgress(owner, "level_up") { EventTargets.matchesLevelUp(it.target, info, level) }
            }
        }
        // ganhar um Pokémon, de qualquer jeito (captura, troca, presente, comando)
        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.NORMAL) { event ->
            safe {
                val player = ServerHolder.server?.playerList?.getPlayer(event.playerId) ?: return@safe
                MissionsService.eventProgress(player, "gained") { PokemonFilter.matches(it.target, info(event.pokemon)) }
            }
        }
        // pescar um Pokémon com a vara
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(Priority.NORMAL) { event ->
            safe {
                val player = event.bobber.owner as? ServerPlayer ?: return@safe
                MissionsService.eventProgress(player, "pokefish") { PokemonFilter.matches(it.target, info(event.pokemon.pokemon)) }
            }
        }
    }

    private fun info(pokemon: Pokemon) = PokemonFilter.Info(
        species = pokemon.species.name,
        shiny = pokemon.shiny,
        legendary = pokemon.isLegendary(),
        types = pokemon.species.types.map { it.name.lowercase() }.toSet()
    )

    /** Um erro numa missão nunca pode atrapalhar uma batalha ou uma captura. */
    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            LOGGER.error("Could not count Cobblemon mission progress", e)
        }
    }
}
