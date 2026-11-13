package com.f4xizzz.serverbuddy.mixin.flan;

import com.f4xizzz.serverbuddy.modules.flan.FlanMenuBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Acrescenta ao menu da claim do Flan os botões de entrada por permissão e por tag. É {@code @Pseudo}: sem o Flan nada acontece,
 * e o plugin {@link FlanMixinPlugin} só o aplica quando o Flan está instalado.
 */
@Pseudo
@Mixin(targets = "io.github.flemmli97.flan.gui.ClaimMenuScreenHandler", remap = false)
public abstract class FlanClaimMenuMixin {
   @Inject(method = "fillInventoryWith", at = @At("TAIL"), remap = false)
   private void serverbuddy$fill(CallbackInfo ci) {
      FlanMenuBridge.fill(this);
   }

   @Inject(method = "handleSlotClicked", at = @At("HEAD"), cancellable = true, remap = false)
   private void serverbuddy$click(ServerPlayer player, int index, Slot slot, int clickType, CallbackInfoReturnable<Boolean> cir) {
      if (FlanMenuBridge.click(this, player, index)) {
         cir.setReturnValue(true);
      }
   }
}
