package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatchAreaEconomyConfig {
   public String currencyId = "impactor:coins";
   public double choiceEntryPrice = 500.0;
   public Map<String, Double> choiceEntryPricePermissions = new LinkedHashMap<>();
   public double randomEntryPrice = 200.0;
   public Map<String, Double> randomEntryPricePermissions = new LinkedHashMap<>();
   public List<DurationPackage> durationPackages = defaultDurationPackages();

   private static List<DurationPackage> defaultDurationPackages() {
      List<DurationPackage> list = new ArrayList<>();
      list.add(new DurationPackage("30min", "30 minutes", 30, 500.0));
      list.add(new DurationPackage("1h", "1 hour", 60, 900.0));
      list.add(new DurationPackage("2h", "2 hours", 120, 1600.0));
      return list;
   }

   public static class DurationPackage {
      public String id;
      public String label;
      public int minutes;
      public double price;

      public DurationPackage(String id, String label, int minutes, double price) {
         this.id = id;
         this.label = label;
         this.minutes = minutes;
         this.price = price;
      }
   }
}
