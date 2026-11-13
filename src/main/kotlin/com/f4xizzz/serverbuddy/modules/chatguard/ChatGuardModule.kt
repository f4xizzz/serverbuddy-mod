package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule

/**
 * Gerenciador de chat: barra a mesma mensagem repetida, dá avisos e, se a pessoa insiste, aplica um ban de chat cujo tempo cresce com
 * os bans e avisos que ela já tem. Funciona sozinho (não depende do módulo de essentials nem do Cobblemon).
 */
class ChatGuardModule : SbModule {
    override val id = "chatguard"
    override val folder = "ChatGuard"
    override val legacyFolders = emptyList<String>()
    override val description = "Chat guard: blocks repeated messages, gives warnings and applies escalating chat bans."

    override fun init() {
        ChatGuardService.load()
        ChatGuardService.register()
        SbCommands.onRegister { dispatcher, _ -> ChatGuardCommands.register(dispatcher) }
    }

    override fun reload() {
        ChatGuardService.load()
    }
}
