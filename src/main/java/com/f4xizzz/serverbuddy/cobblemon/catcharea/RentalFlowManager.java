package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.pokemon.Species;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RentalFlowManager {
   private static final Map<UUID, PendingRental> PENDING = new HashMap<>();

   private RentalFlowManager() {
   }

   public static PendingRental getOrCreate(UUID playerId) {
      return (PendingRental)PENDING.computeIfAbsent(playerId, (id) -> new PendingRental());
   }

   public static void clear(UUID playerId) {
      PENDING.remove(playerId);
   }

   public static class PendingRental {
      public String areaName;
      public boolean choiceMode;
      public Species chosenSpecies;
      public Species baseSpecies;
      public CatchAreaEconomyConfig.DurationPackage duration;

      public void reset() {
         this.areaName = null;
         this.choiceMode = false;
         this.chosenSpecies = null;
         this.baseSpecies = null;
         this.duration = null;
      }
   }
}
