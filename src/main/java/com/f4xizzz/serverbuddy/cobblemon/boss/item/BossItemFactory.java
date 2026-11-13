package com.f4xizzz.serverbuddy.cobblemon.boss.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

public class BossItemFactory {
   private static final String NBT_KEY = "ServerBuddyBossItem";
   // chave do mod antigo (GreatServerUtils): itens de boss que os jogadores ja tem continuam valendo
   private static final String LEGACY_NBT_KEY = "GreatBossItem";

   private BossItemFactory() {
   }

   public static ItemStack create(BossItemDefinition def) {
      Item baseItem;
      try {
         baseItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(def.baseItem));
      } catch (Exception var6) {
         baseItem = Items.PAPER;
      }

      ItemStack stack = new ItemStack(baseItem);
      if (def.displayName != null && !def.displayName.isBlank()) {
         stack.set(DataComponents.CUSTOM_NAME, Component.literal(def.displayName));
      }

      if (def.description != null && !def.description.isEmpty()) {
         List<Component> lines = new ArrayList<>();

         for(String line : def.description) {
            lines.add(Component.literal(line));
         }

         stack.set(DataComponents.LORE, new ItemLore(lines));
      }

      if (def.customModelData != 0) {
         stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(def.customModelData));
      }

      if (def.glowing) {
         stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
      }

      CompoundTag nbt = new CompoundTag();
      nbt.putString(NBT_KEY, def.name);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
      return stack;
   }

   public static String getBossItemName(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (data == null) {
            return null;
         } else {
            CompoundTag nbt = data.copyTag();
            if (nbt.contains(NBT_KEY)) {
               return nbt.getString(NBT_KEY);
            }

            return nbt.contains(LEGACY_NBT_KEY) ? nbt.getString(LEGACY_NBT_KEY) : null;
         }
      } else {
         return null;
      }
   }

   public static boolean isBossItem(ItemStack stack, String name) {
      String id = getBossItemName(stack);
      return id != null && name != null && id.equalsIgnoreCase(name);
   }
}
