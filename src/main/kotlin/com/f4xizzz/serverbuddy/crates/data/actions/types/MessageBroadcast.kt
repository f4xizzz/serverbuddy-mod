package com.f4xizzz.serverbuddy.crates.data.actions.types

import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.actions.ActionType
import com.f4xizzz.serverbuddy.crates.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import com.f4xizzz.serverbuddy.crates.utils.asNative
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class MessageBroadcast(
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    private val message: List<String> = emptyList()
) : Action(ActionType.BROADCAST) {
    override fun executeAction(player: ServerPlayer, gui: SimpleGui) {
        val parsedMessages = message.map { PlaceholderManager.parse(player, it) }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Messages($parsedMessages): $this")

        for (line in parsedMessages) {
            CratesModule.INSTANCE.adventure.all().sendMessage(line.asNative())
        }
    }

    override fun toString(): String {
        return "MessageBroadcast(message=$message)"
    }
}
