package com.f4xizzz.serverbuddy.shop.data.items.actions.types.internal

import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.data.items.actions.ActionType
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CloseShop(
    click: List<GenericClickType> = listOf(GenericClickType.ANY),
) : Action(ActionType.CLOSE_SHOP, click) {
    override fun executeAction(player: ServerPlayer, gui: IRefreshableGui) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}): $this")
        gui.close()
    }

    override fun toString(): String {
        return "CloseShop(click=$click)"
    }
}
