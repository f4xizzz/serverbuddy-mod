package com.f4xizzz.serverbuddy.guis.config.actions.types

import com.f4xizzz.serverbuddy.guis.GuisModule
import com.f4xizzz.serverbuddy.guis.config.actions.Action
import com.f4xizzz.serverbuddy.guis.config.actions.ActionType
import com.f4xizzz.serverbuddy.guis.config.actions.ClickType
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class RefreshGUI(
    type: ActionType = ActionType.REFRESH_GUI,
    click: List<ClickType> = listOf(ClickType.ANY),
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
) : Action(type, click, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, gui: ChestGUI) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}): $this")
        GuisModule.INSTANCE.inventoryControllers[player.uuid]?.update()
    }

    override fun toString(): String {
        return "RefreshGUI(click=$click, delay=$delay, chance=$chance, requirements=$requirements)"
    }
}
