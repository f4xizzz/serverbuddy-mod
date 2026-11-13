package com.f4xizzz.serverbuddy.guis.config.actions.types

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.guis.GuisModule
import com.f4xizzz.serverbuddy.guis.config.actions.Action
import com.f4xizzz.serverbuddy.guis.config.actions.ActionType
import com.f4xizzz.serverbuddy.guis.config.actions.ClickType
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class MessageBroadcast(
    type: ActionType = ActionType.BROADCAST,
    click: List<ClickType> = listOf(ClickType.ANY),
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("message",  alternate = ["messages"])
    private val message: List<String> = emptyList()
) : Action(type, click, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, gui: ChestGUI) {
        if (GuisModule.INSTANCE.adventure == null) {
            Utils.printError("[ACTION - ${type.name}] There was an error while executing for player ${player.gameProfile.name}: Adventure is null")
            return
        }

        val parsedMessages = message.map { Utils.parsePlaceholders(player, it) }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Messages($parsedMessages): $this")

        for (line in parsedMessages) {
            GuisModule.INSTANCE.adventure!!.all().sendMessage(Utils.deserializeText(line))
        }
    }

    override fun toString(): String {
        return "MessageBroadcast(click=$click, delay=$delay, chance=$chance, requirements=$requirements, " +
                "message=$message)"
    }
}
