package com.f4xizzz.serverbuddy.mixin;

import com.f4xizzz.serverbuddy.modules.essentials.PlayerExtras;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O Minecraft acorda na hora quem dorme onde não há cama (`checkBedExists`). Para quem foi posto para dormir pelo /sleep, a
 * checagem diz que a cama existe, e a pessoa fica deitada até acordar sozinha (dia, ou ela se levantar).
 */
@Mixin({LivingEntity.class})
public abstract class LivingEntitySleepMixin {
   @Inject(method = {"checkBedExists"}, at = {@At("HEAD")}, cancellable = true)
   private void serverbuddy$commandSleep(CallbackInfoReturnable<Boolean> cir) {
      if (PlayerExtras.isCommandSleeping(((LivingEntity) (Object) this).getUUID())) {
         cir.setReturnValue(true);
      }
   }
}
