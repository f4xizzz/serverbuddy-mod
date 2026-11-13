package com.f4xizzz.serverbuddy.modules.commandblock

import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule

/**
 * Bloqueio de comandos por condição: impede o /back (ou qualquer comando) de quem tem uma tag, uma permissão, está numa dimensão ou
 * num horário. A tag pode vir do /tag do Minecraft, do menu /tags ou de uma claim do Flan ("Ao entrar: dar tag").
 */
class CommandBlockModule : SbModule {
    override val id = "commandblock"
    override val folder = "CommandBlock"
    override val legacyFolders = emptyList<String>()
    override val description = "Blocks commands (such as /back) based on tags, permissions, dimensions or schedules."

    override fun init() {
        CommandBlockService.load()
        CommandBlockService.register()
        SbCommands.onRegister { dispatcher, _ -> CommandBlockCommands.register(dispatcher) }
    }

    override fun reload() {
        CommandBlockService.load()
    }
}
