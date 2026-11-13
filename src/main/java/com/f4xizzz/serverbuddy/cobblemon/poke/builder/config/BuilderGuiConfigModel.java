package com.f4xizzz.serverbuddy.cobblemon.poke.builder.config;

public class BuilderGuiConfigModel {
   public Icons icons = new Icons();

   public static class Icons {
      public String background_primary = "minecraft:blue_stained_glass_pane";
      public String background_secondary = "minecraft:red_stained_glass_pane";
      public String background_dark = "minecraft:black_stained_glass_pane";
      public String background_light = "minecraft:light_gray_stained_glass_pane";
      public String close_button = "minecraft:barrier";
      public String empty_slot = "cobblemon:poke_ball";
      public String status_correct = "minecraft:lime_stained_glass_pane";
      public String status_partial = "minecraft:yellow_stained_glass_pane";
      public String status_incorrect = "minecraft:red_stained_glass_pane";
   }
}
