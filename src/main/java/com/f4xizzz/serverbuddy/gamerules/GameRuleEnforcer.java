package com.f4xizzz.serverbuddy.gamerules;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.logs.ServerConfig;
import com.f4xizzz.serverbuddy.logs.ServerConfigManager;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public class GameRuleEnforcer {
   private static Map<String, GameRules.Key<?>> keysByName;

   private GameRuleEnforcer() {
   }

   public static void register() {
      ServerLifecycleEvents.SERVER_STARTED.register(GameRuleEnforcer::enforce);
      ServerTickEvents.END_SERVER_TICK.register((ServerTickEvents.EndTick)(server) -> {
         if (server.getTickCount() % 20 == 0) {
            enforce(server);
         }
      });
   }

   private static void enforce(MinecraftServer server) {
      ServerConfig config = ServerConfigManager.getConfig();
      if (config.forceGameRules && config.forcedGameRules != null && !config.forcedGameRules.isEmpty()) {
         for(ServerLevel world : server.getAllLevels()) {
            GameRules rules = world.getGameRules();

            for(Map.Entry<String, String> entry : config.forcedGameRules.entrySet()) {
               forceRule(rules, (String)entry.getKey(), (String)entry.getValue(), server);
            }
         }

      }
   }

   private static void forceRule(GameRules rules, String ruleName, String desiredValue, MinecraftServer server) {
      if (ruleName != null && !ruleName.isBlank() && desiredValue != null) {
         GameRules.Key<?> key = resolveKey(rules, ruleName.trim());
         if (key == null) {
            ServerBuddy.LOGGER.warn("[GameRules] '{}' is not a valid gamerule (check forcedGameRules in serverconfig.conf)!", ruleName);
         } else {
            GameRules.Value<?> rule = rules.getRule(key);
            if (rule instanceof GameRules.BooleanValue) {
               GameRules.BooleanValue booleanRule = (GameRules.BooleanValue)rule;
               boolean desired = Boolean.parseBoolean(desiredValue.trim());
               if (booleanRule.get() != desired) {
                  booleanRule.set(desired, server);
               }
            } else if (rule instanceof GameRules.IntegerValue) {
               GameRules.IntegerValue intRule = (GameRules.IntegerValue)rule;

               try {
                  int desired = Integer.parseInt(desiredValue.trim());
                  if (intRule.get() != desired) {
                     intRule.set(desired, server);
                  }
               } catch (NumberFormatException var9) {
                  ServerBuddy.LOGGER.warn("[GameRules] '{}' is a numeric gamerule, but the configured value '{}' is not a number!", ruleName, desiredValue);
               }
            }

         }
      }
   }

   private static GameRules.Key<?> resolveKey(GameRules rules, String name) {
      if (keysByName == null) {
         final Map<String, GameRules.Key<?>> found = new HashMap<>();
         GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
               found.put(key.getId(), key);
            }
         });
         keysByName = found;
      }

      return (GameRules.Key)keysByName.get(name);
   }
}
