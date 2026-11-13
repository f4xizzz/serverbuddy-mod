package com.f4xizzz.serverbuddy.mixin;

import com.f4xizzz.serverbuddy.modules.essentials.SignFormatting;
import java.util.List;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quem tem a permissão de formatar placas escreve com cores (&c, &lnegrito, MiniMessage seguro): o texto que o jogador digitou na
 * placa é interpretado antes de ser guardado.
 */
@Mixin({SignBlockEntity.class})
public abstract class SignFormatMixin {
   @Inject(method = {"setMessages"}, at = {@At("HEAD")}, cancellable = true)
   private void serverbuddy$formatSign(Player player, List<FilteredText> messages, SignText text, CallbackInfoReturnable<SignText> cir) {
      SignText formatted = SignFormatting.format(player, messages, text);
      if (formatted != null) {
         cir.setReturnValue(formatted);
      }
   }
}
