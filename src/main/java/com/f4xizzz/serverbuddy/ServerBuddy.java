package com.f4xizzz.serverbuddy;

import com.f4xizzz.serverbuddy.cobblemon.CobblemonIntegration;
import com.f4xizzz.serverbuddy.command.RedeemCommand;
import com.f4xizzz.serverbuddy.command.ServerBuddyCommand;
import com.f4xizzz.serverbuddy.core.Integrations;
import com.f4xizzz.serverbuddy.core.LegacyImport;
import com.f4xizzz.serverbuddy.core.Modules;
import com.f4xizzz.serverbuddy.dashboard.DashboardProviders;
import com.f4xizzz.serverbuddy.dashboard.LogBuffer;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import com.f4xizzz.serverbuddy.events.EventConfigManager;
import com.f4xizzz.serverbuddy.events.EventManager;
import com.f4xizzz.serverbuddy.events.EventTicker;
import com.f4xizzz.serverbuddy.flan.FlanIntegration;
import com.f4xizzz.serverbuddy.gamerules.GameRuleEnforcer;
import com.f4xizzz.serverbuddy.dashboard.DashboardCommands;
import com.f4xizzz.serverbuddy.logs.AltAccountTracker;
import com.f4xizzz.serverbuddy.logs.DeathLogger;
import com.f4xizzz.serverbuddy.logs.LogRotator;
import com.f4xizzz.serverbuddy.logs.LogsSession;
import com.f4xizzz.serverbuddy.logs.ServerConfigManager;
import com.f4xizzz.serverbuddy.logs.player.ConsoleEchoAppender;
import com.f4xizzz.serverbuddy.logs.player.PlayerSessionLogManager;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemManager;
import com.f4xizzz.serverbuddy.redeem.RedeemRepository;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerBuddy implements ModInitializer {
   public static final String MOD_ID = "serverbuddy";
   public static final Logger LOGGER = LoggerFactory.getLogger("serverbuddy");

   public void onInitialize() {
      LogBuffer.install();
      DashboardProviders.init();
      LegacyImport.importGreatServerUtils();
      ServerConfigManager.loadConfig();
      EventConfigManager.init();
      EventManager.init();
      EventTicker.register();
      RedeemConfigManager.init();
      AltAccountTracker.load();
      PlayerSessionLogManager.register();
      DeathLogger.register();
      GameRuleEnforcer.register();
      FlanIntegration.register();
      DashboardCommands.registerNotices();
      ServerLifecycleEvents.SERVER_STARTING.register((ServerLifecycleEvents.ServerStarting)(server) -> {
         DatabaseManager.init();
         RedeemRepository.createTable();
         RedeemManager.loadAll();
         LogsSession.computeSessionName();
         LogRotator.rotateStale();
         LogsSession.createDirectories();
         ConsoleEchoAppender.install();
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerLifecycleEvents.ServerStopping)(server) -> {
         DatabaseManager.shutdown();
         ConsoleEchoAppender.uninstall();
         LogRotator.rotateStale();
      });
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         ServerBuddyCommand.register(dispatcher);
         RedeemCommand.register(dispatcher);
      });
      Modules.initAll();
      if (Integrations.cobblemonFeatures()) {
         CobblemonIntegration.init();
      } else {
         LOGGER.info("Cobblemon not found: the Pokémon features (hunts, bosses, catch areas, combo, Pokédex, editor) stay off. The rest of ServerBuddy works normally.");
      }

      LOGGER.info("ServerBuddy inicializado!");
   }
}
