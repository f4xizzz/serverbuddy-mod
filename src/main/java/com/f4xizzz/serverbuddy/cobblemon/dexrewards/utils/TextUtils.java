package com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils;

import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class TextUtils {
   public static net.minecraft.network.chat.Component deserialize(String text) {
      Component component = MiniMessage.miniMessage().deserialize(text);
      return DexRewards.INSTANCE.audience().toNative(component);
   }
}
