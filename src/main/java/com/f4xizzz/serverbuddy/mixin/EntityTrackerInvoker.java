package com.f4xizzz.serverbuddy.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(
   targets = {"net/minecraft/server/level/ChunkMap$TrackedEntity"}
)
public interface EntityTrackerInvoker {
   @Invoker("removePlayer")
   void serverbuddy$stopTracking(ServerPlayer var1);

   @Invoker("updatePlayer")
   void serverbuddy$updateTrackedStatus(ServerPlayer var1);
}
