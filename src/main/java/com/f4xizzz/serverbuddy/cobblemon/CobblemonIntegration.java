package com.f4xizzz.serverbuddy.cobblemon;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossBattleManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossCaptureGuard;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossDropManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossEvents;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossInteractionHandler;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossLookTicker;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossRespawnScheduler;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossVisibilityTicker;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemTagTicker;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.BannedSpeciesConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaAccessGuard;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiSoundsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaOccupancyManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegionManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaSessionRepository;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaTicker;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RandomPoolManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboLeaderboardDisplayRepository;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboLeaderboardRepository;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboLeaderboardTicker;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboListener;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboRepository;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboSoundsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.features.CobblemonFeatures;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntCaptureListener;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntSpeciesIndex;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntTicker;
import com.f4xizzz.serverbuddy.cobblemon.poke.PokeEditor;
import com.f4xizzz.serverbuddy.cobblemon.command.CatchAreaCommand;
import com.f4xizzz.serverbuddy.cobblemon.command.CatchComboCommand;
import com.f4xizzz.serverbuddy.cobblemon.command.CobblemonAdminCommands;
import com.f4xizzz.serverbuddy.cobblemon.command.HuntCommand;
import com.f4xizzz.serverbuddy.cobblemon.command.PokeHealCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Tudo que usa a API do Cobblemon: cacadas, bosses, areas de captura, combo de captura, recompensas da Pokedex e
 * o editor/builder de Pokemon. O ServerBuddy so carrega esta classe quando o Cobblemon esta instalado, entao o
 * resto do mod funciona sozinho. Nao referencie nada deste pacote a partir de codigo que roda sem o Cobblemon.
 */
public final class CobblemonIntegration {
   private CobblemonIntegration() {
   }

   public static void init() {
      CatchComboConfigManager.loadConfig();
      CatchComboSoundsConfigManager.loadConfig();
      CatchComboListener.register();
      CatchComboLeaderboardTicker.register();
      BannedSpeciesConfigManager.loadConfig();
      CatchAreaRegionManager.loadAll();
      CatchAreaConfigManager.loadConfig();
      CatchAreaGuiSoundsConfigManager.loadConfig();
      CatchAreaGuiIconsConfigManager.loadConfig();
      CatchAreaAccessGuard.registerEvents();
      CatchAreaTicker.register();
      BossManager.loadAll();
      BossItemManager.loadAll();
      BossEvents.register();
      BossLookTicker.register();
      BossItemTagTicker.register();
      BossInteractionHandler.register();
      BossBattleManager.register();
      BossCaptureGuard.register();
      BossDropManager.register();
      BossRespawnScheduler.register();
      BossVisibilityTicker.register();
      HuntConfigManager.init();
      HuntCaptureListener.register();
      HuntTicker.register();
      // roda depois do callback do ServerBuddy (que abre o banco de dados)
      ServerLifecycleEvents.SERVER_STARTING.register((ServerLifecycleEvents.ServerStarting)(server) -> {
         CatchComboRepository.createTable();
         CatchComboManager.loadAll();
         CatchComboLeaderboardRepository.createTable();
         CatchComboLeaderboardDisplayRepository.createTable();
         RandomPoolManager.createTable();
         RandomPoolManager.load();
         CatchAreaSessionRepository.createTable();
         CatchAreaOccupancyManager.loadAll();
      });
      ServerLifecycleEvents.SERVER_STARTED.register((ServerLifecycleEvents.ServerStarted)(server) -> {
         HuntSpeciesIndex.refresh();
         HuntManager.init();
      });
      // roda depois do registro de /serverbuddy: os subcomandos de Pokemon entram nele
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         CatchComboCommand.register(dispatcher);
         CatchAreaCommand.register(dispatcher);
         PokeHealCommand.register(dispatcher);
         HuntCommand.register(dispatcher);
         CobblemonAdminCommands.register(dispatcher);
      });
      (new DexRewards()).onInitialize();
      PokeEditor.init();
      CobblemonFeatures.init();
      ServerBuddy.LOGGER.info("Cobblemon integration enabled.");
   }
}
