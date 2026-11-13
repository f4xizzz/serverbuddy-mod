package com.f4xizzz.serverbuddy.cobblemon.catcharea;

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
import net.minecraft.world.phys.Vec3;

public class CatchAreaDisplays {
   private static final int DISCONNECT = 0;
   private static final int CANCEL = 1;
   private static final int NEXT_SPAWN = 2;
   private static final int TIME_REMAINING = 3;
   private static final double[] SLOT_Y_OFFSETS = new double[]{0.0, 1.1, 1.6, 2.1};
   private static final double SLOT_SEARCH_RADIUS = 0.05;

   private CatchAreaDisplays() {
   }

   public static void update(ServerLevel world, CatchAreaRegion region, CatchAreaSession session) {
      Vec3 center = region.getCenter();
      double baseY = (double)(region.getMinY() + 2);
      BlockPos anchor = BlockPos.containing(center.x, baseY, center.z);
      if (world.areEntitiesLoaded(ChunkPos.asLong(anchor))) {
         Display.TextDisplay[] displays = new Display.TextDisplay[4];

         for(int i = 0; i < displays.length; ++i) {
            displays[i] = resolveSlot(world, center.x, baseY + SLOT_Y_OFFSETS[i], center.z);
         }

         CatchAreaConfig config = CatchAreaConfigManager.getConfig();
         long now = System.currentTimeMillis();
         long remainingSeconds = Math.max(0L, (session.expiresAtMillis - now) / 1000L);
         boolean isAway = session.awayFromAreaSinceMillis != null;
         Component timeText;
         if (isAway) {
            long awayRemainingSeconds = Math.max(0L, ((long)config.awayFromAreaTimeoutMinutes * 60000L - (now - session.awayFromAreaSinceMillis)) / 1000L);
            timeText = Component.literal("⚠ Out of the area! Released in: ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal(formatDurationLong(awayRemainingSeconds)).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
         } else {
            timeText = Component.literal("\u23f3 Time left: ").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}).append(Component.literal(formatDurationLong(remainingSeconds)).withStyle(ChatFormatting.WHITE));
         }

         applyText(displays[3], timeText);
         int currentCount = CatchAreaEntityTracker.getEntities(session.areaName).size();
         Component nextSpawnText;
         if (currentCount >= config.maxSpawnedPokemon) {
            nextSpawnText = Component.literal("⏱ Next Pokémon: ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}).append(Component.literal(currentCount + "/" + config.maxSpawnedPokemon).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})).append(Component.literal(" - Maximum capacity reached!").withStyle(ChatFormatting.RED));
         } else {
            long nextSpawnSeconds = Math.max(0L, (session.lastSpawnAtMillis + (long)config.spawnIntervalSeconds * 1000L - now) / 1000L);
            nextSpawnText = Component.literal("⏱ Next Pokémon: ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}).append(Component.literal(formatDuration(nextSpawnSeconds)).withStyle(ChatFormatting.YELLOW));
         }

         applyText(displays[2], nextSpawnText);
         applyText(displays[1], Component.literal("\u2716 Cancel: ").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}).append(Component.literal("/catcharea leave").withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.ITALIC})));
         applyText(displays[0], Component.literal("\u26a0 ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal("Disconnecting keeps the time running. ").withStyle(ChatFormatting.GRAY)).append(Component.literal(config.offlineTimeoutMinutes + " min").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})).append(Component.literal(" offline cancels the area and the amount paid is not refunded.").withStyle(ChatFormatting.GRAY)));
      }
   }

   public static void remove(ServerLevel world, CatchAreaRegion region) {
      Vec3 center = region.getCenter();
      double baseY = (double)(region.getMinY() + 2);
      BlockPos anchor = BlockPos.containing(center.x, baseY, center.z);
      if (world.areEntitiesLoaded(ChunkPos.asLong(anchor))) {
         for(double offset : SLOT_Y_OFFSETS) {
            for(Display.TextDisplay display : findAt(world, center.x, baseY + offset, center.z)) {
               display.discard();
            }
         }

      }
   }

   private static Display.TextDisplay resolveSlot(ServerLevel world, double x, double y, double z) {
      List<Display.TextDisplay> nearby = findAt(world, x, y, z);
      if (nearby.isEmpty()) {
         return spawnDisplay(world, x, y, z);
      } else {
         for(int i = 1; i < nearby.size(); ++i) {
            ((Display.TextDisplay)nearby.get(i)).discard();
         }

         return (Display.TextDisplay)nearby.get(0);
      }
   }

   private static List<Display.TextDisplay> findAt(ServerLevel world, double x, double y, double z) {
      AABB searchBox = new AABB(x - 0.05, y - 0.05, z - 0.05, x + 0.05, y + 0.05, z + 0.05);
      return world.getEntitiesOfClass(Display.TextDisplay.class, searchBox, (entity) -> true);
   }

   private static Display.TextDisplay spawnDisplay(ServerLevel world, double x, double y, double z) {
      Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
      display.moveTo(x, y, z, 0.0F, 0.0F);
      applyDisplaySettings(display);
      applyText(display, Component.literal("..."));
      world.addFreshEntity(display);
      return display;
   }

   private static void applyText(Display.TextDisplay display, Component text) {
      ((TextDisplayEntityAccessor)display).invokeSetText(text);
      ((DisplayEntityAccessor)display).invokeSetBillboardMode(BillboardConstraints.CENTER);
   }

   private static void applyDisplaySettings(Display.TextDisplay display) {
      TextDisplayEntityAccessor accessor = (TextDisplayEntityAccessor)display;
      accessor.invokeSetBackground(0);
      accessor.invokeSetDisplayFlags((byte)1);
   }

   private static String formatDuration(long totalSeconds) {
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return String.format("%02d:%02d", minutes, seconds);
   }

   private static String formatDurationLong(long totalSeconds) {
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      return hours + "h" + minutes + "m" + seconds + "s";
   }
}
