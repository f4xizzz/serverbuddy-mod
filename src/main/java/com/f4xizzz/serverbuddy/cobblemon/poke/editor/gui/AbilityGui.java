package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.AbilityPool;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class AbilityGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
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
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 12 && i != 14 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7 && i != 10 && i != 11 && i != 15 && i != 16) {
               if ((i < 18 || i > 26) && (i < 36 || i > 53)) {
                  if (i >= 27 && i <= 35) {
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

      ItemStack pokeModel = PokemonItem.from(editingPokemon);
      pokeModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(13, GooeyButton.builder().display(pokeModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse("<aqua>" + editingPokemon.getDisplayName(false).getString() + "</aqua>")).build());
      List<PotentialAbility> allPotential = new ArrayList<>();
      AbilityPool var10000 = editingPokemon.getForm().getAbilities();
      Objects.requireNonNull(allPotential);
      var10000.forEach(allPotential::add);
      int[] abilitySlots = new int[]{20, 22, 24};
      int[] ballSlots = new int[]{29, 31, 33};
      int abilityIndex = 0;

      for(PotentialAbility potential : allPotential) {
         if (abilityIndex >= 3) {
            break;
         }

         boolean isHidden = potential instanceof HiddenAbility;
         AbilityTemplate templateAbility = potential.getTemplate();
         boolean isCurrent = editingPokemon.getAbility().getTemplate().getName().equals(templateAbility.getName());
         double cost = isHidden ? EditorConfigManager.prices.change_ability_hidden : EditorConfigManager.prices.change_ability_normal;
         String abilityNameRaw = templateAbility.getName();
         String var48 = abilityNameRaw.substring(0, 1).toUpperCase();
         String abilityNameStr = var48 + abilityNameRaw.substring(1).replace("_", " ");
         String abilityIconStr = isHidden ? EditorConfigManager.guiconfig.icons.ability_patch : EditorConfigManager.guiconfig.icons.ability_capsule;
         ItemStack abilityItem = new ItemStack(getSafeItem(abilityIconStr, Items.SUGAR));
         abilityItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
         List<Component> abilityLore = new ArrayList<>();
         String typeLabel = EditorConfigManager.lang.ability_type_label != null ? EditorConfigManager.lang.ability_type_label : "<gray>Type: </gray>";
         String typeValue = isHidden ? (EditorConfigManager.lang.ability_hidden != null ? EditorConfigManager.lang.ability_hidden : "Hidden") : (EditorConfigManager.lang.ability_normal != null ? EditorConfigManager.lang.ability_normal : "Normal");
         abilityLore.add(EditorConfigManager.parse(typeLabel + typeValue));
         abilityLore.add(Component.empty());
         MutableComponent translatableDesc = Component.translatable(templateAbility.getDescription());
         abilityLore.add(translatableDesc.withStyle(ChatFormatting.GRAY));
         template.set(abilitySlots[abilityIndex], GooeyButton.builder().display(abilityItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse("<yellow>" + abilityNameStr + "</yellow>")).with(DataComponents.LORE, new ItemLore(abilityLore)).build());
         String ballIconStr = isCurrent ? EditorConfigManager.guiconfig.icons.ball_selected : EditorConfigManager.guiconfig.icons.ball_unselected;
         ItemStack ballItem = new ItemStack(getSafeItem(ballIconStr, Items.SLIME_BALL));
         ballItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
         List<Component> ballLore = new ArrayList<>();
         if (isCurrent) {
            String currentText = EditorConfigManager.lang.ability_current != null ? EditorConfigManager.lang.ability_current : "<green>Current</green>";
            ballLore.add(EditorConfigManager.parse(currentText));
         } else {
            String lore1 = EditorConfigManager.lang.ability_select_lore1 != null ? EditorConfigManager.lang.ability_select_lore1 : "<gray>Click to select.</gray>";
            ballLore.add(EditorConfigManager.parse(lore1));
            ballLore.add(Component.empty());
            String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
            String lore2 = EditorConfigManager.lang.ability_select_lore2 != null ? EditorConfigManager.lang.ability_select_lore2 : "<gray>Cost: $<cost></gray>";
            ballLore.add(EditorConfigManager.parse(lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
            String lore3 = EditorConfigManager.lang.ability_select_lore3 != null ? EditorConfigManager.lang.ability_select_lore3 : "<gray>Will be charged.</gray>";
            ballLore.add(EditorConfigManager.parse(lore3));
         }

         String customNameStr;
         if (isCurrent) {
            String format = EditorConfigManager.lang.ability_selected_name != null ? EditorConfigManager.lang.ability_selected_name : "<green>Selected: <ability></green>";
            customNameStr = format.replace("<ability>", abilityNameStr);
         } else {
            String format = EditorConfigManager.lang.ability_unselected_name != null ? EditorConfigManager.lang.ability_unselected_name : "<white>Select: <ability></white>";
            customNameStr = format.replace("<ability>", abilityNameStr);
         }

         template.set(ballSlots[abilityIndex], GooeyButton.builder().display(ballItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(customNameStr)).with(DataComponents.LORE, new ItemLore(ballLore)).onClick((a) -> {
            if (!isCurrent) {
               editingPokemon.updateAbility(templateAbility.create(false, Priority.LOWEST));
               editingPokemon.updateForm();
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
            }
         }).build());
         ++abilityIndex;
      }

      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.close_button, Items.BARRIER));
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String guiTitle = EditorConfigManager.lang.ability_gui_title != null ? EditorConfigManager.lang.ability_gui_title : "<dark_gray>Ability Editor</dark_gray>";
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build());
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
