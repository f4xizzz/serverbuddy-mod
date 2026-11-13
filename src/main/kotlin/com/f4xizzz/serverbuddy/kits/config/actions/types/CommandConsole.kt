package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class CommandConsole(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    private val commands: List<String> = emptyList()
) : Action(ActionType.COMMAND_CONSOLE, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("Attempting to execute a ${type.identifier} Action: $this")
        if (KitsModule.INSTANCE.server.commands == null) {
            Utils.printError("There was an error while executing an action for player ${player.name}: Server was somehow null on command execution?")
            return
        }

        for (command in commands) {
            KitsModule.INSTANCE.server.let { server ->
                server.executeIfPossible {
                    server.commands.performPrefixedCommand(
                        KitsModule.INSTANCE.server.createCommandSourceStack(),
                        Utils.parsePlaceholders(player, command, kitId, kit, kitData)
                    )
                }
            }
        }
    }

    override fun toString(): String {
        return "CommandConsole(type=$type, requirements=$requirements, commands=$commands)"
    }
}
