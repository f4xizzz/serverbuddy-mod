package com.f4xizzz.serverbuddy.cobblemon.poke.editor.commands;

import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui.MainMenuGui;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class PokeEditorCommand {
   public static void register() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         registerCommandTree(dispatcher, "pokeeditor");

         for(String alias : EditorConfigManager.serverconfig.command_aliases) {
            registerCommandTree(dispatcher, alias);
         }

      });
   }

   private static void registerCommandTree(CommandDispatcher<CommandSourceStack> dispatcher, String literalName) {
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(literalName).requires((source) -> SbPermissions.check(source, "serverbuddy.editor.gui", 2))).executes((context) -> {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
         if (player != null) {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.gui_open);
            MainMenuGui.open(player);
         }

         return 1;
      })).then(((RequiredArgumentBuilder)Commands.argument("target", EntityArgument.player()).requires((source) -> SbPermissions.check(source, "serverbuddy.editor.gui.player", 2))).executes((context) -> {
         ServerPlayer sender = ((CommandSourceStack)context.getSource()).getPlayer();
         ServerPlayer target = EntityArgument.getPlayer(context, "target");
         if (target != null) {
            SoundUtils.playAnySound(target, EditorConfigManager.sounds.gui_open);
            MainMenuGui.open(target);
            if (sender != null && sender != target) {
               sender.sendSystemMessage(EditorConfigManager.parse(EditorConfigManager.lang.editor_opened_for_player.replace("<player>", target.getName().getString())));
            }
         }

         return 1;
      }))).then(((LiteralArgumentBuilder)Commands.literal("reload").requires((source) -> SbPermissions.check(source, "serverbuddy.editor.reload", 2))).executes((context) -> {
         EditorConfigManager.loadConfigs();
         ServerPlayer sender = ((CommandSourceStack)context.getSource()).getPlayer();
         if (sender != null) {
            SoundUtils.playAnySound(sender, EditorConfigManager.sounds.save);
         }

         ((CommandSourceStack)context.getSource()).sendSuccess(() -> EditorConfigManager.parse(EditorConfigManager.lang.config_reloaded), true);
         return 1;
      })));
   }
}
