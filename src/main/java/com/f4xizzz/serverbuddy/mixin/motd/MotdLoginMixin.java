package com.f4xizzz.serverbuddy.mixin.motd;

import com.f4xizzz.serverbuddy.modules.motd.MotdBridge;
import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recusas de login: o modo manutenção e a mensagem de "não está na whitelist" da config. */
@Mixin(PlayerList.class)
public abstract class MotdLoginMixin {
   @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
   private void serverbuddy$maintenance(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
      Component refusal = MotdBridge.loginRefusal((PlayerList)(Object)this, profile);
      if (refusal != null) {
         cir.setReturnValue(refusal);
      }
   }

   @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
   private void serverbuddy$whitelistMessage(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
      Component original = cir.getReturnValue();
      Component refined = MotdBridge.refineRefusal(original);
      if (refined != original) {
         cir.setReturnValue(refined);
      }
   }
}
