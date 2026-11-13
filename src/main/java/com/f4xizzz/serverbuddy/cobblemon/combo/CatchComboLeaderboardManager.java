package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.f4xizzz.serverbuddy.mixin.DisplayEntityAccessor;
import com.f4xizzz.serverbuddy.mixin.TextDisplayEntityAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CatchComboLeaderboardManager {
   private static final String[] MEDALS = new String[]{"\ud83e\udd47", "\ud83e\udd48", "\ud83e\udd49"};
   private static final ChatFormatting[] RANK_COLORS;
   private static final long CACHE_REFRESH_INTERVAL_MILLIS = 5000L;
   private static volatile List<CatchComboLeaderboardDisplay> cachedDisplays;
   private static volatile List<CatchComboLeaderboardEntry> cachedTop;
   private static volatile long lastCacheRefreshMillis;
   private static final AtomicBoolean cacheRefreshInProgress;

   private CatchComboLeaderboardManager() {
   }

   public static Float parseDirection(String direction) {
      Float var10000;
      switch (direction.toLowerCase()) {
         case "s" -> var10000 = 0.0F;
         case "w" -> var10000 = 90.0F;
         case "n" -> var10000 = 180.0F;
         case "e" -> var10000 = 270.0F;
         default -> var10000 = null;
      }

      return var10000;
   }

   public static CatchComboLeaderboardDisplay createAt(ServerPlayer player, float yaw) {
      ServerLevel world = player.serverLevel();
      Vec3 pos = player.position();
      CatchComboLeaderboardDisplay record = new CatchComboLeaderboardDisplay();
      record.displayId = UUID.randomUUID().toString();
      record.dimension = world.dimension().location().toString();
      record.x = pos.x;
      record.y = pos.y;
      record.z = pos.z;
      record.yaw = yaw;
      Display.TextDisplay display = spawnEntity(world, record);
      record.entityUuid = display.getUUID();
      CatchComboLeaderboardDisplayRepository.save(record);
      List<CatchComboLeaderboardDisplay> updatedDisplays = new ArrayList<>(cachedDisplays);
      updatedDisplays.add(record);
      cachedDisplays = updatedDisplays;
      refreshText(display, CatchComboLeaderboardRepository.getTop(10));
      return record;
   }

   public static void tick(MinecraftServer server) {
      refreshCacheIfDue();

      for(CatchComboLeaderboardDisplay record : cachedDisplays) {
         ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(record.dimension)));
         if (world != null) {
            BlockPos pos = BlockPos.containing(record.x, record.y, record.z);
            if (world.areEntitiesLoaded(ChunkPos.asLong(pos))) {
               AABB searchBox = (new AABB(pos)).inflate(1.5);
               List<Display.TextDisplay> nearby = world.getEntitiesOfClass(Display.TextDisplay.class, searchBox, (entity) -> true);
               Display.TextDisplay display;
               if (nearby.isEmpty()) {
                  display = spawnEntity(world, record);
                  record.entityUuid = display.getUUID();
                  CompletableFuture.runAsync(() -> CatchComboLeaderboardDisplayRepository.save(record));
               } else {
                  display = (Display.TextDisplay)nearby.get(0);

                  for(int i = 1; i < nearby.size(); ++i) {
                     ((Display.TextDisplay)nearby.get(i)).discard();
                  }

                  if (record.entityUuid == null || !record.entityUuid.equals(display.getUUID())) {
                     record.entityUuid = display.getUUID();
                     CompletableFuture.runAsync(() -> CatchComboLeaderboardDisplayRepository.save(record));
                  }
               }

               refreshText(display, cachedTop);
            }
         }
      }

   }

   private static void refreshCacheIfDue() {
      long now = System.currentTimeMillis();
      if (now - lastCacheRefreshMillis >= 5000L) {
         if (cacheRefreshInProgress.compareAndSet(false, true)) {
            lastCacheRefreshMillis = now;
            CompletableFuture.runAsync(() -> {
               try {
                  cachedDisplays = CatchComboLeaderboardDisplayRepository.loadAll();
                  cachedTop = CatchComboLeaderboardRepository.getTop(10);
               } finally {
                  cacheRefreshInProgress.set(false);
               }

            });
         }
      }
   }

   private static Display.TextDisplay spawnEntity(ServerLevel world, CatchComboLeaderboardDisplay record) {
      Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
      display.moveTo(record.x, record.y, record.z, record.yaw, 0.0F);
      TextDisplayEntityAccessor accessor = (TextDisplayEntityAccessor)display;
      accessor.invokeSetBackground(0);
      accessor.invokeSetDisplayFlags((byte)1);
      ((DisplayEntityAccessor)display).invokeSetBillboardMode(BillboardConstraints.FIXED);
      world.addFreshEntity(display);
      return display;
   }

   private static void refreshText(Display.TextDisplay display, List<CatchComboLeaderboardEntry> top) {
      ((TextDisplayEntityAccessor)display).invokeSetText(buildLeaderboardText(top));
   }

   private static Component buildLeaderboardText(List<CatchComboLeaderboardEntry> top) {
      String border = "\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac";
      MutableComponent text = Component.literal(border).withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("\n\ud83c\udfc6 ").withStyle(ChatFormatting.GOLD)).append(Component.literal("CATCH COMBO").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})).append(Component.literal(" \ud83c\udfc6").withStyle(ChatFormatting.GOLD)).append(Component.literal("\n" + border).withStyle(ChatFormatting.DARK_GRAY));
      if (top.isEmpty()) {
         text = text.copy().append(Component.literal("\nNobody on the leaderboard yet!").withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}));
      } else {
         for(int i = 0; i < top.size(); ++i) {
            CatchComboLeaderboardEntry entry = (CatchComboLeaderboardEntry)top.get(i);
            ChatFormatting rankColor = i < RANK_COLORS.length ? RANK_COLORS[i] : ChatFormatting.GRAY;
            String rankLabel = i < MEDALS.length ? MEDALS[i] : i + 1 + "\u00ba";
            String species = entry.speciesName != null && !entry.speciesName.isEmpty() ? " (" + entry.speciesName + ")" : "";
            text = text.copy().append(Component.literal("\n" + rankLabel + " ").withStyle(new ChatFormatting[]{rankColor, ChatFormatting.BOLD})).append(Component.literal(entry.playerName).withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD})).append(Component.literal(" \u279c ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(entry.bestCombo + "x").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})).append(Component.literal(species).withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}));
         }
      }

      text = text.copy().append(Component.literal("\n" + border).withStyle(ChatFormatting.DARK_GRAY));
      return text;
   }

   static {
      RANK_COLORS = new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.WHITE, ChatFormatting.YELLOW};
      cachedDisplays = List.of();
      cachedTop = List.of();
      lastCacheRefreshMillis = 0L;
      cacheRefreshInProgress = new AtomicBoolean(false);
   }
}
