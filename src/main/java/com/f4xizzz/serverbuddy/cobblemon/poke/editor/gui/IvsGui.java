package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class IvsGui {
   private static final List<Stat> ALL_STATS;

   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      openInternal(player, originalPokemon, editingPokemon, isDevMode, false);
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, boolean usedMightyCandy) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      ItemStack bgBlue = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_primary : null, Items.BLUE_STAINED_GLASS_PANE));
      bgBlue.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlue = GooeyButton.builder().display(bgBlue).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgRed = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_secondary : null, Items.RED_STAINED_GLASS_PANE));
      bgRed.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(bgRed).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_dark : null, Items.BLACK_STAINED_GLASS_PANE));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgWhite = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_white : null, Items.WHITE_STAINED_GLASS_PANE));
      bgWhite.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(bgWhite).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 12 && i != 13 && i != 14 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7 && i != 10 && i != 11 && i != 15 && i != 16) {
               if ((i < 18 || i > 26) && (i < 45 || i > 53 || i == 49)) {
                  if (i >= 27 && i <= 44) {
                     template.set(i, btnWhite);
                  }
               } else {
                  template.set(i, btnBlack);
               }
            } else {
               template.set(i, btnRed);
            }
         } else {
            template.set(i, btnBlue);
         }
      }

      ItemStack originalModel = PokemonItem.from(originalPokemon);
      originalModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String origTitle = EditorConfigManager.lang.ivs_original_title != null ? EditorConfigManager.lang.ivs_original_title : "<white>Original IVs</white>";
      template.set(10, GooeyButton.builder().display(originalModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(origTitle)).with(DataComponents.LORE, getIvTooltip(originalPokemon)).build());
      ItemStack editedModel = PokemonItem.from(editingPokemon);
      editedModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String prevTitle = EditorConfigManager.lang.ivs_preview_title != null ? EditorConfigManager.lang.ivs_preview_title : "<gold>Preview</gold>";
      template.set(19, GooeyButton.builder().display(editedModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevTitle)).with(DataComponents.LORE, getIvTooltip(editingPokemon)).build());
      boolean hasChanges = hasIvModifications(originalPokemon, editingPokemon);
      String confirmBallId = hasChanges ? EditorConfigManager.guiconfig.icons.status_changed : EditorConfigManager.guiconfig.icons.status_unchanged;
      ItemStack confirmBall = new ItemStack(getSafeItem(confirmBallId, hasChanges ? Items.LIME_DYE : Items.GRAY_DYE));
      confirmBall.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      List<Component> saveLore = new ArrayList<>();
      if (hasChanges) {
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore1 != null ? EditorConfigManager.lang.evs_save_lore1 : "<gray>Charges will only be made</gray>"));
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore2 != null ? EditorConfigManager.lang.evs_save_lore2 : "<gray>in the final confirmation menu.</gray>"));
         saveLore.add(Component.empty());
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore3 != null ? EditorConfigManager.lang.evs_save_lore3 : "<yellow>Click to save!</yellow>"));
      } else {
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore_unchanged != null ? EditorConfigManager.lang.evs_save_lore_unchanged : "<gray>Change a value to save.</gray>"));
      }

      String saveTitle = hasChanges ? (EditorConfigManager.lang.evs_save_changed != null ? EditorConfigManager.lang.evs_save_changed : "<green>Save</green>") : (EditorConfigManager.lang.evs_save_unchanged != null ? EditorConfigManager.lang.evs_save_unchanged : "<gray>Return</gray>");
      template.set(28, GooeyButton.builder().display(confirmBall).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(saveTitle)).with(DataComponents.LORE, new ItemLore(saveLore)).onClick((a) -> {
         if (hasChanges) {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.save);
         } else {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         }

         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      addStatButton(template, 21, EditorConfigManager.guiconfig.icons.iv_hp, Items.APPLE, EditorConfigManager.lang.ivs_name_hp, Stats.HP, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      addStatButton(template, 22, EditorConfigManager.guiconfig.icons.iv_attack, Items.IRON_SWORD, EditorConfigManager.lang.ivs_name_attack, Stats.ATTACK, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      addStatButton(template, 23, EditorConfigManager.guiconfig.icons.iv_defence, Items.IRON_CHESTPLATE, EditorConfigManager.lang.ivs_name_defence, Stats.DEFENCE, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      addStatButton(template, 30, EditorConfigManager.guiconfig.icons.iv_sp_attack, Items.BLAZE_POWDER, EditorConfigManager.lang.ivs_name_sp_attack, Stats.SPECIAL_ATTACK, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      addStatButton(template, 31, EditorConfigManager.guiconfig.icons.iv_sp_defence, Items.DIAMOND, EditorConfigManager.lang.ivs_name_sp_defence, Stats.SPECIAL_DEFENCE, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      addStatButton(template, 32, EditorConfigManager.guiconfig.icons.iv_speed, Items.SUGAR, EditorConfigManager.lang.ivs_name_speed, Stats.SPEED, player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      String maxTitle = EditorConfigManager.lang.ivs_maximize_title != null ? EditorConfigManager.lang.ivs_maximize_title : "<gold>Maximize All</gold>";
      addActionButtonWithLore(template, 25, getSafeItem(EditorConfigManager.guiconfig.icons.iv_maximize, Items.GOLDEN_APPLE), maxTitle, getStatSummaryLore("Maximize"), (a) -> {
         for(Stat stat : ALL_STATS) {
            editingPokemon.getIvs().set(stat, 31);
         }

         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         editingPokemon.updateAspects();
         editingPokemon.updateForm();
         editingPokemon.heal();
         openInternal(player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
      });
      String clearTitle = EditorConfigManager.lang.ivs_clear_title != null ? EditorConfigManager.lang.ivs_clear_title : "<dark_red>Clear All (0)</dark_red>";
      addActionButtonWithLore(template, 34, getSafeItem(EditorConfigManager.guiconfig.icons.iv_clear, Items.SLIME_BALL), clearTitle, getStatSummaryLore("Clear"), (a) -> {
         for(Stat stat : ALL_STATS) {
            editingPokemon.getIvs().set(stat, 0);
         }

         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         editingPokemon.updateAspects();
         editingPokemon.updateForm();
         editingPokemon.heal();
         openInternal(player, originalPokemon, editingPokemon, isDevMode, true);
      });
      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String guiTitle = EditorConfigManager.lang.ivs_gui_title != null ? EditorConfigManager.lang.ivs_gui_title : "<dark_gray>IVs Editor</dark_gray>";
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build());
   }

   private static List<Component> getStatSummaryLore(String actionText) {
      List<Component> lore = new ArrayList<>();
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore1 != null ? EditorConfigManager.lang.evs_save_lore1 : "<gray>Charges will only be made</gray>"));
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore2 != null ? EditorConfigManager.lang.evs_save_lore2 : "<gray>in the final confirmation menu.</gray>"));
      lore.add(Component.empty());
      String actionFormat = EditorConfigManager.lang.ivs_action_lore_click != null ? EditorConfigManager.lang.ivs_action_lore_click : "<yellow>Click to <action>.</yellow>";
      lore.add(EditorConfigManager.parse(actionFormat.replace("<action>", actionText)));
      return lore;
   }

   private static void addActionButtonWithLore(ChestTemplate.Builder template, int slot, Item item, String name, List<Component> lore, Consumer<ButtonAction> onClick) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(slot, GooeyButton.builder().display(stack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(name)).with(DataComponents.LORE, new ItemLore(lore)).onClick(onClick).build());
   }

   private static void addStatButton(ChestTemplate.Builder template, int slot, String iconId, Item fallback, String name, Stat stat, ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, boolean usedMightyCandy) {
      int currentIv = editingPokemon.getIvs().getEffectiveBattleIV(stat);
      double cost = EditorConfigManager.prices.change_iv_per_point;
      String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
      List<Component> lore = new ArrayList<>();
      String currentFormat = EditorConfigManager.lang.ivs_stat_current != null ? EditorConfigManager.lang.ivs_stat_current : "<gray>Current: <yellow><value>/31</yellow></gray>";
      lore.add(EditorConfigManager.parse(currentFormat.replace("<value>", String.valueOf(currentIv))));
      lore.add(Component.empty());
      String priceFormat = EditorConfigManager.lang.ivs_stat_price != null ? EditorConfigManager.lang.ivs_stat_price : "<gray>Cost: $<cost></gray>";
      lore.add(EditorConfigManager.parse(priceFormat.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
      lore.add(Component.empty());
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.ivs_stat_click_left != null ? EditorConfigManager.lang.ivs_stat_click_left : "<green>Left Click: +1</green>"));
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.ivs_stat_click_shift_left != null ? EditorConfigManager.lang.ivs_stat_click_shift_left : "<dark_green>Shift+Left: +10</dark_green>"));
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.ivs_stat_click_right != null ? EditorConfigManager.lang.ivs_stat_click_right : "<red>Right Click: -1</red>"));
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.ivs_stat_click_shift_right != null ? EditorConfigManager.lang.ivs_stat_click_shift_right : "<dark_red>Shift+Right: -10</dark_red>"));
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.ivs_stat_click_middle != null ? EditorConfigManager.lang.ivs_stat_click_middle : "<yellow>Middle Click: Maximize</yellow>"));
      ItemStack stack = new ItemStack(getSafeItem(iconId, fallback));
      stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String safeName = name != null ? name : "<gray>Stat</gray>";
      template.set(slot, GooeyButton.builder().display(stack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(safeName)).with(DataComponents.LORE, new ItemLore(lore)).onClick((a) -> {
         String clickType = a.getClickType().name();
         int amount = clickType.contains("SHIFT") ? 10 : 1;
         int newValue = currentIv;
         if (clickType.contains("LEFT")) {
            newValue = Math.min(31, currentIv + amount);
         } else if (clickType.contains("RIGHT")) {
            newValue = Math.max(0, currentIv - amount);
         } else if (clickType.contains("MIDDLE")) {
            newValue = 31;
         }

         if (newValue != currentIv) {
            editingPokemon.getIvs().set(stat, newValue);
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            editingPokemon.updateAspects();
            editingPokemon.updateForm();
            editingPokemon.heal();
            openInternal(player, originalPokemon, editingPokemon, isDevMode, usedMightyCandy);
         }

      }).build());
   }

   private static Item getSafeItem(String iconId, Item fallback) {
      if (iconId != null && !iconId.trim().isEmpty()) {
         try {
            Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId));
            return item != null && item != Items.AIR ? item : fallback;
         } catch (Exception var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static boolean hasIvModifications(Pokemon original, Pokemon current) {
      for(Stat stat : ALL_STATS) {
         if (original.getIvs().getEffectiveBattleIV(stat) != current.getIvs().getEffectiveBattleIV(stat)) {
            return true;
         }
      }

      return false;
   }

   private static ItemLore getIvTooltip(Pokemon p) {
      List<Component> lore = new ArrayList<>();
      int total = 0;

      for(Stat stat : ALL_STATS) {
         int val = p.getIvs().getEffectiveBattleIV(stat);
         total += val;
         String statId = stat.getIdentifier().getPath();
         String statName = statId.toUpperCase().replace("_", " ");
         String color = "<white>";
         if (statId.equals("hp")) {
            color = "<red>";
         } else if (statId.equals("attack")) {
            color = "<gold>";
         } else if (statId.equals("defence")) {
            color = "<yellow>";
         } else if (statId.equals("special_attack")) {
            color = "<light_purple>";
         } else if (statId.equals("special_defence")) {
            color = "<green>";
         } else if (statId.equals("speed")) {
            color = "<aqua>";
         }

         lore.add(EditorConfigManager.parse(color + statName + ": <white>" + val + "/31</white>"));
      }

      int percent = total * 100 / 186;
      lore.add(Component.empty());
      String totalFormat = EditorConfigManager.lang.ivs_tooltip_total != null ? EditorConfigManager.lang.ivs_tooltip_total : "<gold>Total: <value>/186 (<percent>%)</gold>";
      lore.add(EditorConfigManager.parse(totalFormat.replace("<value>", String.valueOf(total)).replace("<percent>", String.valueOf(percent))));
      return new ItemLore(lore);
   }

   static {
      ALL_STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
   }
}
