package com.f4xizzz.serverbuddy.cobblemon.hunt.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.item.PokemonItem;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.gui.GuiIcons;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntBucket;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntPool;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class HuntGui {
   private static final int ITEMS_PER_PAGE = 21;
   private static final int[][] T_SLOTS = buildTSlots();

   private HuntGui() {
   }

   private static int[][] buildTSlots() {
      int[][] slots = new int[21][2];
      int idx = 0;

      for(int row = 1; row <= 3; ++row) {
         for(int col = 1; col <= 7; ++col) {
            slots[idx][0] = row;
            slots[idx][1] = col;
            ++idx;
         }
      }

      return slots;
   }

   public static void open(ServerPlayer player, int pageIndex) {
      HuntPool pool = HuntManager.getPoolFor(player);
      List<HuntTarget> targets = new ArrayList<>(pool.getTargets().values());
      int totalPages = Math.max(1, (int)Math.ceil((double)targets.size() / 21.0));
      int page = Math.max(0, Math.min(pageIndex, totalPages - 1));
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      buildBorders(template);
      buildTargetButtons(template, targets, page);
      buildFooterNav(template, player, page, totalPages);
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("Hunts (" + (page + 1) + "/" + totalPages + ")")).build());
   }

   private static void buildBorders(ChestTemplate.Builder template) {
      for(int col = 0; col <= 8; ++col) {
         template.set(0, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE));
         template.set(4, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE));
      }

      for(int row = 1; row <= 3; ++row) {
         template.set(row, 0, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 8, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      }

      for(int col = 0; col <= 8; ++col) {
         template.set(5, col, GuiIcons.glassButton(Items.BLACK_STAINED_GLASS_PANE));
      }

   }

   private static void buildTargetButtons(ChestTemplate.Builder template, List<HuntTarget> targets, int page) {
      int start = page * 21;

      for(int i = 0; i < T_SLOTS.length; ++i) {
         int index = start + i;
         if (index < targets.size()) {
            template.set(T_SLOTS[i][0], T_SLOTS[i][1], buildTargetButton((HuntTarget)targets.get(index)));
         } else {
            ItemStack empty = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            empty.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
            GuiIcons.hideAdditionalTooltip(empty);
            template.set(T_SLOTS[i][0], T_SLOTS[i][1], GooeyButton.builder().display(empty).build());
         }
      }

   }

   private static Button buildTargetButton(HuntTarget target) {
      ItemStack icon = PokemonItem.from(target.pokemon);
      Component speciesName = target.pokemon.getSpecies().getTranslatedName().copy().withStyle(target.pokemon.getShiny() ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
      if (target.pokemon.getShiny()) {
         speciesName = Component.literal("\u2605 ").withStyle(ChatFormatting.YELLOW).append(speciesName);
      }

      icon.set(DataComponents.CUSTOM_NAME, speciesName);
      List<Component> lore = new ArrayList<>();
      lore.add(bucketLabel(target.bucket));
      if (target.preco > 0.0) {
         lore.add(Component.literal("Reward: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(target.preco)).withStyle(ChatFormatting.GOLD)));
      }

      long remainingSeconds = Math.max(0L, (target.expiresAtMillis - System.currentTimeMillis()) / 1000L);
      lore.add(Component.literal("Time left: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatDuration(remainingSeconds)).withStyle(ChatFormatting.WHITE)));
      icon.set(DataComponents.LORE, new ItemLore(lore));
      GuiIcons.hideAdditionalTooltip(icon);
      return GooeyButton.builder().display(icon).build();
   }

   private static Component bucketLabel(HuntBucket bucket) {
      MutableComponent var10000;
      switch (bucket) {
         case COMMON -> var10000 = Component.literal("Comum").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD});
         case UNCOMMON -> var10000 = Component.literal("Incomum").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
         case RARE -> var10000 = Component.literal("Raro").withStyle(new ChatFormatting[]{ChatFormatting.BLUE, ChatFormatting.BOLD});
         case ULTRA_RARE -> var10000 = Component.literal("Ultra-raro").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD});
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static void buildFooterNav(ChestTemplate.Builder template, ServerPlayer player, int page, int totalPages) {
      int prevPage = Math.max(0, page - 1);
      int nextPage = Math.min(totalPages - 1, page + 1);
      ItemStack prevIcon = new ItemStack(Items.ARROW);
      prevIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(ChatFormatting.YELLOW));
      GuiIcons.hideAdditionalTooltip(prevIcon);
      template.set(5, 0, GooeyButton.builder().display(prevIcon).onClick((action) -> open(player, prevPage)).build());
      ItemStack nextIcon = new ItemStack(Items.ARROW);
      nextIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(ChatFormatting.YELLOW));
      GuiIcons.hideAdditionalTooltip(nextIcon);
      template.set(5, 8, GooeyButton.builder().display(nextIcon).onClick((action) -> open(player, nextPage)).build());
      ItemStack exitIcon = new ItemStack(Items.BARRIER);
      exitIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Close").withStyle(ChatFormatting.RED));
      GuiIcons.hideAdditionalTooltip(exitIcon);
      template.set(5, 4, GooeyButton.builder().display(exitIcon).onClick((action) -> UIManager.closeUI(player)).build());
   }

   private static String formatDuration(long totalSeconds) {
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      return hours > 0L ? hours + "h" + minutes + "m" : minutes + "m" + seconds + "s";
   }
}
