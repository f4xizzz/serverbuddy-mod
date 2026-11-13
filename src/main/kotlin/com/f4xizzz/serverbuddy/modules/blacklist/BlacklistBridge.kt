package com.f4xizzz.serverbuddy.modules.blacklist

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.net.messages.client.battle.BattleMadeInvalidChoicePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.core.Texts
import org.slf4j.LoggerFactory

/**
 * Ponte entre o mixin (em Java) e a batalha do Cobblemon. Nada aqui pode lançar exceção para o mixin: se algo der errado
 * a escolha do jogador passa como se a blacklist não existisse (nunca trava a batalha).
 */
object BlacklistBridge {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Blacklist")

    @Volatile
    private var warned = false

    /**
     * Chamado no início de `BattleActor.setActionResponses`. Devolve true para RECUSAR a escolha (o mixin cancela a
     * chamada); a escolha só é recusada se for um golpe proibido para esse jogador.
     */
    @JvmStatic
    fun intercept(actor: Any?, responses: List<*>?): Boolean = try {
        handle(actor, responses)
    } catch (e: Throwable) {
        if (!warned) {
            warned = true
            LOGGER.error("Error in the Blacklist; choices pass unchecked.", e)
        }
        false
    }

    private fun handle(actor: Any?, responses: List<*>?): Boolean {
        if (!BlacklistService.active || responses.isNullOrEmpty()) return false
        // só jogadores: Pokémon selvagens e NPCs escolhem golpes por conta própria e travariam sem o menu de escolha
        if (actor !is PlayerBattleActor) return false
        val player = actor.entity ?: return false
        val kind = battleKind(actor)
        for (response in responses) {
            val move = (response as? MoveActionResponse)?.moveName ?: continue
            val ban = BlacklistService.check(player, move, kind) ?: continue
            LOGGER.debug("{} tentou usar {} (proibido em: {})", player.gameProfile.name, ban.move, ban.contexts)
            refuse(actor, ban)
            return true
        }
        return false
    }

    /** "pvp", "npc" or "wild" (null se não deu para saber). */
    private fun battleKind(actor: BattleActor): String? {
        val battle = actor.battle
        return when {
            battle.isPvP -> "pvp"
            battle.isPvN -> "npc"
            battle.isPvW -> "wild"
            else -> null
        }
    }

    private fun refuse(actor: PlayerBattleActor, ban: BanResult) {
        val config = BlacklistService.config
        val template = ban.contexts.firstNotNullOfOrNull { name -> config.contexts[name]?.message?.takeIf { it.isNotBlank() } } ?: config.messages.banned
        val vars = mapOf("move" to Texts.lang("cobblemon.move.${ban.move}"), "context" to Texts.escape(ban.contexts.joinToString(", ")))
        actor.sendMessage(Texts.render(template, vars))
        // o menu de golpes do jogador fica esperando a resposta: reabre um tick depois, quando a chamada atual já acabou
        ServerScheduler.later(1) { reopenChoiceMenu(actor) }
    }

    /** Faz o cliente voltar ao menu de golpes, no mesmo ponto da batalha, para o jogador escolher outro golpe. */
    private fun reopenChoiceMenu(actor: BattleActor) {
        try {
            val battle = actor.battle
            if (battle.ended) return
            actor.mustChoose = true
            actor.request?.let { actor.sendUpdate(BattleQueueRequestPacket(it)) }
            actor.sendUpdate(BattleMadeInvalidChoicePacket())
            actor.sendUpdate(BattleMakeChoicePacket())
            battle.checkForInputDispatch()
        } catch (e: Throwable) {
            LOGGER.error("Could not reopen the move menu after a refused choice.", e)
        }
    }
}
