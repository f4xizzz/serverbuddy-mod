package com.f4xizzz.serverbuddy.kits.placeholders.services

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.kits.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.impactdev.impactor.api.platform.players.PlatformPlayer
import net.impactdev.impactor.api.platform.sources.PlatformSource
import net.impactdev.impactor.api.text.TextProcessor
import net.impactdev.impactor.api.utility.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minecraft.server.level.ServerPlayer

/*
    This class will parse Impactor based placeholders if the mod is present. Out of the box, Impactor will
    process color codes, which breaks the compatibility with parsing placeholders using other mods.
    A custom MiniMessage is is created with an empty TagResolver list, that way it will not process
    any color codes and will just pass a string back with only Impactor placeholders processed.
 */
class ImpactorPlaceholderService : IPlaceholderService {
    private val processor = TextProcessor.mini(
        MiniMessage.builder()
            .tags(TagResolver.builder().build())
            .build()
    )

    init {
        Utils.printInfo("Impactor mod found! Enabling placeholder integration...")
    }
    override fun registerPlayer(placeholder: PlayerPlaceholder) {}
    override fun registerServer(placeholder: ServerPlaceholder) {}
    override fun finalizeRegister() {}

    override fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?): String {
        val platformPlayer = PlatformPlayer.getOrCreate(player.uuid)
        return KitsModule.INSTANCE.adventure.toNative(
            processor.parse(platformPlayer, text, Context().append(PlatformSource::class.java, platformPlayer))
        ).string
    }
}
