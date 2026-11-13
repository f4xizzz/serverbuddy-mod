package com.f4xizzz.serverbuddy.announcements.placeholders.services

import com.f4xizzz.serverbuddy.announcements.placeholders.IPlaceholderService
import net.minecraft.server.level.ServerPlayer

class DefaultPlaceholderService : IPlaceholderService {
    override fun parsePlaceholders(player: ServerPlayer, text: String): String {
        return text.replace("%player%", player.name.string)
    }
}
