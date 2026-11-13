package com.f4xizzz.serverbuddy.crates.data.actions.types

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.actions.ActionType
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class CommandPlayer(
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    private val commands: List<String> = emptyList(),
    @SerializedName("permission_level")
    private val permissionLevel: Int? = null
) : Action(ActionType.COMMAND_PLAYER) {
    override fun executeAction(player: ServerPlayer, gui: SimpleGui) {
        val parsedCommands = commands.map { it /* TODO: do parsing */ }

        var source = player.createCommandSourceStack()
        if (permissionLevel != null) {
            source = source.withPermission(permissionLevel)
        }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Commands($parsedCommands): $this")

        for (command in parsedCommands) {
            CratesModule.INSTANCE.server.commands.performPrefixedCommand(
                source,
                command
            )
        }
    }

    override fun toString(): String {
        return "CommandPlayer(commands=$commands, permissionLevel=$permissionLevel)"
    }
}
