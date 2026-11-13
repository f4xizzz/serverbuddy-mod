package com.f4xizzz.serverbuddy.core

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Um som da config. Pode ser só o nome (`"minecraft:entity.player.levelup"`) ou `{ "sound": "...", "volume": 1.0, "pitch": 1.0 }`.
 * Aceita também sons de resource packs (o jogador só ouve se o pacote dele tiver o som).
 */
@JsonAdapter(SoundSpecAdapter::class)
class SoundSpec(val sound: String = "", val volume: Float = 1f, val pitch: Float = 1f) {
    /** Toca só para [player], na posição dele. */
    fun play(player: ServerPlayer) {
        val id = ResourceLocation.tryParse(sound.trim()) ?: return
        val event = SoundEvent.createVariableRangeEvent(id)
        player.connection.send(
            ClientboundSoundPacket(Holder.direct(event), SoundSource.MASTER, player.x, player.y, player.z, volume, pitch, player.level().random.nextLong())
        )
    }
}

class SoundSpecAdapter : TypeAdapter<SoundSpec>() {
    override fun write(out: JsonWriter, value: SoundSpec?) {
        if (value == null) {
            out.nullValue()
            return
        }
        if (value.volume == 1f && value.pitch == 1f) {
            out.value(value.sound)
            return
        }
        out.beginObject()
        out.name("sound").value(value.sound)
        out.name("volume").value(value.volume.toDouble())
        out.name("pitch").value(value.pitch.toDouble())
        out.endObject()
    }

    override fun read(reader: JsonReader): SoundSpec? {
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                return null
            }
            JsonToken.STRING -> return SoundSpec(reader.nextString())
            else -> {
                var sound = ""
                var volume = 1f
                var pitch = 1f
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "sound" -> sound = reader.nextString()
                        "volume" -> volume = reader.nextDouble().toFloat()
                        "pitch" -> pitch = reader.nextDouble().toFloat()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                return SoundSpec(sound, volume, pitch)
            }
        }
    }
}
