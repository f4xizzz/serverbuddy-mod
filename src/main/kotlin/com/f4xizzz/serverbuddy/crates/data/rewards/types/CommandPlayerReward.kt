package com.f4xizzz.serverbuddy.crates.data.rewards.types

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.item.GenericItem
import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardLimits
import com.f4xizzz.serverbuddy.crates.data.rewards.RewardType
import com.f4xizzz.serverbuddy.crates.data.rewards.types.CommandConsoleReward.Companion.DEFAULT_DISPLAY
import com.f4xizzz.serverbuddy.crates.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.crates.utils.FlexibleListAdaptorFactory
import net.minecraft.server.level.ServerPlayer

class CommandPlayerReward(
    name: String = "",
    description: List<String> = emptyList(),
    display: GenericItem? = null,
    weight: Int = 1,
    limits: RewardLimits? = null,
    broadcast: Boolean = false,
    @JsonAdapter(FlexibleListAdaptorFactory::class) @SerializedName("commands",  alternate = ["command"])
    private val commands: List<String> = emptyList(),
    @SerializedName("permission_level")
    private val permissionLevel: Int? = null
) : Reward(RewardType.COMMAND_PLAYER, name, description, display, weight, limits, broadcast) {
    override fun giveReward(player: ServerPlayer, crate: Crate) {
        // Super to call the message
        super.giveReward(player, crate)

        val parsedCommands = commands.map { PlaceholderManager.parse(player, it) }

        var source = player.createCommandSourceStack()
        if (permissionLevel != null) {
            source = source.withPermission(permissionLevel)
        }

        for (command in parsedCommands) {
            CratesModule.INSTANCE.server.commands.performPrefixedCommand(
                source,
                command
            )
        }
    }

    override fun getGenericDisplay(): GenericItem {
        return display ?: DEFAULT_DISPLAY
    }

    override fun toString(): String {
        return "CommandPlayer(type=$type, name='$name', display=$display, weight=$weight, limits=$limits, broadcast=$broadcast, commands=$commands, permissionLevel=$permissionLevel)"
    }
}
