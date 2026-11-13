package com.f4xizzz.serverbuddy.shop.placeholders.services

import com.f4xizzz.serverbuddy.shop.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.shop.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.shop.placeholders.ServerPlaceholder
import net.minecraft.server.level.ServerPlayer

class DefaultPlaceholderService : IPlaceholderService {
    override fun parsePlaceholders(player: ServerPlayer, text: String): String {
        return text
            .replace("%player%", player.name.string)
            .replace("%player_uuid%", player.uuid.toString())
    }

    override fun registerPlayer(placeholder: PlayerPlaceholder) {

    }

    override fun registerServer(placeholder: ServerPlaceholder) {

    }

    override fun finalizeRegister() {

    }
}
