package com.f4xizzz.serverbuddy.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Display.TextDisplay.class})
public interface TextDisplayEntityAccessor {
   @Invoker("setText")
   void invokeSetText(Component var1);

   @Invoker("setBackgroundColor")
   void invokeSetBackground(int var1);

   @Invoker("setFlags")
   void invokeSetDisplayFlags(byte var1);
}
