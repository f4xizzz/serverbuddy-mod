package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.f4xizzz.serverbuddy.ServerBuddy;

public class BossDebug {
   private static volatile boolean enabled = true;

   private BossDebug() {
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static boolean toggle() {
      enabled = !enabled;
      return enabled;
   }

   public static void log(String format, Object... args) {
      if (enabled) {
         ServerBuddy.LOGGER.info(format, args);
      }

   }
}
