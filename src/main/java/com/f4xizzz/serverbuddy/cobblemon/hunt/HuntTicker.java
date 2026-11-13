package com.f4xizzz.serverbuddy.cobblemon.hunt;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class HuntTicker {
   private HuntTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(HuntTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 20 == 0) {
         for(HuntPool pool : HuntManager.getAllPools()) {
            List<HuntTarget> expired = new ArrayList<>();

            for(HuntTarget target : pool.getTargets().values()) {
               if (target.isExpired()) {
                  expired.add(target);
               }
            }

            if (!expired.isEmpty()) {
               HuntManager.onExpiredBatch(server, pool, expired);
            }
         }

      }
   }
}
