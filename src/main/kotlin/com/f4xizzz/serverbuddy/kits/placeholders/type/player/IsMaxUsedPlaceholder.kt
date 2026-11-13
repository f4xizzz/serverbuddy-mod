package com.f4xizzz.serverbuddy.kits.placeholders.type.player

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.GenericResult
import com.f4xizzz.serverbuddy.kits.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class IsMaxUsedPlaceholder : PlayerPlaceholder {
    override fun handle(player: ServerPlayer, args: List<String>): GenericResult {
        if (args.isEmpty()) {
            return GenericResult.invalid("No Kit Specified")
        }

        val kitId = args[0]
        val kit = ConfigManager.KITS[kitId] ?: return GenericResult.invalid("Kit Not Found")

        if (KitsModule.INSTANCE.storage == null) {
            Utils.printError("Returned player data is null! Cannot claim kit $kitId for player ${player.name.string}!")
            return GenericResult.invalid("Storage Error")
        }

        val userdata = KitsModule.INSTANCE.storage?.getUser(player.uuid)
        if (userdata == null) {
            Utils.printError("Userdata for ${player.uuid} not found when processing placeholder. This should not happen.")
            return GenericResult.invalid("Storage Error")
        }

        val kitData = if (userdata.kits.containsKey(kitId)) userdata.kits[kitId]!! else KitData()

        return GenericResult.valid(!kitData.canClaimUses(kit.maxUses))
    }

    override fun id(): List<String> = listOf("is_max_used")
}
