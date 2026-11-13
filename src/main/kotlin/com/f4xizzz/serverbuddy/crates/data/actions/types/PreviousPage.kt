package com.f4xizzz.serverbuddy.crates.data.actions.types

import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.actions.ActionType
import com.f4xizzz.serverbuddy.crates.gui.PreviewInventory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class PreviousPage(
    val wrap: Boolean = false
) : Action(ActionType.PREVIOUS_PAGE) {
    override fun executeAction(player: ServerPlayer, gui: SimpleGui) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) $this")

        if (gui !is PreviewInventory) {
            Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) tried to execute a PreviousPage action not in paginated.")
            return
        }

        gui.previousPage(wrap)
    }

    override fun toString(): String {
        return "PreviousPage(wrap=$wrap)"
    }
}
