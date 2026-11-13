package com.f4xizzz.serverbuddy.crates.data.rewards.types

import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.item.GenericItem
import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardLimits
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardType
import com.f4xizzz.serverbuddy.crates.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.crates.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CommandConsoleReward(
    name: String = "",
    description: List<String> = emptyList(),
    weight: Int = 1,
    display: GenericItem? = null,
    limits: RewardLimits? = null,
    broadcast: Boolean = false,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    private val commands: List<String> = emptyList()
) : Reward(RewardType.COMMAND_CONSOLE, name, description, display, weight, limits, broadcast) {
    companion object {
        val DEFAULT_DISPLAY = GenericItem("minecraft:paper", name = "Command")
    }

    override fun giveReward(player: ServerPlayer, crate: Crate) {
        // Super to call the message
        super.giveReward(player, crate)

        if (CratesModule.INSTANCE.server.commands == null) {
            Utils.printError("There was an error while giving a reward for player ${player.name}: Server was somehow null on command execution?")
            return
        }

        for (command in commands) {
            CratesModule.INSTANCE.server.commands.performPrefixedCommand(
                CratesModule.INSTANCE.server.createCommandSourceStack(),
                PlaceholderManager.parse(player, command)
            )
        }
    }

    override fun getGenericDisplay(): GenericItem {
        return display ?: DEFAULT_DISPLAY
    }

    override fun toString(): String {
        return "CommandConsole(type=$type, name='$name', display=$display, weight=$weight, limits=$limits, broadcast=$broadcast, commands=$commands)"
    }
}
