package com.f4xizzz.serverbuddy.mixin;

import com.f4xizzz.serverbuddy.modules.essentials.Styling;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O nome do jogador na lista (Tab) vem do formato `styling.tab_list.player_format`, quando a lista está ligada na config. */
@Mixin({ServerPlayer.class})
public abstract class ServerPlayerTabNameMixin {
   @Inject(method = {"getTabListDisplayName"}, at = {@At("HEAD")}, cancellable = true)
   private void serverbuddy$tabName(CallbackInfoReturnable<Component> cir) {
      Component name = Styling.tabName((ServerPlayer) (Object) this);
      if (name != null) {
         cir.setReturnValue(name);
      }
   }
}
