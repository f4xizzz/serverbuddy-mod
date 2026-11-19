package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.announcements.AnnouncementsModule
import com.f4xizzz.serverbuddy.announcements.config.AnnouncementGroup
import com.f4xizzz.serverbuddy.announcements.config.MainConfig as AnnouncementsConfig
import com.f4xizzz.serverbuddy.clear.ClearModule
import com.f4xizzz.serverbuddy.clear.config.MainConfig as ClearConfig
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.CratesConfig
import com.f4xizzz.serverbuddy.crates.config.KeysMenu
import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.key.Key
import com.f4xizzz.serverbuddy.crates.data.opening.inventory.InventoryOpeningAnimation
import com.f4xizzz.serverbuddy.crates.data.opening.world.WorldOpeningAnimation
import com.f4xizzz.serverbuddy.crates.data.particles.ParticleAnimationOptions
import com.f4xizzz.serverbuddy.crates.data.previews.Preview
import com.f4xizzz.serverbuddy.events.EventConfigManager
import com.f4xizzz.serverbuddy.events.EventDefinition
import com.f4xizzz.serverbuddy.guis.GuisModule
import com.f4xizzz.serverbuddy.guis.config.GuiConfig
import com.f4xizzz.serverbuddy.guis.config.MainConfig as GuisConfig
import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.KitMenuConfig
import com.f4xizzz.serverbuddy.kits.config.MainConfig as KitsConfig
import com.f4xizzz.serverbuddy.kits.config.PreviewConfig
import com.f4xizzz.serverbuddy.logs.ServerConfig
import com.f4xizzz.serverbuddy.logs.ServerConfigManager
import com.f4xizzz.serverbuddy.modules.actionitems.ActionItemDef
import com.f4xizzz.serverbuddy.modules.missions.MissionCategory
import com.f4xizzz.serverbuddy.modules.trivia.TriviaPool
import com.f4xizzz.serverbuddy.redeem.RedeemConfig
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager
import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.config.ConfirmMenuConfig
import com.f4xizzz.serverbuddy.shop.config.MainConfig as ShopConfig
import com.f4xizzz.serverbuddy.shop.config.ShopConfig as ShopFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Puts in the dashboard (and in the coverage test of `bot-tests/config-coverage-test.js`) the config files that [ConfigFiles.load] does not know
 * about, because their code has its own loader: the Java `*ConfigManager`s, the ported modules (Clear, Announcements, Kits, Shop, Crates, GUIs) and
 * the folders of content the admin adds files to (Trivia pools, mission categories). Every entry says which class describes the file (the
 * dashboard draws the form from it by reflection, with the `@Doc` comments when there are any), how to build the defaults and, when the code has
 * one, how to apply the file again.
 *
 * A new `*ConfigManager` or a new file in `config/ServerBuddy` has to be listed here (or, in the coverage test, in its explicit list of files that
 * are not configuration): the test fails otherwise. Called once, when the modules have started.
 */
object ConfigAdapters {
    fun registerAll() {
        modules()
        content()
        redeemAndLogs()
        if (Integrations.cobblemonFeatures()) CobblemonConfigAdapters.register()
    }

    /** The `config.json` of the ported modules (each reloads with its module). */
    private fun modules() {
        if (Modules.isRunning("clear")) ConfigFiles.register("Clear", "config.json", ClearConfig::class.java, ClearModule.INSTANCE.gson) { ClearConfig() }
        if (Modules.isRunning("announcements")) ConfigFiles.register("Announcements", "config.json", AnnouncementsConfig::class.java) { AnnouncementsConfig() }
        if (Modules.isRunning("guis")) ConfigFiles.register("GUIs", "config.json", GuisConfig::class.java) { GuisConfig() }
        if (Modules.isRunning("kits")) ConfigFiles.register("Kits", "config.json", KitsConfig::class.java, KitsModule.INSTANCE.gson) { KitsConfig() }
        if (Modules.isRunning("shop")) ConfigFiles.register("Shop", "config.json", ShopConfig::class.java, ShopModule.INSTANCE.gson) { ShopConfig() }
        if (Modules.isRunning("crates")) ConfigFiles.register("Crates", "config.json", CratesConfig::class.java, CratesModule.INSTANCE.gson) { CratesConfig() }
    }

    /** Folders of content the admin adds files to: each file is listed on its own, and new ones show up without a restart. */
    private fun content() {
        if (Modules.isRunning("trivia")) ConfigFiles.collection("Trivia/pools", TriviaPool::class.java) { TriviaPool() }
        if (Modules.isRunning("missions")) ConfigFiles.collection("Missions/categories", MissionCategory::class.java) { MissionCategory() }
        if (Modules.isRunning("actionitems")) ConfigFiles.collection("ActionItems/items", ActionItemDef::class.java) { ActionItemDef() }

        // the ported modules have their own Gson (Minecraft types, actions, requirements); the defaults come from the class with nothing in the file
        if (Modules.isRunning("announcements")) items("Announcements/groups", AnnouncementGroup::class.java, AnnouncementsModule.INSTANCE.configManager.gson)
        if (Modules.isRunning("guis")) items("GUIs/guis", GuiConfig::class.java, GuisModule.INSTANCE.configManager.gson, recursive = true)
        if (Modules.isRunning("kits")) {
            val gson = KitsModule.INSTANCE.gson
            items("Kits/kits", Kit::class.java, gson)
            items("Kits/previews", PreviewConfig::class.java, gson)
            items("Kits/menus", KitMenuConfig::class.java, gson)
        }
        if (Modules.isRunning("shop")) {
            val gson = ShopModule.INSTANCE.gson
            // the shop module reads every .json under shops/ (the id is the file name, in any folder), and the big shops keep a folder of categories each
            items("Shop/shops", ShopFile::class.java, gson, recursive = true, template = { NEW_SHOP })
            items("Shop/confirm_menus", ConfirmMenuConfig::class.java, gson)
        }
        if (Modules.isRunning("crates")) {
            val gson = CratesModule.INSTANCE.gson
            items("Crates/crates", Crate::class.java, gson)
            items("Crates/keys", Key::class.java, gson)
            items("Crates/openings/inventory", InventoryOpeningAnimation::class.java, gson)
            items("Crates/openings/world", WorldOpeningAnimation::class.java, gson)
            items("Crates/particles", ParticleAnimationOptions::class.java, gson)
            items("Crates/previews", Preview::class.java, gson)
            ConfigFiles.register("Crates/menus", "keys.json", KeysMenu::class.java, gson) { KeysMenu() }
        }
    }

    /**
     * A folder of files of one class whose defaults come from the class itself (Gson fills what the file leaves out). [recursive]: the folders inside it
     * hold files of the same kind (the menus keep their kits menus in `guis/kits`, and the module reads them all).
     */
    private fun items(folder: String, type: Class<*>, gson: Gson, recursive: Boolean = false, template: (() -> String)? = null) {
        ConfigFiles.collection(folder, type, gson = gson, recursive = recursive, template = template) { gson.fromJson("{}", type) ?: throw IllegalStateException("no defaults for ${type.simpleName}") }
    }

    /**
     * A new shop made by the dashboard. A shop cannot be built from nothing (`title` and `type` are required when the file is read), so it starts from
     * this text: an empty chest of 6 rows with no products and no decoration.
     */
    private val NEW_SHOP = """
        {
          "title": "<bold>New shop",
          "type": "GENERIC_9x6",
          "entries": {},
          "items": {}
        }
    """.trimIndent() + "\n"

    /** `Events/shiny.json` is `{ "eventos": [...] }`: this class only describes that for the dashboard (the events are read by [EventConfigManager]). */
    class EventsFile(@SerializedName("eventos") val events: List<EventDefinition> = ArrayList())

    /** The two Java managers that do not depend on Cobblemon. (`Database/database.conf` is left out on purpose: it holds the database login.) */
    private fun redeemAndLogs() {
        ConfigFiles.register("Events", "shiny.json", EventsFile::class.java, reload = { EventConfigManager.loadConfig() }) { EventsFile() }
        ConfigFiles.register("Redeem", "redeem.conf", RedeemConfig::class.java, reload = { RedeemConfigManager.loadConfig() }) { RedeemConfig() }
        ConfigFiles.register("", "serverconfig.conf", ServerConfig::class.java, reload = { ServerConfigManager.loadConfig() }) { ServerConfig() }
    }
}
