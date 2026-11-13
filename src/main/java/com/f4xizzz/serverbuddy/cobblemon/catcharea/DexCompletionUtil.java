package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.CaughtPercent;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.server.level.ServerPlayer;

public class DexCompletionUtil {
   private DexCompletionUtil() {
   }

   public static float getCaughtPercent(ServerPlayer player) {
      try {
         PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
         Float value = CaughtPercent.INSTANCE.calculate(pokedex);
         if (value == null) {
            return 0.0F;
         } else {
            // the calculator reports percent units (0.195 means 0.195%) when it says so
            return CaughtPercent.INSTANCE.getOutputIsPercentage() ? value : value * 100.0F;
         }
      } catch (Exception var3) {
         return 0.0F;
      }
   }

   public static boolean hasFullDex(ServerPlayer player) {
      return getCaughtPercent(player) >= 99.9F;
   }

   public static boolean canUseChoiceMode(ServerPlayer player) {
      return hasFullDex(player) || player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.catcharea.choice", false);
   }
}
