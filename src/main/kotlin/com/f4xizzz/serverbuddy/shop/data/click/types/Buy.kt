package com.f4xizzz.serverbuddy.shop.data.click.types

import com.f4xizzz.serverbuddy.shop.config.SoundOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOptionType
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntry
import com.f4xizzz.serverbuddy.shop.gui.ShopGUI
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class Buy(
    sound: SoundOption? = null,
    val amount: Int
) : EntryClickOption(EntryClickOptionType.BUY, sound) {
    override fun execute(player: ServerPlayer, gui: ShopGUI, entry: ShopEntry) {
        Utils.printDebug("[Entry Click Option - ${type.name}] Player(${player.gameProfile.name}) Amount($amount): $this")
        if (entry.isBuyable()) {
            entry.tryBuy(player, gui.instance, amount, gui, sound)
        }
    }

    override fun toString(): String {
        return "Buy(amount=$amount)"
    }
}
