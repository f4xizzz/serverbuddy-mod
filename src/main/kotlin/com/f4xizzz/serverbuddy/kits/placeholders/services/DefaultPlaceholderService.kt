package com.f4xizzz.serverbuddy.kits.placeholders.services

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.kits.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class DefaultPlaceholderService : IPlaceholderService {
    override fun registerPlayer(placeholder: PlayerPlaceholder) {}
    override fun registerServer(placeholder: ServerPlaceholder) {}
    override fun finalizeRegister() {}

    override fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?): String {
        var returnText = text
            .replace("%player%", player.name.string)

        if (kitId != null) {
            returnText = returnText
                .replace("%kit_id%", kitId)

            if (kit != null) {
                returnText = returnText
                    .replace("%kit_name%", kit.getDisplayName(kitId))
                    .replace("%kit_max_uses%", kit.maxUses.toString())
                    .replace("%kit_cooldown%", Utils.getFormattedTime(kit.cooldown))

                if (kitData != null) {
                    returnText = returnText
                        .replace("%kit_uses%", kitData.uses.toString())
                        .replace("%kit_time_remaining%", Utils.getFormattedTime(kitData.getTimeRemaining(kit.cooldown)))
                }
            }
        }

        return returnText
    }
}
