package com.f4xizzz.serverbuddy.util;

public class SoundSetting {
   public String soundId;
   public float volume;
   public float pitch;

   public SoundSetting() {
   }

   public SoundSetting(String soundId, float volume, float pitch) {
      this.soundId = soundId;
      this.volume = volume;
      this.pitch = pitch;
   }
}
