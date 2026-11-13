package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class BannedSpeciesConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File DIR = ModPaths.subDir("CatchArea");
   private static final File CONFIG_FILE;
   private static BannedSpeciesConfig config;

   public static void loadConfig() {
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      if (!CONFIG_FILE.exists()) {
         config = new BannedSpeciesConfig();
         saveConfig();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               config = (BannedSpeciesConfig)GSON.fromJson(reader, BannedSpeciesConfig.class);
               if (config == null) {
                  config = new BannedSpeciesConfig();
               }
            } catch (Throwable var4) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            reader.close();
         } catch (Exception e) {
            ServerBuddy.LOGGER.error("Error reading banned_species.conf. Using defaults!", e);
            config = new BannedSpeciesConfig();
         }
      }

   }

   public static void saveConfig() {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(config);
         File tmpFile = new File(DIR, "banned_species.conf.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         Files.move(tmpFile.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save banned_species.conf to disk!", e);
      }

   }

   public static BannedSpeciesConfig getConfig() {
      if (config == null) {
         loadConfig();
      }

      return config;
   }

   public static boolean isBlocked(Species species) {
      BannedSpeciesConfig cfg = getConfig();
      Set<String> labels = species.getLabels();
      if (cfg.blockLegendary && labels.contains("legendary")) {
         return true;
      } else if (cfg.blockMythical && labels.contains("mythical")) {
         return true;
      } else if (cfg.blockUltraBeast && labels.contains("ultra_beast")) {
         return true;
      } else if (cfg.blockParadox && labels.contains("paradox")) {
         return true;
      } else {
         String id = species.getResourceIdentifier().toString();

         for(String blocked : cfg.blockedSpecies) {
            if (blocked.equalsIgnoreCase(id) || blocked.equalsIgnoreCase(species.getName())) {
               return true;
            }
         }

         return false;
      }
   }

   static {
      CONFIG_FILE = new File(DIR, "banned_species.conf");
   }
}
