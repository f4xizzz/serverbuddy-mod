package com.f4xizzz.serverbuddy.util;

import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.ComponentSerializer;

public final class MiniMessageUtil {
   private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
   private static final ComponentSerializer<Component, Component, net.minecraft.network.chat.Component> SERIALIZER = FabricAudiences.nonWrappingSerializer();

   private MiniMessageUtil() {
   }

   public static net.minecraft.network.chat.Component parse(String miniMessage) {
      if (miniMessage != null && !miniMessage.isEmpty()) {
         Component component = MINI_MESSAGE.deserialize(miniMessage);
         return (net.minecraft.network.chat.Component)SERIALIZER.serialize(component);
      } else {
         return net.minecraft.network.chat.Component.empty();
      }
   }
}
