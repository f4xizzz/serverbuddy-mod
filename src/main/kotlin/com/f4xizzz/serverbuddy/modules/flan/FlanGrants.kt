package com.f4xizzz.serverbuddy.modules.flan

import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * O que a claim dá a quem entra ("Ao entrar: dar tag" and "Ao entrar: dar permissão" no menu do Flan).
 *
 *  - **Tags**: tags do scoreboard (as mesmas do /tag), dadas na hora em que a pessoa entra e tiradas quando sai. Cada tag dada leva uma
 *    marca na lista de tags do jogador ([FlanGrantLogic.MARK]), então o mod só tira o que ele mesmo deu, e o estado sobrevive a um
 *    reinício do servidor.
 *  - **Permissões**: valem pela API de permissões do Fabric (o que os comandos do ServerBuddy e os mods que respeitam o LuckPerms
 *    consultam) enquanto a pessoa está dentro. Ficam só na memória: quem não está numa claim que dá permissões não tem nada.
 *
 * A checagem roda a cada `grants.check_interval_ticks` ticks, para os jogadores online.
 */
object FlanGrants {
    private val granted = ConcurrentHashMap<UUID, Set<String>>()
    private var counter = 0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> release(handler.player) }
        PermissionCheckEvent.EVENT.register(PermissionCheckEvent { source, permission -> overlay(source, permission) })
    }

    /** As permissões que o jogador tem agora por causa da claim em que está. */
    fun permissionsOf(uuid: UUID): Set<String> = granted[uuid].orEmpty()

    private fun overlay(source: SharedSuggestionProvider, permission: String): TriState {
        if (granted.isEmpty()) return TriState.DEFAULT
        val uuid = (source as? CommandSourceStack)?.entity?.uuid ?: return TriState.DEFAULT
        val set = granted[uuid] ?: return TriState.DEFAULT
        return if (permission in set) TriState.TRUE else TriState.DEFAULT
    }

    private fun tick(server: MinecraftServer) {
        val interval = FlanBridge.config.grants.checkIntervalTicks.coerceAtLeast(2)
        if (++counter < interval) return
        counter = 0
        val any = FlanStore.anyGrants()
        // sem nenhuma claim que dê algo, só falta limpar quem ainda leva marca de antes (queda do servidor, regra apagada...)
        if (!any && granted.isEmpty() && server.playerList.players.none { player -> player.tags.any { it.startsWith(FlanGrantLogic.MARK) } }) return
        for (player in server.playerList.players) {
            if (player.javaClass != ServerPlayer::class.java) continue // ignora jogadores falsos de outros mods
            update(player, any)
        }
        granted.keys.removeIf { id -> server.playerList.getPlayer(id) == null }
    }

    /** O que as claims onde o jogador está dão a ele agora: (tags, permissões). Vazio se ele não pode ficar ali. */
    private fun desiredFor(player: ServerPlayer): Pair<Set<String>, Set<String>> {
        val none = emptySet<String>() to emptySet<String>()
        val ref = FlanBridge.claimAt(player, player.blockPosition()) ?: return none
        // quem o Flan vai expulsar pela regra de entrada não ganha nada
        if (FlanBridge.deniedBy(player, ref) != null) return none
        val rules = listOfNotNull(ref, ref.parent).mapNotNull { FlanStore.get(it.id) }.filter { it.hasGrants }
        return if (rules.isEmpty()) none else FlanGrantLogic.union(rules)
    }

    private fun update(player: ServerPlayer, any: Boolean) {
        val desired = if (any) desiredFor(player) else emptySet<String>() to emptySet()
        applyTags(player, desired.first, mark = FlanBridge.config.grants.removeOnLeave)
        val before = granted[player.uuid].orEmpty()
        if (before != desired.second) {
            if (desired.second.isEmpty()) granted.remove(player.uuid) else granted[player.uuid] = desired.second
            // o menu de comandos do cliente depende das permissões: manda a árvore de novo para os comandos novos aparecerem (e os antigos sumirem)
            player.server.commands.sendCommands(player)
        }
    }

    /** [mark] = false (`remove_on_leave: false`): a tag dada fica forever, então não leva a marca que a tiraria depois. */
    private fun applyTags(player: ServerPlayer, desired: Set<String>, mark: Boolean = true) {
        val plan = FlanGrantLogic.planTags(desired, player.tags)
        if (plan.isEmpty) return
        for (tag in plan.add) {
            player.addTag(tag)
            if (mark) player.addTag(FlanGrantLogic.MARK + tag)
        }
        for (tag in plan.remove) {
            player.removeTag(tag)
            player.removeTag(FlanGrantLogic.MARK + tag)
        }
    }

    /** O jogador saiu do servidor: tira o que a claim deu (as tags ficam salvas no jogador, então precisam sair agora). */
    private fun release(player: ServerPlayer) {
        granted.remove(player.uuid)
        applyTags(player, emptySet())
    }
}
