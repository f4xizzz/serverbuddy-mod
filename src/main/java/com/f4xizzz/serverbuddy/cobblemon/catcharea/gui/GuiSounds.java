package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiSoundsConfigManager;
import com.f4xizzz.serverbuddy.util.SoundPlayer;
import net.minecraft.server.level.ServerPlayer;

public class GuiSounds {
   private GuiSounds() {
   }

   public static void playOpen(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().open);
   }

   public static void playClick(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().click);
   }

   public static void playSelect(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().select);
   }

   public static void playConfirm(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().confirm);
   }

   public static void playCancel(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().cancel);
   }

   public static void playError(ServerPlayer player) {
      SoundPlayer.play(player, CatchAreaGuiSoundsConfigManager.getConfig().error);
   }
}
