package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.cobblemon.catcharea.BannedSpeciesConfig
import com.f4xizzz.serverbuddy.cobblemon.catcharea.BannedSpeciesConfigManager
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfig
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfig
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfigManager
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiSoundsConfig
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiSoundsConfigManager
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfig
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfigManager
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboSoundsConfig
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboSoundsConfigManager
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntConfig
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntConfigManager
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderConfigManager
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderGuiConfigModel
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderLangModel
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderServerConfigModel
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderSoundsModel
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorGuiConfigModel
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorLangModel
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorPricesModel
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorServerConfigModel
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorSoundsModel

/**
 * The Java `*ConfigManager`s of the Cobblemon features (Catch Area, Catch Combo, Hunt, PokeBuilder, PokeEditor). Kept in its own object so these
 * classes only load when Cobblemon is installed (see [ConfigAdapters]).
 */
object CobblemonConfigAdapters {
    fun register() {
        ConfigFiles.register("CatchArea", "catcharea.conf", CatchAreaConfig::class.java, reload = { CatchAreaConfigManager.loadConfig() }) { CatchAreaConfig() }
        ConfigFiles.register("CatchArea", "banned_species.conf", BannedSpeciesConfig::class.java, reload = { BannedSpeciesConfigManager.loadConfig() }) { BannedSpeciesConfig() }
        ConfigFiles.register("CatchArea", "gui_icons.conf", CatchAreaGuiIconsConfig::class.java, reload = { CatchAreaGuiIconsConfigManager.loadConfig() }) { CatchAreaGuiIconsConfig() }
        ConfigFiles.register("CatchArea", "gui_sounds.conf", CatchAreaGuiSoundsConfig::class.java, reload = { CatchAreaGuiSoundsConfigManager.loadConfig() }) { CatchAreaGuiSoundsConfig() }
        ConfigFiles.register("CatchCombo", "catchcomboconfig.conf", CatchComboConfig::class.java, reload = { CatchComboConfigManager.loadConfig() }) { CatchComboConfig() }
        ConfigFiles.register("CatchCombo", "combo_sounds.conf", CatchComboSoundsConfig::class.java, reload = { CatchComboSoundsConfigManager.loadConfig() }) { CatchComboSoundsConfig() }
        ConfigFiles.register("Hunt", "hunt.conf", HuntConfig::class.java, reload = { HuntConfigManager.loadConfig() }) { HuntConfig() }

        // PokeBuilder and PokeEditor load all their files together
        val builder = { BuilderConfigManager.loadConfigs() }
        ConfigFiles.register("PokeBuilder", "serverconfig.json", BuilderServerConfigModel::class.java, reload = builder) { BuilderServerConfigModel() }
        ConfigFiles.register("PokeBuilder", "guiconfig.json", BuilderGuiConfigModel::class.java, reload = builder) { BuilderGuiConfigModel() }
        ConfigFiles.register("PokeBuilder", "lang.json", BuilderLangModel::class.java, reload = builder) { BuilderLangModel() }
        ConfigFiles.register("PokeBuilder", "sounds.json", BuilderSoundsModel::class.java, reload = builder) { BuilderSoundsModel() }
        val editor = { EditorConfigManager.loadConfigs() }
        ConfigFiles.register("PokeEditor", "serverconfig.json", EditorServerConfigModel::class.java, reload = editor) { EditorServerConfigModel() }
        ConfigFiles.register("PokeEditor", "guiconfig.json", EditorGuiConfigModel::class.java, reload = editor) { EditorGuiConfigModel() }
        ConfigFiles.register("PokeEditor", "lang.json", EditorLangModel::class.java, reload = editor) { EditorLangModel() }
        ConfigFiles.register("PokeEditor", "prices.json", EditorPricesModel::class.java, reload = editor) { EditorPricesModel() }
        ConfigFiles.register("PokeEditor", "sounds.json", EditorSoundsModel::class.java, reload = editor) { EditorSoundsModel() }
    }
}
