package com.f4xizzz.serverbuddy.cobblemon.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossCooldownManager {
   private static final Map<Key, Long> COOLDOWN_UNTIL_MILLIS = new HashMap<>();

   private BossCooldownManager() {
   }

   public static void recordVictory(UUID playerId, BossDefinition boss) {
      if (boss.battleCooldownSeconds > 0) {
         COOLDOWN_UNTIL_MILLIS.put(new Key(playerId, boss.id), System.currentTimeMillis() + (long)boss.battleCooldownSeconds * 1000L);
      }
   }

   public static long remainingSeconds(UUID playerId, BossDefinition boss) {
      Key key = new Key(playerId, boss.id);
      Long until = (Long)COOLDOWN_UNTIL_MILLIS.get(key);
      if (until == null) {
         return 0L;
      } else {
         long remainingMillis = until - System.currentTimeMillis();
         if (remainingMillis <= 0L) {
            COOLDOWN_UNTIL_MILLIS.remove(key);
            return 0L;
         } else {
            return (remainingMillis + 999L) / 1000L;
         }
      }
   }

   public static String formatDuration(long totalSeconds) {
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      StringBuilder sb = new StringBuilder();
      if (hours > 0L) {
         sb.append(hours).append("h");
      }

      if (hours > 0L || minutes > 0L) {
         sb.append(minutes).append("m");
      }

      sb.append(seconds).append("s");
      return sb.toString();
   }

   private static record Key(UUID playerId, String bossId) {
   }
}
