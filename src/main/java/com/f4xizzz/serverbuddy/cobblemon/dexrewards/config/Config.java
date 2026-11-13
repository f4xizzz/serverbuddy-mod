package com.f4xizzz.serverbuddy.cobblemon.dexrewards.config;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.database.DexRewardsDatabase;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.Messages;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.PlayerData;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.guielements.GuiElement;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.guielements.GuiLayout;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.CommandReward;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.ItemReward;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.Reward;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class Config {
   public boolean use_mysql = false;
   public String mysql_host = "localhost";
   public int mysql_port = 3306;
   public String mysql_database = "serverbuddy";
   public String mysql_user = "root";
   public String mysql_password = "password";
   public boolean implemented_only = true;
   public boolean allow_invalid_species = false;
   public List<Species> species_blacklist = new ArrayList<>();
   public List<String> label_blacklist = new ArrayList<>();
   public List<String> generation_blacklist = new ArrayList<>();
   public List<RewardGroup> reward_groups = new ArrayList<>();
   public List<GuiElement> gui_elements = new ArrayList<>();
   public GuiLayout gui_layout;
   public ConcurrentHashMap<UUID, PlayerData> player_data = new ConcurrentHashMap<>();

   public Config() {
      this.createFilesIfMissing();
      this.loadConfig();
      this.loadRewards();
      this.loadMessages();
   }

   private void createFilesIfMissing() {
      File mainDir = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards").toFile();
      if (!mainDir.exists()) {
         mainDir.mkdirs();
      }

      File configFile = new File(mainDir, "config.json");
      if (!configFile.exists()) {
         try {
            Writer writer = new FileWriter(configFile);

            try {
               String defaultJson = "{\n  \"use_mysql\": false,\n  \"mysql_host\": \"localhost\",\n  \"mysql_port\": 3306,\n  \"mysql_database\": \"serverbuddy\",\n  \"mysql_user\": \"root\",\n  \"mysql_password\": \"password\",\n  \"debug\": false,\n  \"implemented_only\": true,\n  \"allow_invalid_species\": false,\n  \"blacklist\": {\n    \"species\": [],\n    \"label\": [],\n    \"generation\": []\n  }\n}";
               writer.write(defaultJson);
            } catch (Throwable var15) {
               try {
                  writer.close();
               } catch (Throwable var14) {
                  var15.addSuppressed(var14);
               }

               throw var15;
            }

            writer.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      File messagesFile = new File(mainDir, "messages.json");
      if (!messagesFile.exists()) {
         try {
            Writer writer = new FileWriter(messagesFile);

            try {
               String defaultJson = "{\n  \"prefix\": \"<dark_gray>[<blue>DexRewards<dark_gray>]\",\n  \"messages\": {\n    \"rewards_to_claim\": \"%prefix% You have rewards to claim!\",\n    \"reward_claimable\": \"%prefix% You can claim the %group.name% rewards!\",\n    \"rewards_claimed\": \"%prefix% You've claimed rewards for %group.name%!\",\n    \"reset_command_sender\": \"%prefix% You've reset %player%'s pokedex reward progress!\",\n    \"reset_command_target\": \"%prefix% Your pokedex reward progress has been reset!\",\n    \"update_command\": \"%prefix% You've updated the pokedex reward progress of %player%\",\n    \"reload_command\": \"%prefix% Reloaded!\"\n  },\n  \"gui\": {\n    \"title\": \"Pokedex Rewards\",\n    \"size\": 54,\n    \"page_layout\": [\n      \"#########\",\n      \"#-------#\",\n      \"#-------#\",\n      \"#-------#\",\n      \"#-------#\",\n      \"#<#####>#\"\n    ],\n    \"page_layout_symbols\": {\n      \"background\": \"#\",\n      \"player_info\": \"P\",\n      \"group\": \"-\",\n      \"previous_page\": \"<\",\n      \"next_page\": \">\"\n    },\n    \"background\": {\n      \"item\": \"minecraft:gray_stained_glass_pane\",\n      \"name\": \" \",\n      \"lore\": []\n    },\n    \"navigation\": {\n      \"previous_item\": \"minecraft:arrow\",\n      \"previous_name\": \"Previous Page\",\n      \"previous_lore\": [],\n      \"next_item\": \"minecraft:arrow\",\n      \"next_name\": \"Next Page\",\n      \"next_lore\": []\n    },\n    \"player_info\": {\n      \"name\": \"Player Info\",\n      \"lore\": [\"Caught: %player.caught_count%\", \"Percent: %player.caught_percent%%\"],\n      \"show_enchantment_glint\": false\n    },\n    \"claimed_group\": {\n      \"name\": \"Claimed\",\n      \"lore\": [\"Already claimed!\"],\n      \"show_enchantment_glint\": true\n    },\n    \"claimable_group\": {\n      \"name\": \"Claimable\",\n      \"lore\": [\"Click to claim!\"],\n      \"show_enchantment_glint\": true\n    },\n    \"locked_group\": {\n      \"name\": \"Locked\",\n      \"lore\": [\"Keep catching!\"],\n      \"show_enchantment_glint\": false\n    }\n  }\n}";
               writer.write(defaultJson);
            } catch (Throwable var12) {
               try {
                  writer.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }

               throw var12;
            }

            writer.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      File rewardsFile = new File(mainDir, "rewards.json");
      if (!rewardsFile.exists()) {
         try {
            Writer writer = new FileWriter(rewardsFile);

            try {
               String defaultJson = "{\n  \"reward_groups\": {\n    \"Bronze\": {\n      \"icon\": \"minecraft:copper_ingot\",\n      \"required_caught_percent\": 10.0,\n      \"rewards\": {\n        \"reward_1\": {\n          \"type\": \"item\",\n          \"item\": \"minecraft:diamond\",\n          \"count\": 1\n        }\n      }\n    }\n  }\n}";
               writer.write(defaultJson);
            } catch (Throwable var9) {
               try {
                  writer.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }

               throw var9;
            }

            writer.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

   }

   private JsonObject getRoot(File file) {
      try {
         if (!file.exists() || file.length() == 0L) {
            return new JsonObject();
         }

         JsonElement root_element = JsonParser.parseReader(new FileReader(file));
         if (root_element != null && root_element.isJsonObject()) {
            return root_element.getAsJsonObject();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return new JsonObject();
   }

   private void loadConfig() {
      File config_file = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/config.json").toFile();
      JsonObject root = this.getRoot(config_file);
      if (root != null) {
         if (root.has("use_mysql")) {
            this.use_mysql = root.get("use_mysql").getAsBoolean();
         }

         if (root.has("mysql_host")) {
            this.mysql_host = root.get("mysql_host").getAsString();
         }

         if (root.has("mysql_port")) {
            this.mysql_port = root.get("mysql_port").getAsInt();
         }

         if (root.has("mysql_database")) {
            this.mysql_database = root.get("mysql_database").getAsString();
         }

         if (root.has("mysql_user")) {
            this.mysql_user = root.get("mysql_user").getAsString();
         }

         if (root.has("mysql_password")) {
            this.mysql_password = root.get("mysql_password").getAsString();
         }

         if (root.has("debug")) {
            DexRewards.DEBUG = root.get("debug").getAsBoolean();
         }

         if (root.has("implemented_only")) {
            this.implemented_only = root.get("implemented_only").getAsBoolean();
         }

         if (root.has("allow_invalid_species")) {
            this.allow_invalid_species = root.get("allow_invalid_species").getAsBoolean();
         }

         if (root.has("blacklist") && root.get("blacklist").isJsonObject()) {
            JsonObject blacklist = root.get("blacklist").getAsJsonObject();
            if (blacklist.has("species") && blacklist.get("species").isJsonArray()) {
               for(JsonElement element : blacklist.get("species").getAsJsonArray()) {
                  String species_id = element.getAsString();
                  PokemonSpecies var10000 = PokemonSpecies.INSTANCE;
                  Species species = PokemonSpecies.getByName(species_id);
                  if (species != null) {
                     this.species_blacklist.add(species);
                  }
               }
            }

            if (blacklist.has("label") && blacklist.get("label").isJsonArray()) {
               this.label_blacklist = blacklist.get("label").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
            }

            if (blacklist.has("generation") && blacklist.get("generation").isJsonArray()) {
               this.generation_blacklist = blacklist.get("generation").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
            }
         }
      }

      this.saveConfigDatabaseKeys(config_file, root);
   }

   private void saveConfigDatabaseKeys(File config_file, JsonObject root) {
      if (root != null && !root.has("use_mysql")) {
         try {
            root.addProperty("use_mysql", this.use_mysql);
            root.addProperty("mysql_host", this.mysql_host);
            root.addProperty("mysql_port", this.mysql_port);
            root.addProperty("mysql_database", this.mysql_database);
            root.addProperty("mysql_user", this.mysql_user);
            root.addProperty("mysql_password", this.mysql_password);
            Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
            Writer writer = new FileWriter(config_file);
            gson.toJson(root, writer);
            writer.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

   }

   private void loadRewards() {
      File rewards_file = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/rewards.json").toFile();
      JsonObject root = this.getRoot(rewards_file);
      if (root != null) {
         JsonObject reward_groups = root.get("reward_groups").getAsJsonObject();
         List<RewardGroup> rewardGroups = new ArrayList<>();

         for(String key : reward_groups.keySet()) {
            JsonObject reward_group = reward_groups.get(key).getAsJsonObject();
            String icon_id = reward_group.get("icon").getAsString();
            ItemStack icon = ((Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(icon_id))).getDefaultInstance();
            JsonElement icon_data = reward_group.get("icon_data");
            if (icon_data != null) {
               icon.applyComponentsAndValidate((DataComponentPatch)((Pair)DataComponentPatch.CODEC.decode(JsonOps.INSTANCE, icon_data).getOrThrow()).getFirst());
            }

            double required_percent = reward_group.get("required_caught_percent").getAsDouble();
            JsonObject rewards_object = reward_group.get("rewards").getAsJsonObject();
            List<Reward> rewards = new ArrayList<>();

            for(String reward_key : rewards_object.keySet()) {
               JsonObject reward = rewards_object.get(reward_key).getAsJsonObject();
               String type = reward.get("type").getAsString();
               if (type.equalsIgnoreCase("item")) {
                  String item_id = reward.get("item").getAsString();
                  int count = reward.get("count").getAsInt();
                  Item item_obj = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(item_id));
                  ItemStack item = new ItemStack(item_obj, count);
                  JsonElement item_data = reward.get("item_data");
                  if (item_data != null) {
                     item.applyComponentsAndValidate((DataComponentPatch)((Pair)DataComponentPatch.CODEC.decode(JsonOps.INSTANCE, item_data).getOrThrow()).getFirst());
                  }

                  rewards.add(new ItemReward(reward_key, type, item));
               } else if (type.equalsIgnoreCase("command")) {
                  List<String> commands = reward.get("commands").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
                  rewards.add(new CommandReward(reward_key, type, commands));
               }
            }

            rewardGroups.add(new RewardGroup(key, icon, required_percent, rewards));
         }

         this.reward_groups = rewardGroups;
      }

   }

   private void loadMessages() {
      File rewards_file = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/messages.json").toFile();
      JsonObject root = this.getRoot(rewards_file);
      if (root != null) {
         Messages.prefix = root.get("prefix").getAsString();
         JsonObject messages = root.get("messages").getAsJsonObject();
         Messages.rewards_to_claim = messages.get("rewards_to_claim").getAsString();
         Messages.reward_claimable = messages.get("reward_claimable").getAsString();
         Messages.rewards_claimed = messages.get("rewards_claimed").getAsString();
         Messages.update_command = messages.get("update_command").getAsString();
         Messages.reset_sender = messages.get("reset_command_sender").getAsString();
         Messages.reset_target = messages.get("reset_command_target").getAsString();
         Messages.reload = messages.get("reload_command").getAsString();
         JsonObject gui = root.get("gui").getAsJsonObject();
         String title = gui.get("title").getAsString();
         int size = gui.get("size").getAsInt();
         List<String> page_layout = gui.get("page_layout").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
         JsonObject page_layout_symbols = gui.get("page_layout_symbols").getAsJsonObject();
         String background_symbol = page_layout_symbols.get("background").getAsString();
         String player_info_symbol = page_layout_symbols.get("player_info").getAsString();
         String group_symbol = page_layout_symbols.get("group").getAsString();
         String previous_page_symbol = page_layout_symbols.get("previous_page").getAsString();
         String next_page_symbol = page_layout_symbols.get("next_page").getAsString();
         JsonObject background = gui.get("background").getAsJsonObject();
         String background_item_id = background.get("item").getAsString();
         String background_name = background.get("name").getAsString();
         List<String> background_lore = background.get("lore").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
         JsonElement background_item_data = background.get("item_data");
         ItemStack background_item = this.generateItem(background_item_id, background_name, background_lore, background_item_data);
         JsonObject navigation = gui.get("navigation").getAsJsonObject();
         String previous_item_id = navigation.get("previous_item").getAsString();
         String previous_name = navigation.get("previous_name").getAsString();
         List<String> previous_lore = navigation.get("previous_lore").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
         JsonElement previous_item_data = navigation.get("previous_item_data");
         ItemStack previous_item = this.generateItem(previous_item_id, previous_name, previous_lore, previous_item_data);
         String next_item_id = navigation.get("next_item").getAsString();
         String next_name = navigation.get("next_name").getAsString();
         List<String> next_lore = navigation.get("next_lore").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
         JsonElement next_item_data = navigation.get("next_item_data");
         ItemStack next_item = this.generateItem(next_item_id, next_name, next_lore, next_item_data);
         String[] elements = new String[]{"player_info", "claimed_group", "claimable_group", "locked_group"};

         for(String element : elements) {
            JsonObject element_object = gui.get(element).getAsJsonObject();
            String name = element_object.get("name").getAsString();
            List<String> lore = element_object.get("lore").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
            boolean glint = element_object.get("show_enchantment_glint").getAsBoolean();
            this.gui_elements.add(new GuiElement(element, name, lore, glint));
         }

         this.gui_layout = new GuiLayout(title, size, page_layout, background_symbol, player_info_symbol, group_symbol, previous_page_symbol, next_page_symbol, background_item, previous_item, next_item);
      }

   }

   private ItemStack generateItem(String id, String name, List<String> lore, JsonElement item_data) {
      ItemStack stack = ((Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))).getDefaultInstance();
      List<Component> lore_text = new ArrayList<>();

      for(String line : lore) {
         lore_text.add(TextUtils.deserialize(line));
      }

      stack.applyComponentsAndValidate(DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, TextUtils.deserialize(name)).set(DataComponents.LORE, new ItemLore(lore_text)).build());
      if (item_data != null) {
         stack.applyComponentsAndValidate((DataComponentPatch)((Pair)DataComponentPatch.CODEC.decode(JsonOps.INSTANCE, item_data).getOrThrow()).getFirst());
      }

      return stack;
   }

   public void loadPlayerData(UUID uuid, String username) {
      PlayerData data = DexRewardsDatabase.loadPlayer(uuid, username);
      if (data != null) {
         this.player_data.put(uuid, data);
      }

   }

   public void unloadPlayerData(UUID uuid) {
      this.player_data.remove(uuid);
   }

   public void updatePlayerData(PlayerData playerData) {
      this.player_data.put(playerData.uuid, playerData);
      DexRewardsDatabase.savePlayerAsync(playerData);
   }

   public void generateImplementation(String id, String generation) {
      try {
         File generated_folder = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/Generated").toFile();
         if (!generated_folder.exists()) {
            generated_folder.mkdirs();
         }

         File generation_folder = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/Generated/" + generation).toFile();
         if (!generation_folder.exists()) {
            generation_folder.mkdirs();
         }

         File implementation_file = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards/Generated/" + generation + "/" + id.replaceAll("cobblemon:", "") + ".json").toFile();
         if (implementation_file.createNewFile()) {
            JsonObject root = new JsonObject();
            root.addProperty("target", id);
            root.addProperty("implemented", true);
            Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
            Writer writer = new FileWriter(implementation_file);
            gson.toJson(root, writer);
            writer.close();
         }
      } catch (IOException var9) {
         var9.printStackTrace();
      }

   }

   public PlayerData getPlayerData(UUID uuid) {
      return (PlayerData)this.player_data.get(uuid);
   }
}
