package com.f4xizzz.serverbuddy.kits.placeholders.services

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.kits.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

class PlaceholderAPIService : IPlaceholderService {
    init {
        Utils.printInfo("PlaceholderAPI mod found! Enabling placeholder integration...")
    }

    override fun registerPlayer(placeholder: PlayerPlaceholder) {
        placeholder.id().forEach { id ->
            // cada placeholder existe no namespace novo (serverbuddy_kits) e no antigo (skieskits)
            KitsModule.PLACEHOLDER_NAMESPACES.forEach { namespace ->
                Placeholders.register(ResourceLocation.fromNamespaceAndPath(namespace, id)) { ctx, arg ->
                    val player = ctx.player ?: return@register PlaceholderResult.invalid("NO PLAYER")
                    val args = arg?.split(":") ?: emptyList()
                    val result = placeholder.handle(player, args)
                    return@register if (result.isSuccessful) {
                        PlaceholderResult.value(KitsModule.INSTANCE.adventure.toNative(result.asComponent()))
                    } else {
                        PlaceholderResult.invalid(result.string)
                    }
                }
            }
        }
    }

    override fun registerServer(placeholder: ServerPlaceholder) {
        placeholder.id().forEach { id ->
            KitsModule.PLACEHOLDER_NAMESPACES.forEach { namespace ->
                Placeholders.register(ResourceLocation.fromNamespaceAndPath(namespace, id)) { _, arg ->
                    val args = arg?.split(":") ?: emptyList()
                    val result = placeholder.handle(args)
                    return@register if (result.isSuccessful) {
                        PlaceholderResult.value(KitsModule.INSTANCE.adventure.toNative(result.asComponent()))
                    } else {
                        PlaceholderResult.invalid(result.string)
                    }
                }
            }
        }
    }

    override fun finalizeRegister() {

    }

    override fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?): String {
        return Placeholders.parseText(Component.literal(text),
            PlaceholderContext.of(player)
        ).string
    }
}
