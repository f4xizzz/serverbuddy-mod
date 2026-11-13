package com.f4xizzz.serverbuddy.cobblemon.events;

import com.cobblemon.mod.common.Cobblemon;
import com.f4xizzz.serverbuddy.ServerBuddy;

/** Troca a taxa de Shiny do Cobblemon enquanto um evento esta ativo (so e usada quando o Cobblemon esta instalado). */
public final class ShinyRateController {
   private static float defaultShinyRate = 8192.0F;
   private static boolean defaultRateCaptured = false;
   private static boolean eventApplying = false;

   /**
    * Troca a taxa de Shiny padrao (usada por /pokeodds setRate). Se um evento esta mudando a taxa agora, a nova taxa
    * so vale quando ele acabar. Devolve true se ha um evento ativo.
    */
   public static boolean setBaseRate(float rate) {
      defaultShinyRate = rate;
      defaultRateCaptured = true;
      if (!eventApplying) {
         try {
            Cobblemon.config.setShinyRate(rate);
         } catch (Exception e) {
            ServerBuddy.LOGGER.error("[Events] Could not set the Shiny rate in Cobblemon!", e);
         }
      }

      return eventApplying;
   }

   private ShinyRateController() {
   }

   /** Guarda a taxa original, uma unica vez, para poder devolver quando o evento acabar. */
   public static void captureDefaultOnce() {
      if (!defaultRateCaptured) {
         try {
            defaultShinyRate = Cobblemon.config.getShinyRate();
         } catch (Exception ignored) {
         }

         defaultRateCaptured = true;
      }
   }

   public static void apply(boolean anyEventActive, float targetShinyRate) {
      eventApplying = anyEventActive && targetShinyRate > 0.0F;

      try {
         if (anyEventActive && targetShinyRate > 0.0F) {
            if (Cobblemon.config.getShinyRate() != targetShinyRate) {
               Cobblemon.config.setShinyRate(targetShinyRate);
               ServerBuddy.LOGGER.info("[Events] Cobblemon Shiny rate changed to: 1/{}", targetShinyRate);
            }
         } else if (Cobblemon.config.getShinyRate() != defaultShinyRate) {
            Cobblemon.config.setShinyRate(defaultShinyRate);
            ServerBuddy.LOGGER.info("[Events] Events ended. Shiny rate went back to the default: 1/{}", defaultShinyRate);
         }
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("[Events] Could not apply the Shiny rate in Cobblemon!", e);
      }

   }
}
