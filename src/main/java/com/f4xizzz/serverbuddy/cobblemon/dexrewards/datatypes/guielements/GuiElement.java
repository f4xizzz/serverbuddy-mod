package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.guielements;

import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.Messages;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;

public record GuiElement(String key, String name, List<String> lore, boolean glint) {
   public DataComponentPatch getComponentMap(UUID uuid, RewardGroup group) {
      String parsed_name = this.name;
      List<String> parsed_lore = this.lore;
      if (uuid != null) {
         parsed_name = Messages.parse(this.name, uuid);
         parsed_lore = new ArrayList<>();

         for(String line : this.lore) {
            parsed_lore.add(Messages.parse(line, uuid));
         }
      }

      List<String> more_parsed_lore = parsed_lore;
      if (group != null) {
         parsed_name = Messages.parse(this.name, group);
         parsed_lore = new ArrayList<>();

         for(String line : more_parsed_lore) {
            parsed_lore.add(Messages.parse(line, group));
         }
      }

      List<Component> lore_text = new ArrayList<>();

      for(String line : parsed_lore) {
         lore_text.add(TextUtils.deserialize(line));
      }

      return DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, TextUtils.deserialize(parsed_name)).set(DataComponents.LORE, new ItemLore(lore_text)).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, this.glint).build();
   }
}
