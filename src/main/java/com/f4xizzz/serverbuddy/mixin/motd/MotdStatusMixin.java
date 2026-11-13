package com.f4xizzz.serverbuddy.mixin.motd;

import com.f4xizzz.serverbuddy.modules.motd.MotdBridge;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Troca o status da lista de servidores (MOTD, ícone, contagem de jogadores) na hora do ping.
 * Fica desligado quando o MiniMOTD está instalado (ver {@link MotdMixinPlugin}), porque os dois mexem no mesmo ponto.
 */
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class MotdStatusMixin {
   @Shadow
   @Final
   private ServerStatus status;

   @Redirect(
      method = "handleStatusRequest",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/server/network/ServerStatusPacketListenerImpl;status:Lnet/minecraft/network/protocol/status/ServerStatus;"
      )
   )
   private ServerStatus serverbuddy$status(ServerStatusPacketListenerImpl instance) {
      return MotdBridge.status(this.status);
   }
}
