package com.f4xizzz.serverbuddy.shop.data.items.actions.types.internal

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.data.items.actions.ActionType
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.shop.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.shop.utils.Utils
import com.f4xizzz.serverbuddy.shop.utils.asNative
import net.minecraft.server.level.ServerPlayer

class MessagePlayer(
    click: List<GenericClickType> = listOf(GenericClickType.ANY),
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("message",  alternate = ["messages"])
    private val message: List<String> = emptyList()
) : Action(ActionType.MESSAGE, click) {
    override fun executeAction(player: ServerPlayer, gui: IRefreshableGui) {
        val parsedMessages = message.map { PlaceholderManager.parse(player, it) }
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Messages($parsedMessages): $this")
        for (line in parsedMessages) {
            player.sendMessage(line.asNative())
        }
    }

    override fun toString(): String {
        return "MessagePlayer(click=$click, message=$message)"
    }
}
