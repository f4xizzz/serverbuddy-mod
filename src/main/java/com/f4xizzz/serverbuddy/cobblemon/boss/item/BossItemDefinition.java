package com.f4xizzz.serverbuddy.cobblemon.boss.item;

import java.util.ArrayList;
import java.util.List;

public class BossItemDefinition {
   public String name;
   public String baseItem = "minecraft:paper";
   public int customModelData = 0;
   public String displayName;
   public List<String> description = new ArrayList<>();
   public boolean glowing = false;
   public String grantsTag;
   public TagMode tagMode;

   public BossItemDefinition() {
      this.tagMode = BossItemDefinition.TagMode.HELD_ONLY;
   }

   public static enum TagMode {
      HELD_ONLY,
      INVENTORY_ANY;
   }
}
