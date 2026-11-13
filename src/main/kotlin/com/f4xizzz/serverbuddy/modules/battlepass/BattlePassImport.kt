package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager
import java.util.UUID

/**
 * Traz o Great BattlePass para cá: converte o `config.json` e o `tiers.json` copiados de `config/GreatBattlePass` (a pasta original
 * fica como estava; a cópia convertida guarda um `.gbp.bak`) e, com `/battlepass import`, o progresso dos jogadores.
 */
object BattlePassImport {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/BattlePass")

    /** Converte os arquivos de [dir] que ainda estão no formato do Great BattlePass. */
    fun convertLegacyFiles(dir: File) {
        convert(File(dir, "config.json")) { old -> convertConfig(old)?.let { SbGson.gson.fromJson(it, BattlePassConfig::class.java) } }
        convert(File(dir, "tiers.json")) { old -> convertTiers(old)?.let { SbGson.gson.fromJson(it, BpTiersFile::class.java) } }
    }

    private fun convert(file: File, build: (JsonObject) -> Any?) {
        if (!file.isFile) return
        try {
            val old = JsonParser.parseString(file.readText(Charsets.UTF_8))
            if (!old.isJsonObject) return
            val converted = build(old.asJsonObject) ?: return
            file.copyTo(File(file.parentFile, file.name + ".gbp.bak"), overwrite = true)
            file.writeText(ConfigDocs.render(converted, SbGson.defaultsWriter), Charsets.UTF_8)
            LOGGER.info("{} converted from the Great BattlePass format (copy of the original: {}.gbp.bak).", file.name, file.name)
        } catch (e: Exception) {
            LOGGER.error("Could not convert ${file.name}", e)
        }
    }

    /** O `config.json` no formato daqui, ou null se [old] já está no formato daqui. */
    @JvmStatic
    fun convertConfig(old: JsonObject): JsonObject? {
        if (!old.has("maxLevel") && !old.has("catchXP") && !old.has("xpProgression")) return null
        val out = JsonObject()
        old.get("maxLevel")?.let { out.add("levels", it) }
        val xp = JsonObject()
        for ((from, to) in listOf("catchXP" to "catch", "defeatXP" to "defeat", "evolveXP" to "evolve", "hatchXP" to "hatch", "tradeXP" to "trade")) old.get(from)?.let { xp.add(to, it) }
        old.get("xpProgression")?.takeIf { it.isJsonObject }?.asJsonObject?.let { progression ->
            val curve = JsonObject()
            progression.get("mode")?.let { curve.addProperty("mode", it.asString.uppercase()) }
            progression.get("xpPerLevel")?.let { curve.add("xp_per_level", it) }
            progression.get("xpMultiplier")?.let { curve.add("multiplier", it) }
            progression.get("manualXpValues")?.takeIf { it.isJsonObject }?.let { curve.add("manual", it) }
            xp.add("curve", curve)
        }
        out.add("xp", xp)
        val season = JsonObject()
        old.get("seasonDurationDays")?.let { season.add("days", it) }
        out.add("season", season)
        val premium = JsonObject()
        old.get("premiumCost")?.let { premium.add("price", it) }
        out.add("premium", premium)
        return out
    }

    /** O `tiers.json` no formato daqui, ou null se [old] já está no formato daqui. */
    @JvmStatic
    fun convertTiers(old: JsonObject): JsonObject? {
        val tiers = old.get("tiers")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val legacy = old.has("templates") || tiers.any { it.isJsonObject && (it.asJsonObject.has("freeReward") || it.asJsonObject.has("premiumReward")) }
        if (!legacy) return null
        val templates = old.get("templates")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val out = JsonArray()
        for (element in tiers) {
            val tier = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val level = tier.get("level")?.asInt ?: continue
            val converted = JsonObject()
            converted.addProperty("level", level)
            converted.add("free", rewardList(tier.get("freeReward"), templates))
            converted.add("premium", rewardList(tier.get("premiumReward"), templates))
            out.add(converted)
        }
        val result = JsonObject()
        result.add("tiers", out)
        return result
    }

    private fun rewardList(element: com.google.gson.JsonElement?, templates: JsonObject): JsonArray {
        val list = JsonArray()
        if (element == null || !element.isJsonObject) return list
        var source = element.asJsonObject
        source.get("\$template")?.asString?.let { name -> source = templates.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return list }
        convertReward(source)?.let { list.add(it) }
        return list
    }

    /** Um prêmio do Great BattlePass (`ITEM`, `POKEMON`, `COMMAND`) no formato de recompensa daqui. */
    @JvmStatic
    fun convertReward(old: JsonObject): JsonObject? {
        val type = old.get("type")?.asString?.uppercase() ?: "ITEM"
        val data = old.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val out = JsonObject()
        when (type) {
            "POKEMON" -> {
                val species = data.get("species")?.asString ?: return null
                val spec = StringBuilder(species)
                if (data.get("shiny")?.asBoolean == true) spec.append(" shiny=yes")
                data.get("level")?.let { spec.append(" level=").append(it.asInt) }
                data.get("ability")?.let { spec.append(" ability=").append(it.asString) }
                data.get("aspect")?.let { aspect -> spec.append(" ").append(if (aspect.asString.startsWith("aspect=")) aspect.asString else "aspect=" + aspect.asString) }
                data.get("properties")?.let { spec.append(" ").append(it.asString) }
                out.addProperty("type", "pokemon")
                out.addProperty("spec", spec.toString())
            }
            "COMMAND" -> {
                val command = old.get("command")?.asString ?: data.get("command")?.asString ?: return null
                out.addProperty("type", "command")
                out.addProperty("command", command)
                data.get("display_name")?.let { out.addProperty("label", it.asString) }
            }
            else -> {
                out.addProperty("type", "item")
                out.addProperty("item", data.get("id")?.asString ?: return null)
                out.addProperty("amount", (data.get("Count") ?: data.get("count"))?.asInt ?: 1)
                data.get("components")?.let { out.add("components", it) }
            }
        }
        return out
    }

    /** Lê os jogadores do banco SQLite do Great BattlePass e grava na temporada [season]. Devolve quantos entraram (ou null se não há banco). */
    fun importPlayers(season: Int): Int? {
        val db = File(FabricLoader.getInstance().configDir.toFile(), "GreatBattlePass/greatbattlepass.db")
        if (!db.isFile) return null
        var count = 0
        try {
            DriverManager.getConnection("jdbc:sqlite:" + db.absolutePath).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT uuid, level, xp, is_premium, free_rewards, premium_rewards FROM battlepass_players").use { rs ->
                        while (rs.next()) {
                            val uuid = runCatching { UUID.fromString(rs.getString("uuid")) }.getOrNull() ?: continue
                            val pass = PlayerPass(
                                uuid, season, rs.getInt("level").coerceAtLeast(1), rs.getLong("xp"), rs.getInt("is_premium") == 1,
                                BattlePassRepository.decode(rs.getString("free_rewards")), BattlePassRepository.decode(rs.getString("premium_rewards"))
                            )
                            if (BattlePassRepository.save(pass)) count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Could not import the Great BattlePass database", e)
            return null
        }
        return count
    }
}
