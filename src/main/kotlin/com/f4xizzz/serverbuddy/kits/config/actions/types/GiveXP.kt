package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class GiveXP(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    private val amount: Int = 0,
    private val level: Boolean = false
) : Action(ActionType.GIVE_XP, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("Attempting to execute a ${type.identifier} Action: $this")
        if (level) {
            player.giveExperienceLevels(amount)
        } else {
            player.giveExperiencePoints(amount)
        }
    }

    override fun toString(): String {
        return "GiveXP(type=$type, requirements=$requirements, amount=$amount, level=$level)"
    }
}
