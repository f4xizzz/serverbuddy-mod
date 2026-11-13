package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.PokemonTooltipUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.impactdev.impactor.api.Impactor;
import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public class ConfirmGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      ItemStack bgBlue = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_primary)));
      bgBlue.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlue = GooeyButton.builder().display(bgBlue).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgRed = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_secondary)));
      bgRed.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(bgRed).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_dark)));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgWhite = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_white)));
      bgWhite.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(bgWhite).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 12 && i != 13 && i != 14 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7 && i != 10 && i != 11 && i != 15 && i != 16) {
               if ((i < 18 || i > 26) && (i < 45 || i > 53)) {
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
      template.set(21, GooeyButton.builder().display(originalModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_original_name)).with(DataComponents.LORE, getPokemonTooltip(originalPokemon)).build());
      ItemStack arrowItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.arrow_icon)));
      arrowItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(22, GooeyButton.builder().display(arrowItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_arrow_name)).build());
      ItemStack editedModel = PokemonItem.from(editingPokemon);
      editedModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      String var10000 = editingPokemon.getShiny() ? "<gold>\u2605 </gold>" : "";
      String name = var10000 + editingPokemon.getDisplayName(false).getString();
      template.set(23, GooeyButton.builder().display(editedModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_edited_name + name)).with(DataComponents.LORE, getPokemonTooltip(editingPokemon)).build());
      ItemStack cancelBtnItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.cancel_button)));
      if (cancelBtnItem.isEmpty()) {
         cancelBtnItem = new ItemStack(Items.SLIME_BALL);
      }

      cancelBtnItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(28, GooeyButton.builder().display(cancelBtnItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_cancel_button)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      ItemStack confirmBtnItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.confirm_button)));
      if (confirmBtnItem.isEmpty()) {
         confirmBtnItem = new ItemStack(Items.LIME_DYE);
      }

      confirmBtnItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      confirmBtnItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      template.set(34, GooeyButton.builder().display(confirmBtnItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_accept_button)).with(DataComponents.LORE, getConfirmTooltip(originalPokemon, editingPokemon, isDevMode)).onClick((a) -> {
         long cost = calculateCost(originalPokemon, editingPokemon);
         if (!isDevMode && cost > 0L) {
            EconomyService economyService = (EconomyService)Impactor.instance().services().provide(EconomyService.class);
            if (economyService == null) {
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
               player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.confirm_error_economy));
               return;
            }

            Account account = (Account)economyService.account(EditorConfigManager.resolveCurrency(economyService), player.getUUID()).join();
            if (account.balance().doubleValue() < (double)cost) {
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
               player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.confirm_error_funds.replace("<cost>", EditorConfigManager.formatCurrency(String.valueOf(cost)))));
               return;
            }

            EconomyTransaction transaction = account.withdraw(BigDecimal.valueOf(cost));
            if (!transaction.successful()) {
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
               player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.confirm_error_transaction));
               return;
            }

            player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.confirm_success_transaction.replace("<cost>", EditorConfigManager.formatCurrency(String.valueOf(cost)))));
         }

         for(Stat stat : Stats.Companion.getPERMANENT()) {
            originalPokemon.getIvs().set(stat, editingPokemon.getIvs().getEffectiveBattleIV(stat));
            originalPokemon.getEvs().set(stat, editingPokemon.getEvs().getOrDefault(stat));
         }

         originalPokemon.copyFrom(editingPokemon);
         originalPokemon.updateAspects();
         originalPokemon.updateForm();
         originalPokemon.heal();
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.success);
         player.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.confirm_toast_success));
         MainMenuGui.open(player);
      }).build());
      ItemStack closeItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.close_button)));
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.confirm_close_button)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         MainMenuGui.open(player);
      }).build());
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(EditorConfigManager.lang.confirm_gui_title)).build());
   }

   private static long calculateCost(Pokemon original, Pokemon current) {
      double total = 0.0;
      if (current.getLevel() > original.getLevel()) {
         total += (double)(current.getLevel() - original.getLevel()) * EditorConfigManager.prices.change_level_per_point;
      }

      if (current.getShiny() && !original.getShiny()) {
         total += EditorConfigManager.prices.change_shiny;
      }

      if (original.getGender() != current.getGender()) {
         total += EditorConfigManager.prices.change_gender;
      }

      if (current.getFriendship() > original.getFriendship()) {
         total += (double)(current.getFriendship() - original.getFriendship()) * EditorConfigManager.prices.change_friendship_per_point;
      }

      if (!original.getNature().getName().equals(current.getNature().getName())) {
         total += EditorConfigManager.prices.change_nature;
      }

      for(Stat stat : Stats.Companion.getPERMANENT()) {
         int diffIv = current.getIvs().getEffectiveBattleIV(stat) - original.getIvs().getEffectiveBattleIV(stat);
         if (diffIv > 0) {
            total += (double)diffIv * EditorConfigManager.prices.change_iv_per_point;
         }

         int diffEv = current.getEvs().getOrDefault(stat) - original.getEvs().getOrDefault(stat);
         if (diffEv > 0) {
            total += (double)diffEv * EditorConfigManager.prices.change_ev_per_point;
         }
      }

      if (!original.getAbility().getTemplate().getName().equals(current.getAbility().getTemplate().getName())) {
         boolean isHidden = false;

         for(PotentialAbility potential : current.getForm().getAbilities()) {
            if (potential.getTemplate().getName().equals(current.getAbility().getTemplate().getName())) {
               if (potential instanceof HiddenAbility) {
                  isHidden = true;
               }
               break;
            }
         }

         total += isHidden ? EditorConfigManager.prices.change_ability_hidden : EditorConfigManager.prices.change_ability_normal;
      }

      if (!original.getCaughtBall().getName().equals(current.getCaughtBall().getName())) {
         String ballId = current.getCaughtBall().getName().getPath();
         total += (Double)EditorConfigManager.prices.custom_ball_costs.getOrDefault(ballId, EditorConfigManager.prices.change_ball_default_cost);
      }

      if (!original.getSpecies().getName().equals(current.getSpecies().getName())) {
         total += EditorConfigManager.prices.evolve;
      }

      Mark origMark = original.getActiveMark();
      Mark currMark = current.getActiveMark();
      if ((origMark == null && currMark != null || origMark != null && currMark == null || origMark != null && currMark != null && !origMark.getIdentifier().equals(currMark.getIdentifier())) && currMark != null) {
         String markId = currMark.getIdentifier().getPath();
         double markCost = EditorConfigManager.prices.change_mark_default_cost;

         for(Map.Entry<String, Double> entry : EditorConfigManager.prices.custom_mark_costs.entrySet()) {
            if (markId.contains((CharSequence)entry.getKey())) {
               markCost = (Double)entry.getValue();
               break;
            }
         }

         total += markCost;
      }

      if (!original.getAspects().equals(current.getAspects())) {
         total += EditorConfigManager.prices.change_aspects;
      }

      return (long)total;
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

   private static ItemLore getConfirmTooltip(Pokemon original, Pokemon current, boolean isDevMode) {
      List<Component> lore = new ArrayList<>();
      boolean changed = hasModifications(original, current);
      lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_title));
      if (original.getLevel() != current.getLevel()) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_level.replace("<old>", String.valueOf(original.getLevel())).replace("<new>", String.valueOf(current.getLevel()))));
      }

      if (original.getShiny() != current.getShiny()) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_shiny.replace("<new>", current.getShiny() ? EditorConfigManager.lang.word_yes : EditorConfigManager.lang.word_no)));
      }

      if (original.getGender() != current.getGender()) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_gender.replace("<old>", original.getGender().name()).replace("<new>", current.getGender().name())));
      }

      if (original.getFriendship() != current.getFriendship()) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_friendship.replace("<old>", String.valueOf(original.getFriendship())).replace("<new>", String.valueOf(current.getFriendship()))));
      }

      if (!original.getNature().getName().equals(current.getNature().getName())) {
         String oldNat = original.getNature().getName().getPath();
         String newNat = current.getNature().getName().getPath();
         String var10000 = oldNat.substring(0, 1).toUpperCase();
         oldNat = var10000 + oldNat.substring(1);
         var10000 = newNat.substring(0, 1).toUpperCase();
         newNat = var10000 + newNat.substring(1);
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_nature.replace("<old>", oldNat).replace("<new>", newNat)));
      }

      if (!original.getAbility().getTemplate().getName().equals(current.getAbility().getTemplate().getName())) {
         String oldAb = original.getAbility().getTemplate().getName().replace("_", " ");
         String newAb = current.getAbility().getTemplate().getName().replace("_", " ");
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_ability.replace("<old>", oldAb).replace("<new>", newAb)));
      }

      if (!original.getCaughtBall().getName().equals(current.getCaughtBall().getName())) {
         String oldBall = original.getCaughtBall().getName().getPath().replace("_", " ");
         String newBall = current.getCaughtBall().getName().getPath().replace("_", " ");
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_ball.replace("<old>", oldBall).replace("<new>", newBall)));
      }

      if (!original.getSpecies().getName().equals(current.getSpecies().getName())) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_species.replace("<old>", original.getSpecies().getName()).replace("<new>", current.getSpecies().getName())));
      }

      Mark origMark = original.getActiveMark();
      Mark currMark = current.getActiveMark();
      if (origMark == null && currMark != null || origMark != null && currMark == null || origMark != null && currMark != null && !origMark.getIdentifier().equals(currMark.getIdentifier())) {
         String oldM = formatMarkName(origMark);
         String newM = formatMarkName(currMark);
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_mark.replace("<old>", oldM).replace("<new>", newM)));
      }

      if (!original.getAspects().equals(current.getAspects())) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_aspects));
      }

      for(Stat stat : Stats.Companion.getPERMANENT()) {
         int oldIv = original.getIvs().getEffectiveBattleIV(stat);
         int newIv = current.getIvs().getEffectiveBattleIV(stat);
         if (oldIv != newIv) {
            lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_iv.replace("<stat>", stat.getIdentifier().getPath().toUpperCase()).replace("<old>", String.valueOf(oldIv)).replace("<new>", String.valueOf(newIv))));
         }

         int oldEv = original.getEvs().getOrDefault(stat);
         int newEv = current.getEvs().getOrDefault(stat);
         if (oldEv != newEv) {
            lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_ev.replace("<stat>", stat.getIdentifier().getPath().toUpperCase()).replace("<old>", String.valueOf(oldEv)).replace("<new>", String.valueOf(newEv))));
         }
      }

      if (!changed) {
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_no_changes));
      } else {
         lore.add(EditorConfigManager.parse(" "));
         if (isDevMode) {
            lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_dev_mode.replace("<cost>", EditorConfigManager.formatCurrency("0"))));
         } else {
            long totalCost = calculateCost(original, current);
            lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_total_cost.replace("<cost>", EditorConfigManager.formatCurrency(String.valueOf(totalCost)))));
         }

         lore.add(EditorConfigManager.parse(" "));
         lore.add(EditorConfigManager.parse(EditorConfigManager.lang.confirm_summary_click_confirm));
      }

      return new ItemLore(lore);
   }

   private static ItemLore getPokemonTooltip(Pokemon p) {
      return PokemonTooltipUtils.build(EditorConfigManager.lang.pokemon_tooltip, p, EditorConfigManager.tooltipStrings(), Map.of());
   }

   private static String formatMarkName(Mark mark) {
      if (mark == null) {
         return EditorConfigManager.lang.word_none;
      } else {
         String titleKey = mark.getTitle();
         String markName;
         if (titleKey != null && !titleKey.trim().isEmpty()) {
            markName = Component.translatable(titleKey).getString();
            markName = markName.replace("%1$s", "").replace("%s", "").trim();
         } else {
            markName = mark.getIdentifier().getPath().replace("_", " ");
         }

         if (!markName.isEmpty()) {
            String var10000 = markName.substring(0, 1).toUpperCase();
            markName = var10000 + markName.substring(1);
         }

         return markName;
      }
   }
}
