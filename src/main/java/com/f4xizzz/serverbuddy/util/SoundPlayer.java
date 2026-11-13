package com.f4xizzz.serverbuddy.util;

import com.f4xizzz.serverbuddy.ServerBuddy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class SoundPlayer {
   private SoundPlayer() {
   }

   public static void play(ServerPlayer player, SoundSetting sound) {
      if (player != null && sound != null && sound.soundId != null && !sound.soundId.isEmpty()) {
         try {
            SoundEvent event = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(sound.soundId));
            if (event != null) {
               player.playNotifySound(event, SoundSource.MASTER, sound.volume, sound.pitch);
            } else {
               ServerBuddy.LOGGER.warn("Sound '{}' not found in the registry (check the id in the .conf).", sound.soundId);
            }
         } catch (Exception e) {
            ServerBuddy.LOGGER.warn("Could not play sound '{}': {}", sound.soundId, e.getMessage());
         }

      }
   }
}
