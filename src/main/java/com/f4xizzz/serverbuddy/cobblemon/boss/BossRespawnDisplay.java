package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.f4xizzz.serverbuddy.mixin.DisplayEntityAccessor;
import com.f4xizzz.serverbuddy.mixin.TextDisplayEntityAccessor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class BossRespawnDisplay {
   private static final double Y_OFFSET = 2.5;
   private static final double SEARCH_RADIUS = 0.2;

   private BossRespawnDisplay() {
   }

   public static void update(ServerLevel world, BossDefinition boss, long secondsRemaining) {
      BlockPos anchor = BlockPos.containing(boss.x, boss.y + 2.5, boss.z);
      if (world.areEntitiesLoaded(ChunkPos.asLong(anchor))) {
         Display.TextDisplay display = resolve(world, boss);
         Component text = Component.literal("\u23f3 ").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}).append(Component.literal(displayName(boss)).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})).append(Component.literal(" respawna em ").withStyle(ChatFormatting.AQUA)).append(Component.literal(formatDuration(secondsRemaining)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
         applyText(display, text);
      }
   }

   public static void remove(ServerLevel world, BossDefinition boss) {
      BlockPos anchor = BlockPos.containing(boss.x, boss.y + 2.5, boss.z);
      if (world.areEntitiesLoaded(ChunkPos.asLong(anchor))) {
         for(Display.TextDisplay display : findAt(world, boss)) {
            display.discard();
         }

      }
   }

   private static String displayName(BossDefinition boss) {
      return boss.nickname != null && !boss.nickname.isBlank() ? boss.nickname : boss.speciesId;
   }

   private static Display.TextDisplay resolve(ServerLevel world, BossDefinition boss) {
      List<Display.TextDisplay> nearby = findAt(world, boss);
      if (nearby.isEmpty()) {
         return spawn(world, boss);
      } else {
         for(int i = 1; i < nearby.size(); ++i) {
            ((Display.TextDisplay)nearby.get(i)).discard();
         }

         return (Display.TextDisplay)nearby.get(0);
      }
   }

   private static List<Display.TextDisplay> findAt(ServerLevel world, BossDefinition boss) {
      double x = boss.x;
      double y = boss.y + 2.5;
      double z = boss.z;
      AABB box = new AABB(x - 0.2, y - 0.2, z - 0.2, x + 0.2, y + 0.2, z + 0.2);
      return world.getEntitiesOfClass(Display.TextDisplay.class, box, (entity) -> true);
   }

   private static Display.TextDisplay spawn(ServerLevel world, BossDefinition boss) {
      Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
      display.moveTo(boss.x, boss.y + 2.5, boss.z, 0.0F, 0.0F);
      ((TextDisplayEntityAccessor)display).invokeSetBackground(0);
      ((TextDisplayEntityAccessor)display).invokeSetDisplayFlags((byte)1);
      applyText(display, Component.literal("..."));
      world.addFreshEntity(display);
      return display;
   }

   private static void applyText(Display.TextDisplay display, Component text) {
      ((TextDisplayEntityAccessor)display).invokeSetText(text);
      ((DisplayEntityAccessor)display).invokeSetBillboardMode(BillboardConstraints.CENTER);
   }

   private static String formatDuration(long totalSeconds) {
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return String.format("%02d:%02d", minutes, seconds);
   }
}
