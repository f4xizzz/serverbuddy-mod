package com.f4xizzz.serverbuddy.shop.data.items.actions.types.internal

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.data.items.actions.ActionType
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.shop.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CommandConsole(
    click: List<GenericClickType> = listOf(GenericClickType.ANY),
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("commands",  alternate = ["command"])
    private val commands: List<String> = emptyList()
) : Action(ActionType.COMMAND_CONSOLE, click) {
    override fun executeAction(player: ServerPlayer, gui: IRefreshableGui) {
        val parsedCommands = commands.map { PlaceholderManager.parse(player, it) }

        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}), Parsed Commands($parsedCommands): $this")

        for (command in parsedCommands) {
            ShopModule.INSTANCE.server.commands.performPrefixedCommand(
                ShopModule.INSTANCE.server.createCommandSourceStack(),
                command
            )
        }
    }

    override fun toString(): String {
        return "CommandConsole(click=$click, commands=$commands)"
    }
}
