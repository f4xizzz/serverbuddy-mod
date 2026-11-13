package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class BossEvents {
   private BossEvents() {
   }

   public static void register() {
      ServerLifecycleEvents.SERVER_STARTING.register((ServerLifecycleEvents.ServerStarting)(server) -> BossEntityTracker.reset());
      ServerEntityEvents.ENTITY_LOAD.register((ServerEntityEvents.Load)(entity, world) -> {
         if (entity instanceof PokemonEntity pokemonEntity) {
            BossEntityTracker.onEntityLoad(pokemonEntity);
         }

      });
   }
}
