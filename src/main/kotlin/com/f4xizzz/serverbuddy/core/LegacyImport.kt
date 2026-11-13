package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.ModPaths
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Importa as configurações dos mods que o ServerBuddy substituiu, copiando as pastas antigas de `config/`
 * para `config/ServerBuddy/` na primeira execução. A pasta antiga nunca é alterada nem apagada, e nada é
 * sobrescrito: se a pasta nova já existe, a importação é ignorada.
 */
object LegacyImport {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    private fun configDir(): File = FabricLoader.getInstance().configDir.toFile()

    /** `config/GreatServerUtils` → `config/ServerBuddy` (o resto do mod antigo). */
    @JvmStatic
    fun importGreatServerUtils() {
        val target = ModPaths.subDir()
        val legacy = File(configDir(), "GreatServerUtils")
        if (target.exists() || !legacy.isDirectory) return
        try {
            legacy.copyRecursively(target, overwrite = false)
            File(target, "GreatLogs").takeIf { it.isDirectory }?.renameTo(File(target, "Logs"))
            LOGGER.info("GreatServerUtils settings imported: config/GreatServerUtils -> config/ServerBuddy (the old folder was kept)")
        } catch (e: Exception) {
            LOGGER.error("Failed to import config/GreatServerUtils", e)
        }
    }

    /** Pastas antigas de um módulo (ex.: `config/skieskits` → `config/ServerBuddy/Kits`). */
    @JvmStatic
    fun importModule(module: SbModule) {
        val target = ModPaths.subDir(module.folder)
        if (target.exists()) return
        for (name in module.legacyFolders) {
            val legacy = File(configDir(), name)
            if (!legacy.isDirectory) continue
            try {
                legacy.copyRecursively(target, overwrite = false)
                LOGGER.info("Settings imported: config/{} -> config/ServerBuddy/{} (the old folder was kept)", name, module.folder)
            } catch (e: Exception) {
                LOGGER.error("Failed to import config/$name", e)
            }
            return
        }
    }
}
