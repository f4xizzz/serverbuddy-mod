package com.f4xizzz.serverbuddy.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Display.class})
public interface DisplayEntityAccessor {
   @Invoker("setBillboardConstraints")
   void invokeSetBillboardMode(Display.BillboardConstraints var1);
}
