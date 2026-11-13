package com.f4xizzz.serverbuddy.mixin.portals;

import com.f4xizzz.serverbuddy.modules.portals.PortalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the portal blocks of the Portals module (the ones registered in portals.json) work without an obsidian frame: they do not
 * break when the frame is missing, they teleport the entity at once instead of starting the vanilla countdown, and they do not spawn
 * zombified piglins. Every other portal block behaves as in vanilla.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
   @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
   private void serverbuddy$keepPortal(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
      if (PortalHooks.isPortal(level, pos)) {
         cir.setReturnValue(state);
      }
   }

   @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
   private void serverbuddy$touch(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
      if (PortalHooks.touch(level, pos, entity)) {
         ci.cancel();
      }
   }

   @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
   private void serverbuddy$noPiglins(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
      if (PortalHooks.isPortal(level, pos)) {
         ci.cancel();
      }
   }
}
