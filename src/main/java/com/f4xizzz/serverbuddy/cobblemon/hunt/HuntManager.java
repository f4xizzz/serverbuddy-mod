package com.f4xizzz.serverbuddy.cobblemon.hunt;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.economy.EconomyManager;
import com.f4xizzz.serverbuddy.util.CommandRunner;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class HuntManager {
   private static final HuntPool SHARED_POOL = new HuntPool((UUID)null);
   private static final Map<UUID, HuntPool> PLAYER_POOLS = new HashMap<>();

   private HuntManager() {
   }

   public static void init() {
      PLAYER_POOLS.clear();
      SHARED_POOL.clear();
      if (!HuntConfigManager.getConfig().individual) {
         SHARED_POOL.ensureFilled();
      }

   }

   public static HuntPool getPoolFor(ServerPlayer player) {
      return !HuntConfigManager.getConfig().individual ? SHARED_POOL : (HuntPool)PLAYER_POOLS.computeIfAbsent(player.getUUID(), (id) -> {
         HuntPool pool = new HuntPool(id);
         pool.ensureFilled();
         return pool;
      });
   }

   public static List<HuntPool> getAllPools() {
      List<HuntPool> pools = new ArrayList<>();
      if (!HuntConfigManager.getConfig().individual) {
         pools.add(SHARED_POOL);
      } else {
         pools.addAll(PLAYER_POOLS.values());
      }

      return pools;
   }

   public static boolean resetPlayer(MinecraftServer server, UUID playerId) {
      if (!HuntConfigManager.getConfig().individual) {
         return false;
      } else {
         HuntPool novo = new HuntPool(playerId);
         novo.ensureFilled();
         PLAYER_POOLS.put(playerId, novo);
         return true;
      }
   }

   /**
    * Trata as cacadas que venceram na mesma checagem. Quando sao varias (e agruparAvisos esta ligado),
    * troca todas e manda UMA mensagem so ("as cacadas foram resetadas"), em vez de 2 mensagens por cacada.
    */
   public static void onExpiredBatch(MinecraftServer server, HuntPool pool, List<HuntTarget> expiredTargets) {
      HuntConfig config = HuntConfigManager.getConfig();
      if (expiredTargets.size() < 2 || !config.agruparAvisos) {
         for(HuntTarget target : expiredTargets) {
            onExpired(server, pool, target);
         }

         return;
      }

      boolean trocouAlguma = false;

      for(HuntTarget target : expiredTargets) {
         if (pool.replace(target.id) != null) {
            trocouAlguma = true;
         }
      }

      if (trocouAlguma && (config.avisarFim || config.avisarInicio)) {
         notify(server, pool, config.mensagens.resetadas);
      }
   }

   public static void onExpired(MinecraftServer server, HuntPool pool, HuntTarget expired) {
      HuntPool.Replacement replacement = pool.replace(expired.id);
      if (replacement != null) {
         HuntConfig config = HuntConfigManager.getConfig();
         if (config.avisarFim) {
            notify(server, pool, fill(config.mensagens.expirada, expired, (ServerPlayer)null));
         }

         if (replacement.added() != null && config.avisarInicio) {
            notify(server, pool, fill(config.mensagens.iniciada, replacement.added(), (ServerPlayer)null));
         }

      }
   }

   public static void onCaptured(MinecraftServer server, ServerPlayer player, HuntPool pool, HuntTarget completed) {
      HuntConfig config = HuntConfigManager.getConfig();
      HuntPool.Replacement replacement = pool.replace(completed.id);
      if (!config.individual) {
         server.getPlayerList().broadcastSystemMessage(MiniMessageUtil.parse(fill(config.mensagens.capturada, completed, player)), false);
      }

      if (completed.preco > 0.0) {
         if (EconomyManager.refund(player, config.moeda, completed.preco)) {
            player.displayClientMessage(MiniMessageUtil.parse(fill(config.mensagens.pagamento, completed, player)), false);
         } else {
            ServerBuddy.LOGGER.error("[Hunt] Failed to pay {} to {} (hunt {})!", new Object[]{completed.preco, player.getName().getString(), completed.id});
         }
      }

      if (completed.comandos != null && !completed.comandos.isEmpty()) {
         List<String> resolved = new ArrayList<>();

         for(String comando : completed.comandos) {
            resolved.add(comando.replace("%pokemon%", completed.pokemon.getSpecies().getName()).replace("%preco%", String.valueOf(completed.preco)));
         }

         CommandRunner.runAll(server, resolved, player.getName().getString());
      }

      if (replacement != null && replacement.added() != null && config.avisarInicio) {
         notify(server, pool, fill(config.mensagens.iniciada, replacement.added(), (ServerPlayer)null));
      }

   }

   private static void notify(MinecraftServer server, HuntPool pool, String text) {
      if (HuntConfigManager.getConfig().individual) {
         if (pool.getOwner() == null) {
            return;
         }

         ServerPlayer owner = server.getPlayerList().getPlayer(pool.getOwner());
         if (owner != null) {
            owner.displayClientMessage(MiniMessageUtil.parse(text), false);
         }
      } else {
         server.getPlayerList().broadcastSystemMessage(MiniMessageUtil.parse(text), false);
      }

   }

   private static String fill(String template, HuntTarget target, ServerPlayer jogador) {
      if (template == null) {
         return "";
      } else {
         String result = template.replace("{pokemon}", target.pokemon.getSpecies().getTranslatedName().getString()).replace("{preco}", String.valueOf(target.preco));
         return jogador != null ? result.replace("{jogador}", jogador.getName().getString()) : result;
      }
   }
}
