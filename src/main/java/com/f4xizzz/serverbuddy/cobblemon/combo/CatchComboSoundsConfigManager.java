package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class CatchComboSoundsConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File DIR = ModPaths.subDir("CatchCombo");
   private static final File CONFIG_FILE;
   private static CatchComboSoundsConfig config;

   public static void loadConfig() {
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      if (!CONFIG_FILE.exists()) {
         config = new CatchComboSoundsConfig();
         saveConfig();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               config = (CatchComboSoundsConfig)GSON.fromJson(reader, CatchComboSoundsConfig.class);
               if (config == null) {
                  config = new CatchComboSoundsConfig();
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
            ServerBuddy.LOGGER.error("Error reading combo_sounds.conf. Using defaults!", e);
            config = new CatchComboSoundsConfig();
         }
      }

   }

   public static void saveConfig() {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(config);
         File tmpFile = new File(DIR, "combo_sounds.conf.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         Files.move(tmpFile.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save combo_sounds.conf to disk!", e);
      }

   }

   public static CatchComboSoundsConfig getConfig() {
      if (config == null) {
         loadConfig();
      }

      return config;
   }

   static {
      CONFIG_FILE = new File(DIR, "combo_sounds.conf");
   }
}
