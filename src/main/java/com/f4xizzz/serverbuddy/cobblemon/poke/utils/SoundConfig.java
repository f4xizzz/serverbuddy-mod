package com.f4xizzz.serverbuddy.cobblemon.poke.utils;

public class SoundConfig {
   public String sound_id;
   public float volume;
   public float pitch;

   public SoundConfig() {
   }

   public SoundConfig(String sound_id, float volume, float pitch) {
      this.sound_id = sound_id;
      this.volume = volume;
      this.pitch = pitch;
   }
}
