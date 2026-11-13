package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.authlib.GameProfile
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.protocol.status.ServerStatus
import net.minecraft.server.players.PlayerList
import org.slf4j.LoggerFactory

/**
 * Ponte entre os mixins (em Java) e o módulo de MOTD. Nada aqui pode lançar exceção para o mixin: se algo der errado
 * o servidor continua com o comportamento normal do Minecraft.
 */
object MotdBridge {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Motd")

    @Volatile
    private var warned = false

    /** O status que vai para a lista de servidores. */
    @JvmStatic
    fun status(vanilla: ServerStatus): ServerStatus = try {
        MotdService.status(vanilla)
    } catch (e: Throwable) {
        if (!warned) {
            warned = true
            LOGGER.error("Error building the MOTD; using the server's own.", e)
        }
        vanilla
    }

    /** Motivo para recusar [profile] agora (manutenção), ou null se ele pode entrar. */
    @JvmStatic
    fun loginRefusal(playerList: PlayerList, profile: GameProfile): Component? = try {
        if (!MotdService.active || !MotdService.maintenance || MotdService.canStayDuringMaintenance(playerList, profile)) {
            null
        } else {
            Texts.parse(MotdService.config.maintenance.kickMessage)
        }
    } catch (e: Throwable) {
        null
    }

    /** Troca a mensagem de "não está na whitelist" pela da config (as outras recusas passam como vieram). */
    @JvmStatic
    fun refineRefusal(original: Component?): Component? = try {
        val text = MotdService.config.login.whitelistMessage
        val key = (original?.contents as? TranslatableContents)?.key
        if (original == null || !MotdService.active || text == null || key != "multiplayer.disconnect.not_whitelisted") original else Texts.parse(text)
    } catch (e: Throwable) {
        original
    }
}
