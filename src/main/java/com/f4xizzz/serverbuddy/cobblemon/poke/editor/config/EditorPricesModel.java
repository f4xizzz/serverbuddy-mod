package com.f4xizzz.serverbuddy.cobblemon.poke.editor.config;

import java.util.HashMap;
import java.util.Map;

public class EditorPricesModel {
   public double change_level_per_point = 100.0;
   public double change_iv_per_point = 100.0;
   public double change_ev_per_point = 100.0;
   public double change_friendship_per_point = 10.0;
   public double change_shiny = 5000.0;
   public double change_gender = 500.0;
   public double change_nature = 500.0;
   public double evolve = 1000.0;
   public double change_aspects = 500.0;
   public double change_ability_normal = 500.0;
   public double change_ability_hidden = 1000.0;
   public double change_ball_default_cost = 500.0;
   public Map<String, Double> custom_ball_costs = new HashMap<String, Double>() {
      {
         this.put("master_ball", 10000.0);
         this.put("cherish_ball", 10000.0);
         this.put("park_ball", 10000.0);
         this.put("beast_ball", 2000.0);
         this.put("safari_ball", 2000.0);
         this.put("sport_ball", 2000.0);
         this.put("ultra_ball", 1000.0);
         this.put("dusk_ball", 1000.0);
         this.put("quick_ball", 1000.0);
         this.put("timer_ball", 1000.0);
         this.put("fast_ball", 1000.0);
         this.put("level_ball", 1000.0);
         this.put("lure_ball", 1000.0);
         this.put("heavy_ball", 1000.0);
         this.put("love_ball", 1000.0);
         this.put("friend_ball", 1000.0);
         this.put("moon_ball", 1000.0);
         this.put("dream_ball", 1000.0);
         this.put("great_ball", 500.0);
         this.put("net_ball", 500.0);
         this.put("dive_ball", 500.0);
         this.put("nest_ball", 500.0);
         this.put("repeat_ball", 500.0);
         this.put("heal_ball", 500.0);
         this.put("luxury_ball", 500.0);
         this.put("premier_ball", 500.0);
         this.put("poke_ball", 100.0);
      }
   };
   public double change_mark_default_cost = 500.0;
   public Map<String, Double> custom_mark_costs = new HashMap<String, Double>() {
      {
         this.put("destiny", 5000.0);
         this.put("rare", 5000.0);
         this.put("time", 2000.0);
         this.put("weather", 2000.0);
         this.put("cloudy", 2000.0);
         this.put("dawn", 2000.0);
         this.put("dusk", 2000.0);
         this.put("uncommon", 1000.0);
         this.put("flustered", 1000.0);
         this.put("pumped", 1000.0);
      }
   };
}
