package com.f4xizzz.serverbuddy.kits.placeholders.services

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.IPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.PlayerPlaceholder
import com.f4xizzz.serverbuddy.kits.placeholders.ServerPlaceholder
import com.f4xizzz.serverbuddy.kits.utils.Utils
import io.github.miniplaceholders.api.Expansion
import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minecraft.server.level.ServerPlayer

class MiniPlaceholdersService : IPlaceholderService {
    // um Expansion por namespace: o novo (serverbuddy_kits) e o do SkiesKits (skieskits)
    private val builders = KitsModule.PLACEHOLDER_NAMESPACES.map { Expansion.builder(it) }
    private val miniMessage = MiniMessage.builder()
        .tags(TagResolver.builder().build())
        .build()

    init {
        Utils.printInfo("MiniPlaceholders mod found! Enabling placeholder integration...")
    }

    override fun registerPlayer(placeholder: PlayerPlaceholder) {
        placeholder.id().forEach { id ->
            builders.forEach { builder ->
                builder.filter(ServerPlayer::class.java)
                    .audiencePlaceholder(id) { audience, queue, _ ->
                        val player = audience as ServerPlayer
                        val arguments: MutableList<String> = mutableListOf()
                        while (queue.peek() != null) {
                            arguments.add(queue.pop().toString())
                        }
                        return@audiencePlaceholder Tag.preProcessParsed(placeholder.handle(player, arguments).string)
                    }
            }
        }
    }

    override fun registerServer(placeholder: ServerPlaceholder) {
        placeholder.id().forEach { id ->
            builders.forEach { builder ->
                builder.globalPlaceholder(id) { queue, _ ->
                    val arguments: MutableList<String> = mutableListOf()
                    while (queue.peek() != null) {
                        arguments.add(queue.pop().toString())
                    }
                    return@globalPlaceholder Tag.preProcessParsed(placeholder.handle(arguments).string)
                }.audiencePlaceholder(id) { audience, queue, _ ->
                    if (audience !is ServerPlayer) return@audiencePlaceholder Tag.inserting(Component.empty())
                    val arguments: MutableList<String> = mutableListOf()
                    while (queue.peek() != null) {
                        arguments.add(queue.pop().toString())
                    }
                    return@audiencePlaceholder Tag.preProcessParsed(placeholder.handle(arguments).string)
                }
            }
        }
    }

    override fun finalizeRegister() {
        builders.forEach { it.build().register() }
    }

    override fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?): String {
        val resolvers = mutableListOf(MiniPlaceholders.getGlobalPlaceholders())
        player.let { resolvers.add(MiniPlaceholders.getAudiencePlaceholders(it)) }
        val resolver = TagResolver.resolver(*resolvers.toTypedArray())

        return KitsModule.INSTANCE.adventure.toNative(
            miniMessage.deserialize(text, resolver)
        ).string
    }
}
