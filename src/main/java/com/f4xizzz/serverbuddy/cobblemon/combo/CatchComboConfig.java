package com.f4xizzz.serverbuddy.cobblemon.combo;

import java.util.ArrayList;
import java.util.List;

public class CatchComboConfig {
   public boolean enabled = true;
   public int actionBarStartAt = 5;
   public String actionBarMessage = "&e%species% &7combo: &a%combo%x";
   public List<ComboTier> tiers = defaultTiers();

   private static List<ComboTier> defaultTiers() {
      List<ComboTier> list = new ArrayList<>();
      list.add(new ComboTier(5, 0, 0.05, 0.001, 0.05));
      list.add(new ComboTier(10, 1, 0.1, 0.003, 0.1));
      list.add(new ComboTier(20, 2, 0.2, 0.01, 0.2));
      list.add(new ComboTier(31, 3, 0.35, 0.02, 0.35));
      return list;
   }

   public static class ComboTier {
      public int combo;
      public int guaranteedPerfectIvs;
      public double chancePerfectIv;
      public double shinyChance;
      public double hiddenAbilityChance;

      public ComboTier(int combo, int guaranteedPerfectIvs, double chancePerfectIv, double shinyChance, double hiddenAbilityChance) {
         this.combo = combo;
         this.guaranteedPerfectIvs = guaranteedPerfectIvs;
         this.chancePerfectIv = chancePerfectIv;
         this.shinyChance = shinyChance;
         this.hiddenAbilityChance = hiddenAbilityChance;
      }
   }
}
