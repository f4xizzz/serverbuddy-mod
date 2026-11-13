package com.f4xizzz.serverbuddy.core

import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Placeholders do ServerBuddy no PlaceholderAPI (`%serverbuddy:nome%`), para qualquer mod de chat, scoreboard ou menu
 * usar. Cada módulo registra os seus aqui.
 */
object SbPlaceholders {
    /**
     * Registra `%serverbuddy:<id>%`. [resolver] recebe o jogador (pode ser null em contextos sem jogador) e o argumento
     * depois do `/` (`%serverbuddy:id/argumento%`) e devolve o texto, ou null se não há valor. O texto aceita MiniMessage.
     */
    @JvmStatic
    fun register(id: String, resolver: (ServerPlayer?, String?) -> String?) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath("serverbuddy", id)) { ctx, arg ->
            val text = resolver(ctx.player, arg)
            if (text == null) PlaceholderResult.invalid("no value") else PlaceholderResult.value(Texts.parse(text))
        }
    }
}
