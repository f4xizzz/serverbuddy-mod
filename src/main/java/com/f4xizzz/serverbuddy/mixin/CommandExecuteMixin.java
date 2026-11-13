package com.f4xizzz.serverbuddy.mixin;

import com.f4xizzz.serverbuddy.core.CommandHooks;
import com.f4xizzz.serverbuddy.logs.player.CommandLogBridge;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Commands.class})
public class CommandExecuteMixin {
   @Inject(
      method = {"performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void serverbuddy$onExecute(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
      CommandSourceStack source = (CommandSourceStack)parseResults.getContext().getSource();
      CommandLogBridge.onCommandExecuted(source, command);
      // os módulos podem bloquear o comando (ex.: quem está na cela)
      if (CommandHooks.fire(source, command)) {
         ci.cancel();
      }
   }
}
