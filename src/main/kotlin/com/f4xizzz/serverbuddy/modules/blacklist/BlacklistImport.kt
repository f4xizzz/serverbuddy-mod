package com.f4xizzz.serverbuddy.modules.blacklist

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Importa a lista do mod "Cobblemon Blacklist" (`config/blacklist.json` ou `config/cobblemon_blacklist.json`) na
 * primeira vez, quando o `config/ServerBuddy/Blacklist/config.json` ainda não existe. Os contextos do mod original
 * (`default`, `mode_tower`, `mode_raid`...) já eram ligados por tag, que é o jeito padrão daqui, então nada muda.
 */
object BlacklistImport {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Blacklist")

    /**
     * Lê o texto do arquivo do mod original: `{ "contexts": { "default": ["a", "b"] } }` ou só uma lista (que vira o
     * "default"). Devolve null se o texto não é nenhum dos dois.
     */
    @JvmStatic
    fun parse(text: String): Map<String, List<String>>? {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            return null
        }
        if (root.isJsonArray) return mapOf("default" to strings(root.asJsonArray))
        if (!root.isJsonObject) return null
        val contexts = root.asJsonObject.get("contexts")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val result = LinkedHashMap<String, List<String>>()
        for ((name, value) in contexts.entrySet()) {
            if (value.isJsonArray) result[name.trim().lowercase()] = strings(value.asJsonArray)
        }
        return result
    }

    private fun strings(array: JsonArray): List<String> =
        array.mapNotNull { if (it.isJsonPrimitive) BlacklistService.normalize(it.asString) else null }.filter { it.isNotEmpty() }.distinct()

    /** Cria o config.json do ServerBuddy a partir do arquivo do mod original, se der. */
    fun importIfNeeded() {
        val target = File(ConfigFiles.dir("Blacklist"), "config.json")
        if (target.exists()) return
        val configDir = FabricLoader.getInstance().configDir.toFile()
        val original = listOf("blacklist.json", "cobblemon_blacklist.json").map { File(configDir, it) }.firstOrNull { it.isFile } ?: return
        try {
            val parsed = parse(original.readText(Charsets.UTF_8)) ?: return
            val contexts = LinkedHashMap<String, BlacklistContext>()
            for ((name, moves) in parsed) contexts[name] = BlacklistContext(moves = moves)
            if (contexts.keys.none { it == "default" }) contexts["default"] = BlacklistContext()
            target.parentFile.mkdirs()
            target.writeText(ConfigDocs.render(BlacklistConfig(contexts), SbGson.defaultsWriter), Charsets.UTF_8)
            LOGGER.info("Blacklist imported from config/{} ({} context(s)); the original file was kept.", original.name, contexts.size)
        } catch (e: Exception) {
            LOGGER.error("Could not import config/${original.name}", e)
        }
    }
}
