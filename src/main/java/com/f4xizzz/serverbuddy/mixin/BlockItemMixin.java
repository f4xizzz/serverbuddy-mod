package com.f4xizzz.serverbuddy.mixin;

import com.f4xizzz.serverbuddy.modules.chunklimits.ChunkLimits;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block a player places by hand goes through {@code BlockItem.place}. The chunk limits module refuses the placement here
 * when the chunk is already full for that kind of block (redstone parts, hoppers, Poké Snacks...).
 */
@Mixin({BlockItem.class})
public abstract class BlockItemMixin {
   @Inject(method = {"place"}, at = {@At("HEAD")}, cancellable = true)
   private void serverbuddy$chunkLimit(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
      if (!ChunkLimits.allowPlace(context, ((BlockItem) (Object) this).getBlock())) {
         cir.setReturnValue(InteractionResult.FAIL);
      }
   }
}
