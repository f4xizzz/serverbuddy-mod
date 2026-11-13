package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.guielements;

import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.Messages;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.PlayerData;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public class GuiLayout {
   String title;
   int size;
   List<String> layout;
   String background_symbol;
   String player_info_symbol;
   String group_symbol;
   String previous_page_symbol;
   String next_page_symbol;
   ItemStack background_item;
   ItemStack previous_item;
   ItemStack next_item;

   public GuiLayout(String title, int size, List<String> layout, String background_symbol, String player_info_symbol, String group_symbol, String previous_page_symbol, String next_page_symbol, ItemStack background_item, ItemStack previous_item, ItemStack next_item) {
      this.title = title;
      this.size = size;
      this.layout = layout;
      this.background_symbol = background_symbol;
      this.player_info_symbol = player_info_symbol;
      this.group_symbol = group_symbol;
      this.previous_page_symbol = previous_page_symbol;
      this.next_page_symbol = next_page_symbol;
      this.background_item = background_item;
      this.previous_item = previous_item;
      this.next_item = next_item;
   }

   public MenuType<?> getType() {
      MenuType var10000;
      switch (this.size) {
         case 1 -> var10000 = MenuType.GENERIC_9x1;
         case 2 -> var10000 = MenuType.GENERIC_9x2;
         case 3 -> var10000 = MenuType.GENERIC_9x3;
         case 4 -> var10000 = MenuType.GENERIC_9x4;
         case 5 -> var10000 = MenuType.GENERIC_9x5;
         default -> var10000 = MenuType.GENERIC_9x6;
      }

      return var10000;
   }

   public void create_gui(ServerPlayer player, int page) {
      SimpleGui gui = new SimpleGui(this.getType(), player, false);
      gui.setTitle(TextUtils.deserialize(this.title));
      this.setSlots(gui, this.background_symbol, (new GuiElementBuilder(this.background_item)).build());
      ItemStack player_info = Items.PLAYER_HEAD.getDefaultInstance();
      GuiElement pi_element = null;

      for(GuiElement element : DexRewards.INSTANCE.config().gui_elements) {
         if (element.key().equals("player_info")) {
            pi_element = element;
         }
      }

      player_info.applyComponentsAndValidate(DataComponentPatch.builder().set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile())).build());
      if (pi_element != null) {
         player_info.applyComponentsAndValidate(pi_element.getComponentMap(player.getUUID(), (RewardGroup)null));
      }

      this.setSlots(gui, this.player_info_symbol, (new GuiElementBuilder(player_info)).build());
      List<Integer> groupSlots = this.getGroupSlots();
      int set_groups = groupSlots.size() * page;
      int count = groupSlots.size() * page;
      if (!groupSlots.isEmpty()) {
         for(int slot : groupSlots) {
            if (count >= set_groups) {
               if (count >= DexRewards.INSTANCE.config().reward_groups.size()) {
                  gui.setSlot(slot, (new GuiElementBuilder(this.background_item)).build());
               } else {
                  RewardGroup group = (RewardGroup)DexRewards.INSTANCE.config().reward_groups.get(count);
                  ItemStack icon = group.icon;
                  PlayerData playerData = DexRewards.INSTANCE.config().getPlayerData(player.getUUID());
                  String status = "locked";
                  if (playerData != null) {
                     if (playerData.claimable_rewards.contains(group.name)) {
                        status = "claimable";
                     } else if (playerData.claimed_rewards.contains(group.name)) {
                        status = "claimed";
                     }
                  }

                  GuiElement group_element = null;

                  for(GuiElement elementx : DexRewards.INSTANCE.config().gui_elements) {
                     if (elementx.key().equals(status + "_group")) {
                        group_element = elementx;
                     }
                  }

                  if (group_element != null) {
                     icon.applyComponentsAndValidate(group_element.getComponentMap(player.getUUID(), group));
                  }

                  final String statusFinal = status;
                  gui.setSlot(slot, (new GuiElementBuilder(icon)).setCallback((i, clickType, slotActionType) -> {
                     if (statusFinal.equals("claimable")) {
                        playerData.claimable_rewards.removeIf((data) -> data.equals(group.name));
                        playerData.claimed_rewards.add(group.name);
                        group.distribute_rewards(player);
                        DexRewards.INSTANCE.config().updatePlayerData(playerData);
                        player.sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.rewards_claimed, group)));
                        this.create_gui(player, page);
                     }

                  }).build());
                  ++set_groups;
               }
            }

            ++count;
         }

         if (set_groups < DexRewards.INSTANCE.config().reward_groups.size()) {
            this.setSlots(gui, this.next_page_symbol, (new GuiElementBuilder(this.next_item)).setCallback((i, clickType, slotActionType) -> this.create_gui(player, page + 1)).build());
         } else {
            this.setSlots(gui, this.next_page_symbol, (new GuiElementBuilder(this.background_item)).build());
         }

         if (page > 0) {
            this.setSlots(gui, this.previous_page_symbol, (new GuiElementBuilder(this.previous_item)).setCallback((i, clickType, slotActionType) -> this.create_gui(player, page - 1)).build());
         } else {
            this.setSlots(gui, this.previous_page_symbol, (new GuiElementBuilder(this.background_item)).build());
         }
      }

      gui.open();
   }

   public List<Integer> getGroupSlots() {
      List<Integer> slots = new ArrayList<>();
      int slot = 0;

      for(String layout : this.layout) {
         for(char c : layout.toCharArray()) {
            if (c == this.group_symbol.charAt(0)) {
               slots.add(slot);
            }

            ++slot;
         }
      }

      return slots;
   }

   public void setSlots(SimpleGui gui, String symbol, eu.pb4.sgui.api.elements.GuiElement gui_element) {
      int slot = 0;

      for(String layout : this.layout) {
         for(char c : layout.toCharArray()) {
            if (c == symbol.charAt(0)) {
               gui.setSlot(slot, gui_element);
            }

            ++slot;
         }
      }

   }
}
