package com.f4xizzz.serverbuddy.announcements.placeholders.services

import com.f4xizzz.serverbuddy.announcements.AnnouncementsModule
import com.f4xizzz.serverbuddy.announcements.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minecraft.server.level.ServerPlayer

class MiniPlaceholdersService : IPlaceholderService {
    private val miniMessage = MiniMessage.builder()
        .tags(TagResolver.builder().build())
        .build()

    init {
        Utils.printInfo("MiniPlaceholders mod found! Enabling placeholder integration...")
    }

    override fun parsePlaceholders(player: ServerPlayer, text: String): String {
        val resolver = TagResolver.resolver(
            MiniPlaceholders.getGlobalPlaceholders(),
            MiniPlaceholders.getAudiencePlaceholders(player)
        )

        return AnnouncementsModule.INSTANCE.adventure!!.toNative(
            miniMessage.deserialize(text, resolver)
        ).string
    }
}
