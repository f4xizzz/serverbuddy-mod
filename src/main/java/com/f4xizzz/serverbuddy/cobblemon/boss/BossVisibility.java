package com.f4xizzz.serverbuddy.cobblemon.boss;

import net.minecraft.server.level.ServerPlayer;

public class BossVisibility {
   private BossVisibility() {
   }

   public static boolean isAllowed(BossDefinition boss, ServerPlayer player) {
      if (boss.visibilityMode == BossDefinition.VisibilityMode.NONE) {
         return true;
      } else if (boss.visibilityTag != null && !boss.visibilityTag.isBlank()) {
         boolean hasTag = player.getTags().contains(boss.visibilityTag);
         boolean var10000;
         switch (boss.visibilityMode) {
            case REQUIRE_TAG -> var10000 = hasTag;
            case BLOCK_TAG -> var10000 = !hasTag;
            case NONE -> var10000 = true;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      } else {
         return true;
      }
   }

   public static boolean canInteract(BossDefinition boss, ServerPlayer player) {
      return isAllowed(boss, player);
   }
}
