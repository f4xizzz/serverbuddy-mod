package com.f4xizzz.serverbuddy.mixin.blacklist;

import com.f4xizzz.serverbuddy.modules.blacklist.BlacklistBridge;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recusa a escolha de um golpe proibido (Blacklist). Mexe numa classe do Cobblemon, então é {@code @Pseudo}
 * (sem o Cobblemon nada acontece) e só é aplicado quando o plugin {@link BlacklistMixinPlugin} deixa.
 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.battles.model.actor.BattleActor", remap = false)
public abstract class BattleActorMixin {
   @Inject(method = "setActionResponses", at = @At("HEAD"), cancellable = true, remap = false)
   private void serverbuddy$blacklist(List<?> responses, CallbackInfo ci) {
      if (BlacklistBridge.intercept(this, responses)) {
         ci.cancel();
      }
   }
}
