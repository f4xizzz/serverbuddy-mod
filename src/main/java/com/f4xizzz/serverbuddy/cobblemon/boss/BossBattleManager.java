package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.util.CommandRunner;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class BossBattleManager {
   private static final Map<UUID, ActiveBossBattle> ACTIVE_BATTLES = new HashMap<>();

   private BossBattleManager() {
   }

   public static void register() {
      CobblemonEvents.BATTLE_STARTED_POST.subscribe(BossBattleManager::onBattleStarted);
      CobblemonEvents.BATTLE_FLED.subscribe(BossBattleManager::onBattleFled);
      CobblemonEvents.BATTLE_VICTORY.subscribe(BossBattleManager::onBattleVictory);
   }

   static Collection<ActiveBossBattle> getActiveBattles() {
      return ACTIVE_BATTLES.values();
   }

   public static ActiveBossBattle findActiveBattle(UUID playerId, String bossId) {
      for(ActiveBossBattle active : ACTIVE_BATTLES.values()) {
         if (active.player().getUUID().equals(playerId) && active.boss().id.equals(bossId)) {
            return active;
         }
      }

      return null;
   }

   public static boolean isInActiveBattle(String bossId) {
      for(ActiveBossBattle active : ACTIVE_BATTLES.values()) {
         if (active.boss().id.equals(bossId)) {
            return true;
         }
      }

      return false;
   }

   private static void onBattleStarted(BattleStartedEvent.Post event) {
      PokemonBattle battle = event.getBattle();
      BossDebug.log("[Boss] BATTLE_STARTED_POST recebido \u2014 battleId={}.", battle.getBattleId());

      for(BattleActor actor : battle.getActors()) {
         if (actor instanceof PokemonBattleActor pokemonActor) {
            PokemonEntity wildEntity = pokemonActor.getEntity();
            BossDefinition boss = BossEntityTracker.getBoss(wildEntity.getUUID());
            if (boss != null) {
               List<ServerPlayer> players = battle.getPlayers();
               if (players.isEmpty()) {
                  ServerBuddy.LOGGER.warn("[Boss] battleId={} \u2014 boss '{}' identified but battle.getPlayers() is empty! Not registering the active battle.", battle.getBattleId(), boss.id);
                  return;
               }

               BattlePokemon bossBattlePokemon = pokemonActor.getPokemon();
               ACTIVE_BATTLES.put(battle.getBattleId(), new ActiveBossBattle(boss, (ServerPlayer)players.get(0), battle, bossBattlePokemon));
               BossDebug.log("[Boss] battleId={} \u2014 batalha registrada: boss='{}', player='{}', hp={}/{}. ACTIVE_BATTLES agora tem {} entrada(s).", battle.getBattleId(), boss.id, ((ServerPlayer)players.get(0)).getName().getString(), bossBattlePokemon.getHealth(), bossBattlePokemon.getMaxHealth(), ACTIVE_BATTLES.size());
               return;
            }

            BossDebug.log("[Boss] battleId={} \u2014 PokemonEntity uuid={} is not a tracked boss, ignoring.", battle.getBattleId(), wildEntity.getUUID());
         } else {
            BossDebug.log("[Boss] battleId={} \u2014 actor '{}' is not a PokemonBattleActor (type={}), ignoring.", battle.getBattleId(), actor.getUuid(), actor.getClass().getSimpleName());
         }
      }

   }

   private static void onBattleFled(BattleFledEvent event) {
      PokemonBattle battle = event.getBattle();
      ActiveBossBattle active = (ActiveBossBattle)ACTIVE_BATTLES.remove(battle.getBattleId());
      if (active == null) {
         BossDebug.log("[Boss] BATTLE_FLED received for battleId={}, but it is not a tracked boss battle (player='{}').", battle.getBattleId(), event.getPlayer().getUuid());
      } else {
         ServerBuddy.LOGGER.warn("[Boss] FLED! battleId={}, boss='{}', player='{}' \u2014 the battle ended in a flee (Cobblemon's checkFlee). This should NOT happen by itself with fleeDistance=-1f; if you see this, the player used the Run/forfeit button. The boss is still alive, no respawn scheduled.", new Object[]{battle.getBattleId(), active.boss().id, active.player().getName().getString()});
      }
   }

   private static void onBattleVictory(BattleVictoryEvent event) {
      PokemonBattle battle = event.getBattle();
      BossDebug.log("[Boss] BATTLE_VICTORY recebido \u2014 battleId={}, wasWildCapture={}, winners={}, losers={}.", battle.getBattleId(), event.getWasWildCapture(), event.getWinners().size(), event.getLosers().size());
      ActiveBossBattle active = (ActiveBossBattle)ACTIVE_BATTLES.remove(battle.getBattleId());
      if (active == null) {
         BossDebug.log("[Boss] battleId={} is not a tracked boss battle, ignoring BATTLE_VICTORY.", battle.getBattleId());
      } else {
         ServerPlayer player = active.player();
         MinecraftServer server = player.getServer();
         if (server == null) {
            ServerBuddy.LOGGER.warn("[Boss] battleId={} \u2014 boss='{}', but player.getServer() is null, not running the commands!", battle.getBattleId(), active.boss().id);
         } else {
            boolean captured = event.getWasWildCapture();
            List<String> commands;
            String outcome;
            if (captured) {
               commands = active.boss().victoryCommands;
               outcome = "capturado -> victoryCommands";
               BossCooldownManager.recordVictory(player.getUUID(), active.boss());
            } else {
               BattleActor playerActor = findPlayerActor(battle, player);
               boolean playerLost = playerActor != null && event.getLosers().contains(playerActor);
               if (!playerLost) {
                  commands = active.boss().defeatCommands;
                  outcome = "boss defeated without capture -> defeatCommands";
                  BossCooldownManager.recordVictory(player.getUUID(), active.boss());
               } else if (allPokemonFainted(playerActor)) {
                  commands = active.boss().lossCommands;
                  outcome = "player perdeu (party inteira desmaiada) -> lossCommands";
               } else {
                  commands = null;
                  outcome = "player forfeited/left without the party fainting -> no command runs";
               }
            }

            BossDebug.log("[Boss] battleId={} \u2014 boss='{}' encerrado: {}.", battle.getBattleId(), active.boss().id, outcome);
            if (commands != null && !commands.isEmpty()) {
               CommandRunner.runAll(server, commands, player.getName().getString());
            }

            BossDebug.log("[Boss] battleId={} \u2014 agendando respawn de '{}' (delay={}s).", battle.getBattleId(), active.boss().id, active.boss().respawnDelaySeconds);
            BossRespawnScheduler.schedule(active.boss(), server);
         }
      }
   }

   private static BattleActor findPlayerActor(PokemonBattle battle, ServerPlayer player) {
      for(BattleActor actor : battle.getActors()) {
         if (actor.isForPlayer(player)) {
            return actor;
         }
      }

      return null;
   }

   private static boolean allPokemonFainted(BattleActor playerActor) {
      List<BattlePokemon> party = playerActor.getPokemonList();
      if (party.isEmpty()) {
         return false;
      } else {
         for(BattlePokemon mon : party) {
            if (mon.getHealth() > 0) {
               return false;
            }
         }

         return true;
      }
   }

   static record ActiveBossBattle(BossDefinition boss, ServerPlayer player, PokemonBattle battle, BattlePokemon bossBattlePokemon) {
   }
}
