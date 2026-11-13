package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class MessagePlayer(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    private val message: List<String> = emptyList()
) : Action(ActionType.MESSAGE, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("Attempting to execute a ${type.identifier} Action: $this")
        for (line in message) {
            player.sendMessage(Utils.deserializeText(Utils.parsePlaceholders(player, line, kitId, kit, kitData)))
        }
    }

    override fun toString(): String {
        return "MessagePlayer(type=$type, requirements=$requirements, message=$message)"
    }
}
