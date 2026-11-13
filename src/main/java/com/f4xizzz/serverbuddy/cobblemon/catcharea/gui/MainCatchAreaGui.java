package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfig;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaOccupancyManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegion;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegionManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.DexCompletionUtil;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.PreEvolutionResolver;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RandomPoolManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RentalFlowManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class MainCatchAreaGui {
   private static final int[][] R_SLOTS = new int[][]{{1, 2}, {1, 3}, {1, 4}, {1, 5}, {1, 6}, {2, 2}, {2, 3}, {2, 4}, {2, 5}, {2, 6}};

   private MainCatchAreaGui() {
   }

   public static void open(ServerPlayer player, String areaName) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      buildBorders(template);
      buildPoolButtons(template, player, areaName);
      template.set(3, 3, buildAreasInfoButton());
      template.set(3, 4, buildRotationClockButton());
      template.set(3, 5, buildMasterballButton(player, areaName));
      CatchAreaGuiIconsConfig icons = CatchAreaGuiIconsConfigManager.getConfig();
      ItemStack exitIcon = GuiIcons.resolveIcon(icons.exitIcon);
      exitIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Exit").withStyle(ChatFormatting.RED));
      GuiIcons.hideAdditionalTooltip(exitIcon);
      template.set(5, 4, GooeyButton.builder().display(exitIcon).onClick((action) -> {
         GuiSounds.playCancel(player);
         UIManager.closeUI(player);
      }).build());
      GuiSounds.playOpen(player);
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("Catch Area")).build());
   }

   private static void buildBorders(ChestTemplate.Builder template) {
      for(int col = 0; col <= 8; ++col) {
         template.set(0, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.YELLOW_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE));
         template.set(4, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.YELLOW_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE));
      }

      for(int row : new int[]{1, 2}) {
         template.set(row, 0, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));
         template.set(row, 1, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 7, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 8, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));
      }

      template.set(3, 0, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));
      template.set(3, 1, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      template.set(3, 2, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      template.set(3, 6, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      template.set(3, 7, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      template.set(3, 8, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));

      for(int col = 0; col <= 8; ++col) {
         template.set(5, col, GuiIcons.glassButton(Items.BLACK_STAINED_GLASS_PANE));
      }

   }

   private static void buildPoolButtons(ChestTemplate.Builder template, ServerPlayer player, String areaName) {
      List<Species> pool = RandomPoolManager.getCurrentPoolSpecies();

      for(int i = 0; i < R_SLOTS.length; ++i) {
         if (i < pool.size()) {
            template.set(R_SLOTS[i][0], R_SLOTS[i][1], buildPoolButton(player, areaName, (Species)pool.get(i)));
         } else {
            template.set(R_SLOTS[i][0], R_SLOTS[i][1], GuiIcons.glassButton(Items.GRAY_STAINED_GLASS_PANE));
         }
      }

   }

   private static Button buildPoolButton(ServerPlayer player, String areaName, Species species) {
      Species base = PreEvolutionResolver.resolveBase(species);
      Pokemon preview = new Pokemon();
      preview.setSpecies(species);
      preview.initialize();
      ItemStack icon = PokemonItem.from(preview);
      icon.set(DataComponents.CUSTOM_NAME, species.getTranslatedName().copy().withStyle(ChatFormatting.AQUA));
      icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Will spawn: ").withStyle(ChatFormatting.GRAY).append(base.getTranslatedName().copy().withStyle(ChatFormatting.YELLOW)))));
      GuiIcons.hideAdditionalTooltip(icon);
      return GooeyButton.builder().display(icon).onClick((action) -> {
         GuiSounds.playSelect(player);
         RentalFlowManager.PendingRental rental = RentalFlowManager.getOrCreate(player.getUUID());
         rental.reset();
         rental.areaName = areaName;
         rental.choiceMode = false;
         rental.chosenSpecies = species;
         rental.baseSpecies = base;
         DurationPickerGui.open(player);
      }).build();
   }

   private static Button buildAreasInfoButton() {
      Map<String, CatchAreaRegion> regions = CatchAreaRegionManager.getAll();
      int total = regions.size();
      long free = regions.entrySet().stream().filter((entry) -> ((CatchAreaRegion)entry.getValue()).enabled && CatchAreaOccupancyManager.getRenterOf((String)entry.getKey()) == null).count();
      ItemStack icon = GuiIcons.resolveIcon(CatchAreaGuiIconsConfigManager.getConfig().areasInfoIcon);
      icon.set(DataComponents.CUSTOM_NAME, Component.literal("Available areas").withStyle(ChatFormatting.GOLD));
      icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(free + "/" + total).withStyle(ChatFormatting.AQUA))));
      GuiIcons.hideAdditionalTooltip(icon);
      return GooeyButton.builder().display(icon).build();
   }

   private static Button buildRotationClockButton() {
      long remainingMillis = Math.max(0L, RandomPoolManager.getNextRotationMillis() - System.currentTimeMillis());
      long totalMinutes = remainingMillis / 60000L;
      long hours = totalMinutes / 60L;
      long minutes = totalMinutes % 60L;
      ItemStack icon = GuiIcons.resolveIcon(CatchAreaGuiIconsConfigManager.getConfig().clockIcon);
      icon.set(DataComponents.CUSTOM_NAME, Component.literal("Next pool rotation").withStyle(ChatFormatting.GOLD));
      icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("In " + hours + "h " + minutes + "min").withStyle(ChatFormatting.AQUA))));
      GuiIcons.hideAdditionalTooltip(icon);
      return GooeyButton.builder().display(icon).build();
   }

   private static Button buildMasterballButton(ServerPlayer player, String areaName) {
      ItemStack icon = GuiIcons.resolveIcon(CatchAreaGuiIconsConfigManager.getConfig().masterballIcon);
      icon.set(DataComponents.CUSTOM_NAME, Component.literal("Catch Area Premium").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD}));
      boolean unlocked = DexCompletionUtil.canUseChoiceMode(player);
      List<Component> lore = new ArrayList<>();
      if (unlocked) {
         lore.add(Component.literal("Click to pick any Pokémon from your party/PC!").withStyle(ChatFormatting.GRAY));
      } else {
         lore.add(Component.literal("Requires 100% of the Pokédex captured.").withStyle(ChatFormatting.RED));
      }

      icon.set(DataComponents.LORE, new ItemLore(lore));
      GuiIcons.hideAdditionalTooltip(icon);
      return GooeyButton.builder().display(icon).onClick((action) -> {
         if (!DexCompletionUtil.canUseChoiceMode(player)) {
            GuiSounds.playError(player);
            player.displayClientMessage(Component.literal("You need 100% of the Pokédex (or a special permission) to access the Premium Catch Area!").withStyle(ChatFormatting.RED), true);
         } else {
            GuiSounds.playSelect(player);
            RentalFlowManager.PendingRental rental = RentalFlowManager.getOrCreate(player.getUUID());
            rental.reset();
            rental.areaName = areaName;
            rental.choiceMode = true;
            PremiumCatchAreaGui.open(player, areaName);
         }
      }).build();
   }
}
