package com.f4xizzz.serverbuddy.cobblemon.boss;

import java.util.ArrayList;
import java.util.List;

public class BossDefinition {
   public String id;
   public String entityUuid;
   public String speciesId;
   public String spawnQuery;
   public String nickname;
   public int level = 1;
   public float scale = 1.0F;
   public String world;
   public double x;
   public double y;
   public double z;
   public float yaw;
   public String requiredHeldItem;
   public String blockedIfPlayerHasTag;
   public List<String> victoryCommands = new ArrayList<>();
   public List<String> defeatCommands = new ArrayList<>();
   public List<String> lossCommands = new ArrayList<>();
   public float minCaptureHealthPercent = 0.1F;
   public int respawnDelaySeconds = 0;
   public int battleCooldownSeconds = 0;
   public boolean lookAtPlayer = true;
   public List<String> moves = new ArrayList<>();
   public List<DropEntry> drops = new ArrayList<>();
   public ItemRequirementMode requiredItemMode;
   public String requiredItemModeOptions;
   public VisibilityMode visibilityMode;
   public String visibilityModeOptions;
   public String visibilityTag;
   public VisibilityBehavior visibilityBehavior;
   public String visibilityBehaviorOptions;
   public BossDialogueDefinition blockedDialogue;
   public BossDialogueDefinition missingItemDialogue;

   public BossDefinition() {
      this.requiredItemMode = BossDefinition.ItemRequirementMode.HELD_ONLY;
      this.requiredItemModeOptions = "HELD_ONLY | INVENTORY_ANY";
      this.visibilityMode = BossDefinition.VisibilityMode.NONE;
      this.visibilityModeOptions = "NONE (or empty) | REQUIRE_TAG | BLOCK_TAG";
      this.visibilityBehavior = BossDefinition.VisibilityBehavior.BLOCK_INTERACTION_ONLY;
      this.visibilityBehaviorOptions = "BLOCK_INTERACTION_ONLY | HIDE_ENTITY";
   }

   public static class DropEntry {
      public String item;
      public float percentage = 100.0F;
      public int minQuantity = 1;
      public int maxQuantity = 1;
   }

   public static enum ItemRequirementMode {
      HELD_ONLY,
      INVENTORY_ANY;
   }

   public static enum VisibilityMode {
      NONE,
      REQUIRE_TAG,
      BLOCK_TAG;
   }

   public static enum VisibilityBehavior {
      BLOCK_INTERACTION_ONLY,
      HIDE_ENTITY;
   }
}
