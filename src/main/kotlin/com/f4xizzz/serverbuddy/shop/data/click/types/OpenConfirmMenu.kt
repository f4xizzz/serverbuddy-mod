package com.f4xizzz.serverbuddy.shop.data.click.types

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.config.SoundOption
import com.f4xizzz.serverbuddy.shop.data.TransactionType
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOption
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOptionType
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntry
import com.f4xizzz.serverbuddy.shop.gui.ConfirmGUI
import com.f4xizzz.serverbuddy.shop.gui.ShopGUI
import com.f4xizzz.serverbuddy.shop.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class OpenConfirmMenu(
    sound: SoundOption? = null,
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("transactions", alternate = ["transaction"])
    val transactions: List<TransactionType> = listOf(),
    val menu: String
) : EntryClickOption(EntryClickOptionType.OPEN_CONFIRM_MENU, sound) {
    override fun execute(player: ServerPlayer, gui: ShopGUI, entry: ShopEntry) {
        Utils.printDebug("[Entry Click Option - ${type.name}] Player(${player.gameProfile.name}) Menu($menu): $this")

        if (transactions.isEmpty()) return
        if (!((transactions.contains(TransactionType.BUY) && entry.isBuyable()) || (transactions.contains(TransactionType.SELL) && entry.isSellable()))) {
            return
        }

        val config = ConfigManager.CONFIRM_MENUS[menu]
        if (config == null) {
            Utils.printError("Confirm Menu '$menu' not found! Please check your configuration.")
            return
        }
        sound?.playSound(player)
        ConfirmGUI(player, config, gui, entry).open()
    }

    override fun toString(): String {
        return "OpenConfirmMenu(menu='$menu')"
    }
}
