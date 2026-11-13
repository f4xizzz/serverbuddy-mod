package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.PokemonTooltipUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.f4xizzz.serverbuddy.core.SbPermissions;
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

public class PokemonEditorGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon) {
      if (canEditSpecies(player, originalPokemon)) {
         Pokemon editingPokemon = originalPokemon.clone(false, player.getServer().registryAccess());
         openInternal(player, originalPokemon, editingPokemon, false);
      }
   }

   public static void open(ServerPlayer player, Pokemon pokemon, boolean isDevMode) {
      if (canEditSpecies(player, pokemon)) {
         openInternal(player, pokemon, pokemon.clone(false, player.getServer().registryAccess()), isDevMode);
      }
   }

   private static boolean canEditSpecies(ServerPlayer player, Pokemon pokemon) {
      String speciesId = pokemon.getSpecies().getName();
      boolean blocked = EditorConfigManager.serverconfig.blocked_species.stream().anyMatch((entry) -> entry != null && entry.trim().equalsIgnoreCase(speciesId));
      if (blocked) {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.species_not_editable.replace("<pokemon>", speciesId)));
      }

      return !blocked;
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      ItemStack bgBlue = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_primary, Items.BLUE_STAINED_GLASS_PANE));
      bgBlue.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlue = GooeyButton.builder().display(bgBlue).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgRed = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_secondary, Items.RED_STAINED_GLASS_PANE));
      bgRed.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(bgRed).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_dark, Items.BLACK_STAINED_GLASS_PANE));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgWhite = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_white, Items.WHITE_STAINED_GLASS_PANE));
      bgWhite.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(bgWhite).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 12 && i != 14 && i != 17 && i != 18 && i != 26) {
            if (i != 1 && i != 2 && i != 6 && i != 7) {
               if (i != 21 && i != 23 && (i < 45 || i > 53 || i == 49)) {
                  if (i == 27 || i == 30 || i == 32 || i == 35 || i >= 36 && i <= 44) {
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

      ItemStack walletItem = new ItemStack(getSafeItem(EditorConfigManager.serverconfig.currency_icon_item, Items.SUNFLOWER));
      walletItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(0, GooeyButton.builder().display(walletItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parseWalletBalance(player)).build());
      if (player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.editor.admin.aspects")) {
         addActionButton(template, 4, getSafeItem(EditorConfigManager.guiconfig.icons.editor_aspects, Items.BLACK_STAINED_GLASS_PANE), EditorConfigManager.lang.editor_btn_aspects, (a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            AspectsGui.open(player, originalPokemon, editingPokemon, isDevMode);
         });
      }

      if (player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.editor.devmode")) {
         addActionButton(template, 8, getSafeItem(EditorConfigManager.guiconfig.icons.editor_dev_mode, Items.DIAMOND), isDevMode ? EditorConfigManager.lang.editor_btn_dev_on : EditorConfigManager.lang.editor_btn_dev_off, (a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, !isDevMode);
         });
      }

      addActionButton(template, 10, getSafeItem(EditorConfigManager.guiconfig.icons.editor_level, Items.BLUE_ICE), EditorConfigManager.lang.editor_btn_level, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         LevelGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      addActionButton(template, 11, getSafeItem(EditorConfigManager.guiconfig.icons.editor_marks, Items.PAPER), EditorConfigManager.lang.editor_btn_marks, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         MarkGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      ItemStack originalModel = PokemonItem.from(originalPokemon);
      originalModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(13, GooeyButton.builder().display(originalModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.editor_pokemon_original)).with(DataComponents.LORE, getPokemonTooltip(originalPokemon)).build());
      boolean isShiny = editingPokemon.getShiny();
      Item shinyItem = isShiny ? getSafeItem(EditorConfigManager.guiconfig.icons.editor_shiny_on, Items.SUGAR_CANE) : getSafeItem(EditorConfigManager.guiconfig.icons.editor_shiny_off, Items.NETHER_STAR);
      double shinyCost = EditorConfigManager.prices.change_shiny;
      String formattedShinyCost = shinyCost == (double)((long)shinyCost) ? String.format("%d", (long)shinyCost) : String.format("%s", shinyCost);
      List<Component> shinyLore = new ArrayList<>();
      shinyLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_shiny_lore1));
      shinyLore.add(EditorConfigManager.parse(" "));
      shinyLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_shiny_lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedShinyCost))));
      shinyLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore3.replace("return to the Editor!", "the Confirmation.")));
      addActionButtonWithLore(template, 15, shinyItem, isShiny ? EditorConfigManager.lang.editor_btn_shiny_normal : EditorConfigManager.lang.editor_btn_shiny_shiny, shinyLore, (a) -> {
         editingPokemon.setShiny(!editingPokemon.getShiny());
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         openInternal(player, originalPokemon, editingPokemon, isDevMode);
      });
      int currentFriendship = editingPokemon.getFriendship();
      double friendshipCost = EditorConfigManager.prices.change_friendship_per_point;
      String formattedFriendCost = friendshipCost == (double)((long)friendshipCost) ? String.format("%d", (long)friendshipCost) : String.format("%s", friendshipCost);
      List<Component> friendshipLore = new ArrayList<>();
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore1.replace("<value>", String.valueOf(currentFriendship))));
      friendshipLore.add(EditorConfigManager.parse(" "));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedFriendCost))));
      friendshipLore.add(EditorConfigManager.parse(" "));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore_left));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore_shift_left));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore_right));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore_shift_right));
      friendshipLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_friendship_lore_middle));
      addActionButtonWithLore(template, 16, getSafeItem(EditorConfigManager.guiconfig.icons.editor_friendship, Items.BELL), EditorConfigManager.lang.editor_btn_friendship, friendshipLore, (a) -> {
         String clickType = a.getClickType().name();
         int amount = clickType.contains("SHIFT") ? 50 : 10;
         int newValue = currentFriendship;
         if (clickType.contains("LEFT")) {
            newValue = Math.min(255, currentFriendship + amount);
         } else if (clickType.contains("RIGHT")) {
            newValue = Math.max(0, currentFriendship - amount);
         } else if (clickType.contains("MIDDLE")) {
            newValue = 255;
         }

         if (newValue != currentFriendship) {
            editingPokemon.setFriendship(newValue, true);
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, isDevMode);
         }

      });
      addActionButton(template, 19, getSafeItem(EditorConfigManager.guiconfig.icons.editor_evolutions, Items.OBSERVER), EditorConfigManager.lang.editor_btn_evolutions, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         EvolutionsGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      addActionButton(template, 20, getSafeItem(EditorConfigManager.guiconfig.icons.editor_evs, Items.SUGAR), EditorConfigManager.lang.editor_btn_evs, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         EvsGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      ItemStack editedModel = PokemonItem.from(editingPokemon);
      editedModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String var10000 = editingPokemon.getShiny() ? "<gold>\u2605 </gold>" : "";
      String nameStr = var10000 + editingPokemon.getDisplayName(false).getString();
      template.set(22, GooeyButton.builder().display(editedModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.editor_pokemon_edited.replace("<name>", nameStr))).with(DataComponents.LORE, getPokemonTooltip(editingPokemon)).build());
      Item mochiItem = getSafeItem(editingPokemon.getGender() == Gender.MALE ? EditorConfigManager.guiconfig.icons.editor_gender_male : EditorConfigManager.guiconfig.icons.editor_gender_female, Items.BEETROOT);
      double genderCost = EditorConfigManager.prices.change_gender;
      String formattedGenderCost = genderCost == (double)((long)genderCost) ? String.format("%d", (long)genderCost) : String.format("%s", genderCost);
      List<Component> genderLore = new ArrayList<>();
      genderLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_gender_lore1));
      genderLore.add(EditorConfigManager.parse(" "));
      genderLore.add(EditorConfigManager.parse(EditorConfigManager.lang.nature_select_lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedGenderCost))));
      genderLore.add(EditorConfigManager.parse(EditorConfigManager.lang.evs_save_lore3.replace("return to the Editor!", "the Confirmation.")));
      addActionButtonWithLore(template, 24, mochiItem, EditorConfigManager.lang.editor_btn_gender, genderLore, (a) -> {
         editingPokemon.setGender(editingPokemon.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE);
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         openInternal(player, originalPokemon, editingPokemon, isDevMode);
      });
      addActionButton(template, 25, getSafeItem(EditorConfigManager.guiconfig.icons.editor_ability, Items.END_CRYSTAL), EditorConfigManager.lang.editor_btn_ability, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         AbilityGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      double natureCost = EditorConfigManager.prices.change_nature;
      String formattedNatureCost = natureCost == (double)((long)natureCost) ? String.format("%d", (long)natureCost) : String.format("%s", natureCost);
      List<Component> natureLore = new ArrayList<>();
      natureLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_nature_lore1));
      natureLore.add(EditorConfigManager.parse(" "));
      natureLore.add(EditorConfigManager.parse(EditorConfigManager.lang.nature_select_lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedNatureCost))));
      addActionButtonWithLore(template, 28, getSafeItem(EditorConfigManager.guiconfig.icons.editor_nature, Items.KELP), EditorConfigManager.lang.editor_btn_nature, natureLore, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         NatureGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      addActionButton(template, 29, getSafeItem(EditorConfigManager.guiconfig.icons.editor_ivs, Items.POTION), EditorConfigManager.lang.editor_btn_ivs, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         IvsGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      boolean hasChanges = hasModifications(originalPokemon, editingPokemon);
      String confirmBallId = hasChanges ? EditorConfigManager.guiconfig.icons.status_changed : EditorConfigManager.guiconfig.icons.status_unchanged;
      Item confirmGlass = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(confirmBallId));
      if (confirmGlass == null || confirmGlass == Items.AIR) {
         confirmGlass = hasChanges ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE;
      }

      String confirmTitle = hasChanges ? EditorConfigManager.lang.editor_btn_confirm_changes : EditorConfigManager.lang.editor_btn_confirm_none;
      ItemStack confirmModel = new ItemStack(confirmGlass);
      confirmModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(31, GooeyButton.builder().display(confirmModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(confirmTitle)).onClick((a) -> {
         if (hasChanges) {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            ConfirmGui.open(player, originalPokemon, editingPokemon, isDevMode);
         }

      }).build());
      addActionButton(template, 33, getSafeItem(EditorConfigManager.guiconfig.icons.editor_catch_ball, Items.SLIME_BALL), EditorConfigManager.lang.editor_btn_catch_ball, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         CatchBallGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      double formCost = EditorConfigManager.prices.change_aspects;
      String formattedFormCost = formCost == (double)((long)formCost) ? String.format("%d", (long)formCost) : String.format("%s", formCost);
      List<Component> regionLore = new ArrayList<>();
      regionLore.add(EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_form_lore1));
      regionLore.add(EditorConfigManager.parse(" "));
      regionLore.add(EditorConfigManager.parse(EditorConfigManager.lang.nature_select_lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedFormCost))));
      addActionButtonWithLore(template, 34, getSafeItem(EditorConfigManager.guiconfig.icons.editor_form, Items.MAP), EditorConfigManager.lang.editor_btn_form, regionLore, (a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         RegionalFormsGui.open(player, originalPokemon, editingPokemon, isDevMode);
      });
      template.set(49, GooeyButton.builder().display(new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.close_button, Items.BARRIER))).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.editor_btn_discard)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         MainMenuGui.open(player);
      }).build());
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(EditorConfigManager.lang.editor_gui_title)).build());
   }

   private static Item getSafeItem(String iconId, Item fallback) {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId));
      return item != null && item != Items.AIR ? item : fallback;
   }

   private static boolean hasModifications(Pokemon original, Pokemon current) {
      if (original.getLevel() == current.getLevel() && original.getShiny() == current.getShiny() && original.getGender() == current.getGender() && original.getFriendship() == current.getFriendship() && original.getSpecies().getName().equals(current.getSpecies().getName()) && original.getNature().getName().equals(current.getNature().getName()) && original.getAbility().getTemplate().getName().equals(current.getAbility().getTemplate().getName()) && original.getCaughtBall().getName().equals(current.getCaughtBall().getName()) && original.getAspects().equals(current.getAspects())) {
         Mark origMark = original.getActiveMark();
         Mark currMark = current.getActiveMark();
         if ((origMark != null || currMark == null) && (origMark == null || currMark != null) && (origMark == null || currMark == null || origMark.getIdentifier().equals(currMark.getIdentifier()))) {
            for(Stat stat : Stats.Companion.getPERMANENT()) {
               if (original.getIvs().getEffectiveBattleIV(stat) != current.getIvs().getEffectiveBattleIV(stat)) {
                  return true;
               }

               if (original.getEvs().getOrDefault(stat) != current.getEvs().getOrDefault(stat)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private static ItemLore getPokemonTooltip(Pokemon p) {
      return PokemonTooltipUtils.build(EditorConfigManager.lang.pokemon_tooltip, p, EditorConfigManager.tooltipStrings(), Map.of());
   }

   private static void addActionButton(ChestTemplate.Builder template, int slot, Item item, String name, Consumer<ButtonAction> onClick) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(slot, GooeyButton.builder().display(stack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(name)).onClick(onClick).build());
   }

   private static void addActionButtonWithLore(ChestTemplate.Builder template, int slot, Item item, String name, List<Component> lore, Consumer<ButtonAction> onClick) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(slot, GooeyButton.builder().display(stack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(name)).with(DataComponents.LORE, new ItemLore(lore)).onClick(onClick).build());
   }
}
