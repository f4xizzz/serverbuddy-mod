package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.ServerScheduler
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

/**
 * Missões do Minecraft (quebrar, fabricar, andar, matar, pescar...) com categorias, rotação diária e semanal, pré-requisitos e
 * prêmios; e, com o Cobblemon, missões de captura, batalha, evolução e ovos como brinde. Inspirado no CobbleMissions.
 */
class MissionsModule : SbModule {
    override val id = "missions"
    override val folder = "Missions"
    override val legacyFolders = emptyList<String>()
    override val description = "Minecraft and Cobblemon missions with menus, daily/weekly rotations and rewards."

    override fun init() {
        MissionsService.load()
        if (Integrations.cobblemon()) CobblemonMissionEvents.register()

        ServerLifecycleEvents.SERVER_STARTING.register {
            MissionsRepository.createTable()
            GlobalMissions.loadState()
            MissionsService.ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { MissionsService.flushAll() }
        ServerLifecycleEvents.SERVER_STOPPED.register { MissionsService.ready = false }
        ServerTickEvents.END_SERVER_TICK.register { server -> MissionsService.tick(server) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            MissionsService.onJoin(player)
            // dá um tempo para o jogador terminar de entrar antes do aviso
            ServerScheduler.later(60) { MissionsService.onJoinReminder(player) }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> MissionsService.onLeave(handler.player.uuid) }
        SbCommands.onRegister { dispatcher, _ -> MissionsCommands.register(dispatcher) }

        // %serverbuddy:missions_ready% (prontas para resgatar), missions_active (em andamento) e missions_done (já resgatadas)
        SbPlaceholders.register("missions_ready") { player, _ -> player?.let { MissionsService.readyCount(it.uuid).toString() } ?: "0" }
        SbPlaceholders.register("missions_active") { player, _ -> player?.let { MissionsService.activeCount(it.uuid).toString() } ?: "0" }
        SbPlaceholders.register("missions_done") { player, _ -> player?.let { MissionsService.claimedCount(it.uuid).toString() } ?: "0" }
        // missão global: %serverbuddy:missions_global_name%, _time (quanto falta), _top (quem está na frente), _top_progress, _progress (o do jogador), _next
        SbPlaceholders.register("missions_global_name") { _, _ -> GlobalMissions.activeName() }
        SbPlaceholders.register("missions_global_time") { _, _ -> if (GlobalMissions.activeKey() == null) "—" else GlobalMissions.timeLeftText() }
        SbPlaceholders.register("missions_global_top") { _, _ -> ServerHolder.server?.let { GlobalMissions.top(it)?.name } ?: "—" }
        SbPlaceholders.register("missions_global_top_progress") { _, _ -> ServerHolder.server?.let { GlobalMissions.top(it)?.progress } ?: "—" }
        SbPlaceholders.register("missions_global_progress") { player, _ ->
            val key = GlobalMissions.activeKey()
            val mission = key?.let { MissionsService.find(it) }
            val progress = if (player != null && mission != null) MissionsService.progressOf(player.uuid, mission) else null
            progress?.let { "${it.first}/${it.second}" } ?: "—"
        }
        SbPlaceholders.register("missions_global_next") { _, _ -> GlobalMissions.nextText() }
    }

    override fun reload() {
        MissionsService.load()
    }
}
