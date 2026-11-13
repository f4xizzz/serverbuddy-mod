package com.f4xizzz.serverbuddy.redeem;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.f4xizzz.serverbuddy.core.SbGson;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class RedeemConfigManager {
   // o mesmo Gson dos menus novos: os itens do codigo ("itens") aceitam "components" como nos outros menus
   private static final Gson GSON = SbGson.INSTANCE.getPretty();
   private static final File DIR = ModPaths.subDir("Redeem");
   private static final File CONFIG_FILE;
   private static RedeemConfig config;

   private RedeemConfigManager() {
   }

   public static void init() {
      loadConfig();
   }

   public static void loadConfig() {
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      if (!CONFIG_FILE.exists()) {
         config = createDefaultConfig();
         saveConfig();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               config = (RedeemConfig)GSON.fromJson(reader, RedeemConfig.class);
               if (config == null) {
                  config = createDefaultConfig();
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
            ServerBuddy.LOGGER.error("Error reading redeem.conf. Using defaults!", e);
            config = createDefaultConfig();
         }
      }

      if (config.aliases == null || config.aliases.isEmpty()) {
         config.aliases = new ArrayList<>(List.of("redeem"));
      }

      if (config.mensagens == null) {
         config.mensagens = new RedeemMessages();
      }

      if (config.codigos == null) {
         config.codigos = new LinkedHashMap<>();
      }

   }

   public static void saveConfig() {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(config);
         File tmpFile = new File(DIR, "redeem.conf.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         Files.move(tmpFile.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save redeem.conf to disk!", e);
      }

   }

   public static RedeemConfig getConfig() {
      if (config == null) {
         loadConfig();
      }

      return config;
   }

   private static RedeemConfig createDefaultConfig() {
      RedeemConfig cfg = new RedeemConfig();
      RedeemCode example = new RedeemCode();
      example.max_usos = 0;
      example.permissao = null;
      example.comandos = List.of("give %player% minecraft:golden_apple 1");
      cfg.codigos.put("boasvindas", example);
      return cfg;
   }

   static {
      CONFIG_FILE = new File(DIR, "redeem.conf");
   }
}
