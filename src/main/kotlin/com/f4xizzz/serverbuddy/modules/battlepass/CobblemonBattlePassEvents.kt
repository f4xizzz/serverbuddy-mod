package com.f4xizzz.serverbuddy.modules.battlepass

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.trade.PlayerTradeParticipant
import org.slf4j.LoggerFactory

/** XP do passe pelos eventos do Cobblemon (capturar, derrotar, evoluir, chocar e trocar). Só é carregado com o Cobblemon instalado. */
internal object CobblemonBattlePassEvents {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/BattlePass")

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event ->
            safe { BattlePassService.addXp(event.player, BattlePassService.config.xp.catch.toLong(), "catch") }
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            safe {
                // só conta vencer o selvagem: um XP por Pokémon derrotado
                if (!event.battle.isPvW) return@safe
                for (winner in event.winners) {
                    val player = (winner as? PlayerBattleActor)?.entity ?: continue
                    val fainted = event.losers.sumOf { it.pokemonList.size }
                    BattlePassService.addXp(player, BattlePassService.config.xp.defeat.toLong() * fainted, "defeat")
                }
            }
        }
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL) { event ->
            safe { event.pokemon.getOwnerPlayer()?.let { BattlePassService.addXp(it, BattlePassService.config.xp.evolve.toLong(), "evolve") } }
        }
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.NORMAL) { event ->
            safe { BattlePassService.addXp(event.player, BattlePassService.config.xp.hatch.toLong(), "hatch") }
        }
        CobblemonEvents.TRADE_EVENT_POST.subscribe(Priority.NORMAL) { event ->
            safe {
                val xp = BattlePassService.config.xp.trade.toLong()
                (event.tradeParticipant1 as? PlayerTradeParticipant)?.player?.let { BattlePassService.addXp(it, xp, "trade") }
                (event.tradeParticipant2 as? PlayerTradeParticipant)?.player?.let { BattlePassService.addXp(it, xp, "trade") }
            }
        }
    }

    /** Um erro no passe nunca pode atrapalhar uma batalha ou uma captura. */
    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            LOGGER.error("Could not award battle pass XP", e)
        }
    }
}
