package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
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

public class LevelGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      openInternal(player, originalPokemon, editingPokemon, isDevMode);
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
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
      String origName = EditorConfigManager.lang.level_original_name != null ? EditorConfigManager.lang.level_original_name : "<white>Original Level: <gray><level></gray></white>";
      template.set(10, GooeyButton.builder().display(originalModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(origName.replace("<level>", String.valueOf(originalPokemon.getLevel())))).build());
      ItemStack editedModel = PokemonItem.from(editingPokemon);
      editedModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String prevName = EditorConfigManager.lang.level_preview_name != null ? EditorConfigManager.lang.level_preview_name : "<yellow>New Level: <gold><level></gold></yellow>";
      template.set(19, GooeyButton.builder().display(editedModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName.replace("<level>", String.valueOf(editingPokemon.getLevel())))).build());
      boolean modified = editingPokemon.getLevel() != originalPokemon.getLevel();
      String confirmBallId = modified ? EditorConfigManager.guiconfig.icons.status_changed : EditorConfigManager.guiconfig.icons.status_unchanged;
      ItemStack confirmBall = new ItemStack(getSafeItem(confirmBallId, modified ? Items.LIME_DYE : Items.GRAY_DYE));
      confirmBall.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      List<Component> saveLore = new ArrayList<>();
      if (modified) {
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.level_save_lore1 != null ? EditorConfigManager.lang.level_save_lore1 : "<gray>Charges will only be made</gray>"));
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.level_save_lore2 != null ? EditorConfigManager.lang.level_save_lore2 : "<gray>in the final confirmation menu.</gray>"));
         saveLore.add(Component.empty());
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.level_save_lore3 != null ? EditorConfigManager.lang.level_save_lore3 : "<yellow>Click to save!</yellow>"));
      } else {
         saveLore.add(EditorConfigManager.parse(EditorConfigManager.lang.level_save_lore_unchanged != null ? EditorConfigManager.lang.level_save_lore_unchanged : "<gray>Change the level to save.</gray>"));
      }

      String saveTitle = modified ? (EditorConfigManager.lang.level_save_changed != null ? EditorConfigManager.lang.level_save_changed : "<green>Save</green>") : (EditorConfigManager.lang.level_save_unchanged != null ? EditorConfigManager.lang.level_save_unchanged : "<gray>Return</gray>");
      template.set(28, GooeyButton.builder().display(confirmBall).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(saveTitle)).with(DataComponents.LORE, new ItemLore(saveLore)).onClick((a) -> {
         if (modified) {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.save);
         } else {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         }

         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      addLevelButton(template, 14, EditorConfigManager.guiconfig.icons.level_set_1, Items.WHITE_DYE, EditorConfigManager.lang.level_btn_set_1, (a) -> editingPokemon.setLevel(1), player, originalPokemon, editingPokemon, isDevMode);
      addLevelButton(template, 23, EditorConfigManager.guiconfig.icons.level_add_1, Items.LIME_DYE, EditorConfigManager.lang.level_btn_add_1, (a) -> editingPokemon.setLevel(Math.min(100, editingPokemon.getLevel() + 1)), player, originalPokemon, editingPokemon, isDevMode);
      addLevelButton(template, 32, EditorConfigManager.guiconfig.icons.level_add_5, Items.LIGHT_BLUE_DYE, EditorConfigManager.lang.level_btn_add_5, (a) -> editingPokemon.setLevel(Math.min(100, editingPokemon.getLevel() + 5)), player, originalPokemon, editingPokemon, isDevMode);
      addLevelButton(template, 15, EditorConfigManager.guiconfig.icons.level_add_15, Items.MAGENTA_DYE, EditorConfigManager.lang.level_btn_add_15, (a) -> editingPokemon.setLevel(Math.min(100, editingPokemon.getLevel() + 15)), player, originalPokemon, editingPokemon, isDevMode);
      addLevelButton(template, 24, EditorConfigManager.guiconfig.icons.level_add_50, Items.RED_DYE, EditorConfigManager.lang.level_btn_add_50, (a) -> editingPokemon.setLevel(Math.min(100, editingPokemon.getLevel() + 50)), player, originalPokemon, editingPokemon, isDevMode);
      addLevelButton(template, 33, EditorConfigManager.guiconfig.icons.level_set_100, Items.GOLDEN_APPLE, EditorConfigManager.lang.level_btn_set_100, (a) -> editingPokemon.setLevel(100), player, originalPokemon, editingPokemon, isDevMode);
      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String guiTitle = EditorConfigManager.lang.level_gui_title != null ? EditorConfigManager.lang.level_gui_title : "<dark_gray>Level Editor</dark_gray>";
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build());
   }

   private static void addLevelButton(ChestTemplate.Builder template, int slot, String iconId, Item fallback, String name, Consumer<ButtonAction> onClick, ServerPlayer p, Pokemon orig, Pokemon edit, boolean dev) {
      ItemStack item = new ItemStack(getSafeItem(iconId, fallback));
      item.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      double cost = EditorConfigManager.prices.change_level_per_point;
      String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
      List<Component> lore = new ArrayList<>();
      String lorePrice = EditorConfigManager.lang.level_btn_lore_price != null ? EditorConfigManager.lang.level_btn_lore_price : "<gray>Cost: $<cost></gray>";
      lore.add(EditorConfigManager.parse(lorePrice.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
      String safeName = name != null ? name : "<gray>Level Option</gray>";
      template.set(slot, GooeyButton.builder().display(item).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(safeName)).with(DataComponents.LORE, new ItemLore(lore)).onClick((a) -> {
         int oldLevel = edit.getLevel();
         onClick.accept(a);
         if (oldLevel != edit.getLevel()) {
            SoundUtils.playAnySound(p, EditorConfigManager.sounds.click);
            edit.updateAspects();
            edit.updateForm();
            edit.heal();
            openInternal(p, orig, edit, dev);
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
}
