package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.events.EventManager

/**
 * MOTD da lista de servidores com perfis por situação (whitelist, manutenção, lotado, horário, evento...), ícones,
 * contagem de jogadores editável e modo manutenção. Faz tudo que o MiniMOTD faz e mais.
 */
class MotdModule : SbModule {
    override val id = "motd"
    override val folder = "Motd"
    override val legacyFolders = emptyList<String>()
    override val description = "Server-list MOTD with profiles by situation (whitelist, maintenance, full, time of day...)."

    override fun init() {
        MotdService.load()
        MotdConditions.register("event") { arg, _ -> if (arg.isBlank()) EventManager.anyActive() else EventManager.isActive(arg) }
        SbCommands.onRegister { dispatcher, _ -> MotdCommands.register(dispatcher) }
    }

    // o MiniMOTD já responde à lista de servidores: o mixin do ServerBuddy se desliga (ver MotdMixinPlugin)
    override fun idleReason(): String? = if (Integrations.isLoaded("minimotd-fabric")) "MiniMOTD is installed (remove one of the two)" else null

    override fun reload() {
        MotdService.load()
    }
}
