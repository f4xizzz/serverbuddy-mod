package com.f4xizzz.serverbuddy.crates.placeholders.services

import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.crates.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.crates.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.crates.utils.Utils
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

class PlaceholderAPIService : IPlaceholderService {
    init {
        Utils.printInfo("PlaceholderAPI mod found! Enabling placeholder integration...")
    }

    override fun parsePlaceholders(player: ServerPlayer, text: String): String {
        return Placeholders.parseText(Component.literal(text), PlaceholderContext.of(player)).string
    }

    override fun registerPlayer(placeholder: PlayerPlaceholder) {
        // cada placeholder existe no namespace novo (serverbuddy_crates) e no antigo (skiescrates)
        CratesModule.PLACEHOLDER_NAMESPACES.forEach { namespace ->
            Placeholders.register(ResourceLocation.fromNamespaceAndPath(namespace, placeholder.id())) { ctx, arg ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("NO PLAYER")
                val result = placeholder.handle(player, arg?.split(":") ?: emptyList())
                return@register if (result.isSuccessful) {
                    PlaceholderResult.value(CratesModule.INSTANCE.adventure.toNative(result.result))
                } else {
                    PlaceholderResult.invalid(PlainTextComponentSerializer.plainText().serialize(result.result))
                }
            }
        }
    }

    override fun registerServer(placeholder: ServerPlaceholder) {
        CratesModule.PLACEHOLDER_NAMESPACES.forEach { namespace ->
            Placeholders.register(ResourceLocation.fromNamespaceAndPath(namespace, placeholder.id())) { ctx, arg ->
                val result = placeholder.handle(arg?.split(":") ?: emptyList())
                return@register if (result.isSuccessful) {
                    PlaceholderResult.value(CratesModule.INSTANCE.adventure.toNative(result.result))
                } else {
                    PlaceholderResult.invalid(PlainTextComponentSerializer.plainText().serialize(result.result))
                }
            }
        }
    }

    override fun finalizeRegister() {

    }
}
