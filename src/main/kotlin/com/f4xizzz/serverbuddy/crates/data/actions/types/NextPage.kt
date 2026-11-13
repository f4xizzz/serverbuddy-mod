package com.f4xizzz.serverbuddy.crates.data.actions.types

import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.actions.ActionType
import com.f4xizzz.serverbuddy.crates.gui.PreviewInventory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class NextPage(
    val wrap: Boolean = false
) : Action(ActionType.NEXT_PAGE) {
    override fun executeAction(player: ServerPlayer, gui: SimpleGui) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) $this")

        if (gui !is PreviewInventory) {
            Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) tried to execute a NextPage action not paginated.")
            return
        }

        gui.nextPage(wrap)
    }

    override fun toString(): String {
        return "NextPage(wrap=$wrap)"
    }
}
