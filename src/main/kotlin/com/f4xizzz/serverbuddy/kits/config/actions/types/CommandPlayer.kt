package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class CommandPlayer(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    private val commands: List<String> = emptyList(),
    @SerializedName("permission_level")
    private val permissionLevel: Int? = null
) : Action(ActionType.COMMAND_PLAYER, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("Attempting to execute a ${type.identifier} Action: $this")

        var source = player.createCommandSourceStack()

        if (permissionLevel != null) {
            source = source.withPermission(permissionLevel)
        }

        for (command in commands) {
            KitsModule.INSTANCE.server.let { server ->
                server.executeIfPossible {
                    server.commands.performPrefixedCommand(
                        source,
                        Utils.parsePlaceholders(player, command, kitId, kit, kitData)
                    )
                }
            }
        }
    }

    override fun toString(): String {
        return "CommandPlayer(type=$type, requirements=$requirements, commands=$commands)"
    }
}
