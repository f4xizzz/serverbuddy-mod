package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.ChatPipeline
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.ServerScheduler
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * As mecânicas de essentials do Solstice, reescritas aqui: casas, warps, spawn, /back, /tpa, /rtp, mensagens privadas, silenciar,
 * apelido, correio, god/fly/heal/feed/speed, AFK, /invsee, cela, anotações, /restart, bancadas portáteis e mais. Cada grupo de comandos
 * liga e desliga em Essentials/config.json. Os textos ficam em Essentials/messages.json.
 */
class EssentialsModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Essentials")

    override val id = "essentials"
    override val folder = "Essentials"
    override val legacyFolders = emptyList<String>()
    override val description = "Homes, warps, tpa, rtp, msg, mute, AFK, invsee, jail, restart, portable workstations... (Solstice mechanics)."

    companion object {
        @Volatile
        var config: EssentialsConfig = EssentialsConfig()

        /** Só fica true depois que as tabelas do banco existem. */
        @Volatile
        var ready: Boolean = false
    }

    override fun init() {
        if (Integrations.isLoaded("solstice")) {
            log.warn("Solstice is installed: ServerBuddy essentials is disabled to avoid conflicts (remove one of them).")
            return
        }
        config = ConfigFiles.load("Essentials", "config.json", EssentialsConfig::class.java) { EssentialsConfig() }
        Msg.load()
        StaffCommands.hook()
        PowerTools.register()
        CommandCooldowns.register()
        PlayerExtras.registerEvents()
        Styling.register()

        ServerLifecycleEvents.SERVER_STARTING.register {
            EssentialsRepository.createTables()
            TeleportCommands.loadWarps()
            JailService.load()
            ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            if (ready) ActiveTime.flush()
            TimeBars.clear()
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { ready = false }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (ready) Styling.tick(server)
            if (ready && server.tickCount % 20 == 0) {
                Teleports.tick(server.playerList.players)
                AfkService.tick(server)
                JailService.tick(server)
                RestartService.tick(server)
                ActiveTime.tick(server)
                TimeBars.tick(server)
                PlayerExtras.tickSleepers(server)
            }
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> if (ready) onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            if (ready) TeleportExtras.rememberLogoff(player)
            ChatService.onLeave(player)
            Teleports.onLeave(player.uuid)
            AfkService.onLeave(player)
            StaffCommands.onLeave(player.uuid)
            ActiveTime.onLeave(player.uuid)
            PowerTools.onLeave(player.uuid)
            CommandCooldowns.onLeave(player.uuid)
            PlayerExtras.onLeave(player.uuid)
            Notifications.onLeave(player.uuid)
            Styling.onLeave(player.uuid)
        }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> if (entity is ServerPlayer) Teleports.rememberDeath(entity) }
        // silenciados não falam, o chat da equipe fica só com a equipe, e falar tira do AFK. É a primeira etapa do chat (GATE): o que não é
        // chat público não chega ao trivia, ao gerenciador de chat nem ao formato.
        ChatPipeline.register(ChatPipeline.Stage.GATE) { chat ->
            if (!ready) return@register true
            val sender = chat.sender
            AfkService.activity(sender)
            when {
                ChatService.muteLeft(sender.uuid) > 0 -> {
                    ChatService.mutedMessage(sender)
                    false
                }
                sender.uuid in ChatService.staffChat -> {
                    ChatCommands.sendStaffChat(sender, chat.text)
                    false
                }
                else -> true
            }
        }
        SbCommands.onRegister { dispatcher, _ ->
            val groups = config.groups
            if (groups.teleport) TeleportCommands.register(dispatcher)
            if (groups.chat) {
                ChatCommands.register(dispatcher)
                PlayerExtras.registerChat(dispatcher)
                Notifications.register(dispatcher)
            }
            if (groups.player) {
                PlayerCommands.register(dispatcher)
                PlayerExtras.registerPlayer(dispatcher)
            }
            if (groups.staff) {
                StaffCommands.register(dispatcher)
                StaffExtras.register(dispatcher)
            }
            if (groups.utilities || groups.info) UtilityCommands.register(dispatcher, groups.utilities, groups.info)
            if (groups.info) PlayerExtras.registerInfo(dispatcher)
        }

        // %serverbuddy:nick% (apelido ou nome) e %serverbuddy:afk% ("AFK" ou vazio)
        SbPlaceholders.register("nick") { player, _ -> player?.let { ChatService.displayName(it.uuid, it.gameProfile.name) } ?: "" }
        SbPlaceholders.register("afk") { player, _ -> if (player != null && AfkService.isAfk(player.uuid)) AfkService.label().trimEnd() else "" }
    }

    private fun onJoin(player: ServerPlayer) {
        AfkService.cleanTag(player)
        val first = ChatService.onJoin(player)
        if (first) Styling.markFirstJoin(player.uuid)
        JailService.onJoin(player)
        RestartService.onJoin(player)
        ActiveTime.onJoin(player)
        PowerTools.onJoin(player)
        val spawn = config.teleport.spawn
        if ((first && spawn.onFirstJoin) || spawn.onJoin) {
            // quem chega pela primeira vez vai para o spawn de primeira entrada (/setfirstspawn), se existe; senão, para o /setspawn
            val place = (if (first) TeleportExtras.firstSpawn() else null) ?: TeleportCommands.spawn()
            // dá um instante para o jogador terminar de entrar antes de mandá-lo para o spawn
            ServerScheduler.later(10) { place?.let { if (!JailService.isJailed(player.uuid)) it.teleport(player) } }
        }
        PlayerExtras.motdOnJoin(player)
        Styling.onJoin(player)
        ServerScheduler.later(60) { ChatCommands.mailReminder(player) }
    }

    override fun idleReason(): String? = if (Integrations.isLoaded("solstice")) "Solstice is installed (remove one of them)" else null

    override fun reload() {
        config = ConfigFiles.load("Essentials", "config.json", EssentialsConfig::class.java) { EssentialsConfig() }
        Msg.load()
    }
}
