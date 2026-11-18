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

      if (config.messages == null) {
         config.messages = new RedeemMessages();
      }

      if (config.codes == null) {
         config.codes = new LinkedHashMap<>();
      }

      validate();
   }

   /**
    * Finds a code by what the player typed (capital letters do not matter). Returns the name as it is written in the config (claims are
    * kept under that name), or null when there is no such code.
    */
   public static String canonicalName(String typed) {
      return RedeemRules.canonicalName(getConfig().codes.keySet(), typed);
   }

   /** The expiration of a code (never, a moment, or invalid when the text cannot be read). */
   public static RedeemRules.Expiry expiryOf(RedeemCode code) {
      return RedeemRules.expiry(code.expiresAt);
   }

   /** Tells the admin about what cannot work: a code with no definition, or an expiration that cannot be read. */
   private static void validate() {
      config.codes.entrySet().removeIf((entry) -> {
         if (entry.getValue() == null) {
            ServerBuddy.LOGGER.warn("[Redeem] The code '{}' has no definition in redeem.conf and was ignored.", entry.getKey());
            return true;
         }
         return false;
      });
      for(java.util.Map.Entry<String, RedeemCode> entry : config.codes.entrySet()) {
         RedeemRules.Expiry expiry = expiryOf(entry.getValue());
         if (expiry instanceof RedeemRules.Expiry.Invalid invalid) {
            ServerBuddy.LOGGER.warn("[Redeem] The code '{}' has an expires_at that cannot be read ('{}'), so nobody can redeem it. Use 2026-12-31, 2026-12-31 18:30 or 2026-12-31T18:30:00Z.", entry.getKey(), invalid.getText());
         }
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
      example.maxUses = 0;
      example.permission = null;
      example.commands = List.of("give %player% minecraft:golden_apple 1");
      cfg.codes.put("welcome", example);
      return cfg;
   }

   static {
      CONFIG_FILE = new File(DIR, "redeem.conf");
   }
}
