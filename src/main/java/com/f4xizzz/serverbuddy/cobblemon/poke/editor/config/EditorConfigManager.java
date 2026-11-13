package com.f4xizzz.serverbuddy.cobblemon.poke.editor.config;

import com.f4xizzz.serverbuddy.cobblemon.poke.utils.TextUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.TooltipStrings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.kyori.adventure.key.Key;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EditorConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/PokeEditor").toFile();
   private static final File GUICONFIG_FILE;
   private static final File SERVERCONFIG_FILE;
   private static final File LANG_FILE;
   private static final File PRICES_FILE;
   private static final File SOUNDS_FILE;
   public static EditorServerConfigModel serverconfig;
   public static EditorGuiConfigModel guiconfig;
   public static EditorLangModel lang;
   public static EditorPricesModel prices;
   public static EditorSoundsModel sounds;

   public static void loadConfigs() {
      if (!CONFIG_DIR.exists()) {
         CONFIG_DIR.mkdirs();
      }

      serverconfig = (EditorServerConfigModel)loadOrGenerate(SERVERCONFIG_FILE, EditorServerConfigModel.class, new EditorServerConfigModel());
      guiconfig = (EditorGuiConfigModel)loadOrGenerate(GUICONFIG_FILE, EditorGuiConfigModel.class, new EditorGuiConfigModel());
      lang = (EditorLangModel)loadOrGenerate(LANG_FILE, EditorLangModel.class, new EditorLangModel());
      prices = (EditorPricesModel)loadOrGenerate(PRICES_FILE, EditorPricesModel.class, new EditorPricesModel());
      sounds = (EditorSoundsModel)loadOrGenerate(SOUNDS_FILE, EditorSoundsModel.class, new EditorSoundsModel());
   }

   public static void saveAll() {
      save(SERVERCONFIG_FILE, serverconfig);
      save(GUICONFIG_FILE, guiconfig);
      save(LANG_FILE, lang);
      save(PRICES_FILE, prices);
      save(SOUNDS_FILE, sounds);
   }

   private static <T> T loadOrGenerate(File file, Class<T> clazz, T defaultInstance) {
      if (file.exists()) {
         try {
            FileReader reader = new FileReader(file);

            Object var4;
            try {
               var4 = GSON.fromJson(reader, clazz);
            } catch (Throwable var7) {
               try {
                  reader.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }

               throw var7;
            }

            reader.close();
            return (T)var4;
         } catch (IOException e) {
            System.out.println("[PokeEditor] Error loading " + file.getName());
            e.printStackTrace();
         }
      }

      save(file, defaultInstance);
      return defaultInstance;
   }

   private static void save(File file, Object instance) {
      try {
         FileWriter writer = new FileWriter(file);

         try {
            GSON.toJson(instance, writer);
         } catch (Throwable var6) {
            try {
               writer.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         writer.close();
      } catch (IOException e) {
         System.out.println("[PokeEditor] Error saving " + file.getName());
         e.printStackTrace();
      }

   }

   public static Component parse(String text) {
      if (text != null && lang != null && lang.prefix != null) {
         text = text.replace("%prefix%", lang.prefix);
      }

      return TextUtils.parse(text);
   }

   public static Currency resolveCurrency(EconomyService economyService) {
      try {
         return (Currency)economyService.currencies().currency(Key.key(serverconfig.impactor_currency)).orElse(economyService.currencies().primary());
      } catch (Exception var2) {
         return economyService.currencies().primary();
      }
   }

   public static String formatCurrency(String rawValue) {
      return serverconfig.currency_prefix.replace("<balance>", rawValue);
   }

   public static Component parseWalletBalance(ServerPlayer player) {
      String currencyText = formatCurrency(serverconfig.wallet_balance_placeholder);
      String raw = lang.wallet_button_name.replace("<balance>", currencyText);
      Component minimessageText = parse(raw);
      return Placeholders.parseText(minimessageText, PlaceholderContext.of(player));
   }

   public static TooltipStrings tooltipStrings() {
      return new TooltipStrings(lang.word_yes, lang.word_no, lang.word_none, lang.tooltip_gender_male, lang.tooltip_gender_female, lang.tooltip_gender_none, lang.pokemon_tooltip_ha_suffix);
   }

   static {
      GUICONFIG_FILE = new File(CONFIG_DIR, "guiconfig.json");
      SERVERCONFIG_FILE = new File(CONFIG_DIR, "serverconfig.json");
      LANG_FILE = new File(CONFIG_DIR, "lang.json");
      PRICES_FILE = new File(CONFIG_DIR, "prices.json");
      SOUNDS_FILE = new File(CONFIG_DIR, "sounds.json");
   }
}
