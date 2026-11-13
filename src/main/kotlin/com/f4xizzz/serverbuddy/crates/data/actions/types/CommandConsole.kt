package com.f4xizzz.serverbuddy.crates.data.actions.types

import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.actions.ActionType
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class CommandConsole(
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    private val commands: List<String> = emptyList()
) : Action(ActionType.COMMAND_CONSOLE) {
    override fun executeAction(player: ServerPlayer, gui: SimpleGui) {
        val parsedCommands = commands.map { it /* TODO: do parsing */ }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Commands($parsedCommands): $this")

        for (command in parsedCommands) {
            CratesModule.INSTANCE.server.commands.performPrefixedCommand(
                CratesModule.INSTANCE.server.createCommandSourceStack(),
                command
            )
        }
    }

    override fun toString(): String {
        return "CommandConsole(commands=$commands)"
    }
}
