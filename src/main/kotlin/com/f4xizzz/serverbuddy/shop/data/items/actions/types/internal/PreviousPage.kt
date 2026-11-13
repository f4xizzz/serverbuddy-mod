package com.f4xizzz.serverbuddy.shop.data.items.actions.types.internal

import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.data.items.actions.ActionType
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.gui.ShopGUI
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class PreviousPage(
    click: List<GenericClickType> = listOf(GenericClickType.ANY)
) : Action(ActionType.PREVIOUS_PAGE, click) {
    override fun executeAction(player: ServerPlayer, gui: IRefreshableGui) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) $this")

        if (gui !is ShopGUI) {
            Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) tried to execute a PreviousPage action not in a ShopGUI.")
            return
        }

        gui.previousPage()
    }

    override fun toString(): String {
        return "PreviousPage()"
    }
}
