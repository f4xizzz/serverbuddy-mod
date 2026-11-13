package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public class CatchComboListener {
   public static void register() {
      CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (event) -> {
         handleCapture(event);
         return Unit.INSTANCE;
      });
      CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL, (event) -> {
         handleSpawn((PokemonEntity)event.getEntity());
         return Unit.INSTANCE;
      });
   }

   private static void handleCapture(PokemonCapturedEvent event) {
      ServerPlayer player = event.getPlayer();
      if (player != null) {
         CatchComboManager.registerCapture(player, event.getPokemon().getSpecies());
      }
   }

   private static void handleSpawn(PokemonEntity entity) {
      AABB searchArea = entity.getBoundingBox().inflate(48.0);

      for(ServerPlayer player : entity.level().getEntitiesOfClass(ServerPlayer.class, searchArea, (p) -> true)) {
         CatchComboManager.applySpawnBuffs(player, entity.getPokemon());
      }

   }
}
