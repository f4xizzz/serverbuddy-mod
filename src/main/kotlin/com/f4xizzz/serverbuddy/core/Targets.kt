package com.f4xizzz.serverbuddy.core

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Acha um jogador por nome (online ou conhecido pelo servidor) ou UUID, para os comandos de admin. */
object Targets {
    class Target(val uuid: UUID, val name: String, val online: ServerPlayer?)

    fun resolve(server: MinecraftServer, token: String): Target? {
        val text = token.trim()
        if (text.isEmpty()) return null

        server.playerList.getPlayerByName(text)?.let { return Target(it.uuid, it.gameProfile.name, it) }

        val parsed = runCatching { UUID.fromString(text) }.getOrNull()
        if (parsed != null) {
            val online = server.playerList.getPlayer(parsed)
            val name = online?.gameProfile?.name ?: server.profileCache?.get(parsed)?.orElse(null)?.name ?: text
            return Target(parsed, name, online)
        }

        val profile = server.profileCache?.get(text)?.orElse(null) ?: return null
        return Target(profile.id, profile.name, server.playerList.getPlayer(profile.id))
    }

    fun suggestions(source: CommandSourceStack, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(source.onlinePlayerNames, builder)
}
