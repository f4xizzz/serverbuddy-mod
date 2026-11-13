package com.f4xizzz.serverbuddy.guis.config.actions.types

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.guis.GuisModule
import com.f4xizzz.serverbuddy.guis.config.actions.Action
import com.f4xizzz.serverbuddy.guis.config.actions.ActionType
import com.f4xizzz.serverbuddy.guis.config.actions.ClickType
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CommandPlayer(
    type: ActionType = ActionType.COMMAND_PLAYER,
    click: List<ClickType> = listOf(ClickType.ANY),
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("commands",  alternate = ["command"])
    private val commands: List<String> = emptyList(),
    @SerializedName("permission_level")
    private val permissionLevel: Int? = null
) : Action(type, click, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, gui: ChestGUI) {
        val parsedCommands = commands.map { Utils.parsePlaceholders(player, it) }

        var source = player.createCommandSourceStack()
        if (permissionLevel != null) {
            source = source.withPermission(permissionLevel)
        }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Commands($parsedCommands): $this")

        for (command in parsedCommands) {
            GuisModule.INSTANCE.server.commands.performPrefixedCommand(
                source,
                command
            )
        }
    }

    override fun toString(): String {
        return "CommandPlayer(click=$click, delay=$delay, chance=$chance, requirements=$requirements, " +
                "commands=$commands)"
    }
}
