package com.f4xizzz.serverbuddy.shop.data.click.types

import com.f4xizzz.serverbuddy.shop.config.SoundOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOptionType
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntry
import com.f4xizzz.serverbuddy.shop.gui.ShopGUI
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class Sell(
    sound: SoundOption? = null,
    val amount: Int
) : EntryClickOption(EntryClickOptionType.SELL, sound) {
    override fun execute(player: ServerPlayer, gui: ShopGUI, entry: ShopEntry) {
        Utils.printDebug("[Entry Click Option - ${type.name}] Player(${player.gameProfile.name}) Amount($amount): $this")
        if (entry.isSellable()) {
            entry.trySell(player, gui.instance, amount, gui, sound)
        }
    }

    override fun toString(): String {
        return "Sell(amount=$amount)"
    }
}
