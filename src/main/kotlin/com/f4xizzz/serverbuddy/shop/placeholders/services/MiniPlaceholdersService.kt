package com.f4xizzz.serverbuddy.shop.placeholders.services

import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.shop.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.shop.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.shop.utils.Utils
import io.github.miniplaceholders.api.Expansion
import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minecraft.server.level.ServerPlayer

class MiniPlaceholdersService : IPlaceholderService {
    // um Expansion por namespace: o novo (serverbuddy_shop) e o do SkiesShop (skiesshop)
    private val builders = ShopModule.PLACEHOLDER_NAMESPACES.map { Expansion.builder(it) }
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

        return ShopModule.INSTANCE.adventure.toNative(
            miniMessage.deserialize(text, resolver)
        ).string
    }

    override fun registerPlayer(placeholder: PlayerPlaceholder) {
        builders.forEach { builder ->
            builder.filter(ServerPlayer::class.java)
                .audiencePlaceholder(placeholder.id()) { audience, queue, _ ->
                    val arguments: MutableList<String> = mutableListOf()
                    while (queue.peek() != null) {
                        arguments.add(queue.pop().toString())
                    }
                    return@audiencePlaceholder Tag.inserting(placeholder.handle(audience as ServerPlayer, arguments).result)
                }
        }
    }

    override fun registerServer(placeholder: ServerPlaceholder) {
        builders.forEach { builder ->
            builder.globalPlaceholder(placeholder.id()) { queue, _ ->
                val arguments: MutableList<String> = mutableListOf()
                while (queue.peek() != null) {
                    arguments.add(queue.pop().toString())
                }
                return@globalPlaceholder Tag.inserting(placeholder.handle(arguments).result)
            }
        }
    }

    override fun finalizeRegister() {
        builders.forEach { it.build().register() }
    }
}
