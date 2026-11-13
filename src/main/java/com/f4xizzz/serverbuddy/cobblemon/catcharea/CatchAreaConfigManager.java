package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class CatchAreaConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File DIR = ModPaths.subDir("CatchArea");
   private static final File CONFIG_FILE;
   private static CatchAreaConfig config;

   public static void loadConfig() {
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      if (!CONFIG_FILE.exists()) {
         config = new CatchAreaConfig();
         saveConfig();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               config = (CatchAreaConfig)GSON.fromJson(reader, CatchAreaConfig.class);
               if (config == null) {
                  config = new CatchAreaConfig();
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
            ServerBuddy.LOGGER.error("Error reading catcharea.conf. Using defaults!", e);
            config = new CatchAreaConfig();
         }
      }

   }

   public static void saveConfig() {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(config);
         File tmpFile = new File(DIR, "catcharea.conf.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         Files.move(tmpFile.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save catcharea.conf to disk!", e);
      }

   }

   public static CatchAreaConfig getConfig() {
      if (config == null) {
         loadConfig();
      }

      return config;
   }

   static {
      CONFIG_FILE = new File(DIR, "catcharea.conf");
   }
}
