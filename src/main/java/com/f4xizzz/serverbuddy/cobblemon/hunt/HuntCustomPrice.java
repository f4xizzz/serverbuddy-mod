package com.f4xizzz.serverbuddy.cobblemon.hunt;

public class HuntCustomPrice {
   public String especie = "magikarp";
   public String forma = "";
   public HuntReward recompensa = new HuntReward(500.0, new String[]{"give %player% minecraft:diamond 1"});

   public boolean matches(String especieId, String formaId) {
      if (!this.especie.trim().equalsIgnoreCase(especieId.trim())) {
         return false;
      } else {
         return this.forma.trim().isEmpty() || this.forma.trim().equalsIgnoreCase(formaId.trim());
      }
   }
}
