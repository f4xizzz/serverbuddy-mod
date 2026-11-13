package com.f4xizzz.serverbuddy.cobblemon.poke.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class SoundUtils {
   public static void playAnySound(ServerPlayer player, SoundConfig config) {
      if (player != null && config != null && config.sound_id != null && !config.sound_id.isEmpty()) {
         ResourceLocation soundId = ResourceLocation.parse(config.sound_id);
         SoundEvent soundEvent = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(soundId);
         if (soundEvent != null) {
            player.connection.send(new ClientboundSoundPacket(Holder.direct(soundEvent), SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), config.volume, config.pitch, player.getRandom().nextLong()));
         }

      }
   }
}
