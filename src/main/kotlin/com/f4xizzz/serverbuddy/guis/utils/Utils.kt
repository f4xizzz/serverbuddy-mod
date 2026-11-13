package com.f4xizzz.serverbuddy.guis.utils

import com.google.gson.*
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.f4xizzz.serverbuddy.guis.GuisModule
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.minecraft.core.Registry
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Type

object Utils {
    val miniMessage: MiniMessage = MiniMessage.miniMessage()

    fun parsePlaceholders(player: ServerPlayer, text: String): String {
        return GuisModule.INSTANCE.placeholderManager.parse(player, text)
    }

    fun deserializeText(text: String): Component {
        return GuisModule.INSTANCE.adventure!!.toNative(
            miniMessage.deserialize(text)
                .applyFallbackStyle({it.decoration(TextDecoration.ITALIC, false)})
        )
    }

    fun printDebug(message: String, bypassCheck: Boolean = false) {
        if (bypassCheck || GuisModule.INSTANCE.configManager.config.debug)
            GuisModule.LOGGER.info("[GUIs] DEBUG: $message")
    }

    fun printError(message: String) {
        GuisModule.LOGGER.error("[GUIs] ERROR: $message")
    }

    fun printInfo(message: String) {
        GuisModule.LOGGER.info("[GUIs] $message")
    }

    // Thank you to Patbox for these wonderful serializers =)
    data class RegistrySerializer<T>(val registry: Registry<T>) : JsonSerializer<T>, JsonDeserializer<T> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T? {
            var parsed = if (json.isJsonPrimitive) registry.get(ResourceLocation.parse(json.asString)) else null
            if (parsed == null)
                printError("There was an error while deserializing a Registry Type: $registry")
            return parsed
        }

        override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(registry.getId(src).toString())
        }
    }

    data class CodecSerializer<T>(val codec: Codec<T>) : JsonSerializer<T>, JsonDeserializer<T> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): T? {
            return try {
                codec.decode(JsonOps.INSTANCE, json).orThrow.first
            } catch (e: Throwable) {
                printError("There was an error while deserializing a Codec: $codec")
                null
            }
        }

        override fun serialize(src: T?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return try {
                if (src != null)
                    codec.encodeStart(JsonOps.INSTANCE, src).orThrow
                else
                    JsonNull.INSTANCE
            } catch (e: Throwable) {
                printError("There was an error while serializing a Codec: $codec")
                JsonNull.INSTANCE
            }
        }
    }
}

fun net.kyori.adventure.text.Component.toNative(): Component {
    return Component.empty().setStyle(Style.EMPTY.withItalic(false))
        .append(GuisModule.INSTANCE.adventure!!.toNative(this))
}

fun String.parseMiniMessage(vararg placeholders: TagResolver): net.kyori.adventure.text.Component {
    return Utils.miniMessage.deserialize(this, *placeholders)
}

fun String.parseToNative(vararg placeholders: TagResolver): Component {
    return this.parseMiniMessage(*placeholders).toNative()
}
