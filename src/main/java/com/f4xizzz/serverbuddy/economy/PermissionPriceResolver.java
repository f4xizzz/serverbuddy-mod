package com.f4xizzz.serverbuddy.economy;

import java.util.Map;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.server.level.ServerPlayer;

public class PermissionPriceResolver {
   private PermissionPriceResolver() {
   }

   public static double resolve(ServerPlayer player, double basePrice, Map<String, Double> permissionPrices) {
      double cheapest = basePrice;
      if (permissionPrices != null) {
         for(Map.Entry<String, Double> entry : permissionPrices.entrySet()) {
            if (entry.getValue() != null && SbPermissions.check(player, (String)entry.getKey(), false) && (Double)entry.getValue() < cheapest) {
               cheapest = (Double)entry.getValue();
            }
         }
      }

      return cheapest;
   }
}
