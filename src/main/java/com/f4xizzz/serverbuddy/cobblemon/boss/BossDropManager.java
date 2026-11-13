package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.Random;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BossDropManager {
   private static final Random RANDOM = new Random();

   private BossDropManager() {
   }

   public static void register() {
      CobblemonEvents.LOOT_DROPPED.subscribe(Priority.NORMAL, (event) -> {
         handle(event);
         return Unit.INSTANCE;
      });
   }

   private static void handle(LootDroppedEvent event) {
      LivingEntity entity = event.getEntity();
      BossDefinition boss = BossEntityTracker.getBoss(entity.getUUID());
      if (boss != null) {
         event.cancel();
         BossDebug.log("[Boss] '{}': LOOT_DROPPED cancelled (the species drop table was replaced by boss.drops, {} configured entries).", boss.id, boss.drops.size());
         if (!boss.drops.isEmpty()) {
            Level var4 = entity.level();
            if (var4 instanceof ServerLevel) {
               ServerLevel world = (ServerLevel)var4;
               Vec3 var8 = entity.position();
               ServerPlayer player = event.getPlayer();

               for(BossDefinition.DropEntry dropDef : boss.drops) {
                  dropOne(boss, entity, world, var8, player, dropDef);
               }

               return;
            }
         }

      }
   }

   private static void dropOne(BossDefinition boss, LivingEntity entity, ServerLevel world, Vec3 pos, ServerPlayer player, BossDefinition.DropEntry dropDef) {
      if (dropDef.item != null && !dropDef.item.isBlank()) {
         if (!(RANDOM.nextFloat() * 100.0F >= dropDef.percentage)) {
            ResourceLocation itemId;
            try {
               itemId = ResourceLocation.parse(dropDef.item);
            } catch (Exception var11) {
               ServerBuddy.LOGGER.warn("[Boss] '{}': invalid drop item '{}', ignoring.", boss.id, dropDef.item);
               return;
            }

            int min = Math.min(dropDef.minQuantity, dropDef.maxQuantity);
            int max = Math.max(dropDef.minQuantity, dropDef.maxQuantity);
            int quantity = Math.max(1, min >= max ? min : min + RANDOM.nextInt(max - min + 1));
            ItemDropEntry entry = new ItemDropEntry();
            entry.setItem(itemId);
            entry.setQuantity(quantity);
            entry.drop(entity, world, pos, player);
         }
      }
   }
}
