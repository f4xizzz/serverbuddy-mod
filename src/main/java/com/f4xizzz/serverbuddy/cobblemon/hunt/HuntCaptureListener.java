package com.f4xizzz.serverbuddy.cobblemon.hunt;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.UUID;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class HuntCaptureListener {
   private HuntCaptureListener() {
   }

   public static void register() {
      CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (event) -> {
         handleCapture(event);
         return Unit.INSTANCE;
      });
   }

   private static void handleCapture(PokemonCapturedEvent event) {
      ServerPlayer player = event.getPlayer();
      if (player != null) {
         Pokemon pokemon = event.getPokemon();
         HuntPool pool = HuntManager.getPoolFor(player);
         UUID matchedId = pool.matches(pokemon);
         if (matchedId != null) {
            HuntTarget target = pool.getTarget(matchedId);
            if (target != null) {
               MinecraftServer server = player.getServer();
               if (server != null) {
                  HuntManager.onCaptured(server, player, pool, target);
               }
            }
         }
      }
   }
}
