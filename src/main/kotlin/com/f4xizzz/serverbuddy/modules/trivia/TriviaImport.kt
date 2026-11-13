package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Converte as pools do SkiesTrivia (copiadas de `config/skiestrivia` para `Trivia/pools` na primeira vez) para o formato
 * daqui. O arquivo original, em `config/skiestrivia`, não é tocado; a cópia convertida guarda um `.skies.bak`.
 */
object TriviaImport {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Trivia")

    private val GENERATOR_ALIASES = mapOf(
        "dex_num_to_species" to "dex_number_to_species",
        "species_to_dex_num" to "species_to_dex_number",
        "equation_easy" to "equation"
    )

    /** Converte cada pool em [poolsDir] que está no formato do SkiesTrivia. */
    fun convertLegacyPools(poolsDir: File) {
        val files = poolsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) } ?: return
        for (file in files) {
            try {
                val old = JsonParser.parseString(file.readText(Charsets.UTF_8))
                if (!old.isJsonObject) continue
                val converted = convertPool(old.asJsonObject) ?: continue
                val pool = SbGson.gson.fromJson(converted, TriviaPool::class.java)
                file.copyTo(File(file.parentFile, file.name + ".skies.bak"), overwrite = true)
                file.writeText(ConfigDocs.render(pool, SbGson.defaultsWriter), Charsets.UTF_8)
                LOGGER.info("Pool '{}' converted from SkiesTrivia format (original backup: {}.skies.bak).", file.name, file.name)
            } catch (e: Exception) {
                LOGGER.error("Could not convert a pool ${file.name}", e)
            }
        }
    }

    /** O JSON da pool no formato nosso, ou null se [old] já está no formato daqui. */
    @JvmStatic
    fun convertPool(old: JsonObject): JsonObject? {
        val settings = old.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
        val questions = old.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val rewards = old.get("rewards")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val skiesQuestions = questions.any { it.isJsonObject && it.asJsonObject.has("type") }
        val skiesRewards = rewards.any { it.isJsonObject && it.asJsonObject.get("type")?.asString?.startsWith("COMMAND_", ignoreCase = true) == true }
        if (settings == null && !skiesQuestions && !skiesRewards) return null

        val out = JsonObject()
        out.add("enabled", old.get("enabled") ?: JsonPrimitive(true))
        settings?.get("weight")?.let { out.add("weight", it) }
        settings?.get("answer_time")?.takeIf { !it.isJsonNull }?.let { out.add("answer_time", it) }

        val generatedText = settings?.get("messages")?.takeIf { it.isJsonObject }?.asJsonObject?.get("generated")?.takeIf { it.isJsonObject }?.asJsonObject
        val newQuestions = JsonArray()
        for (entry in questions) {
            if (!entry.isJsonObject) continue
            val q = entry.asJsonObject
            if (q.get("type")?.asString.equals("GENERATED", ignoreCase = true)) {
                val full = q.get("id")?.asString ?: continue
                val id = full.substringAfter(':')
                val generated = JsonObject()
                generated.addProperty("generator", GENERATOR_ALIASES[id] ?: id)
                if (id == "equation_easy") generated.addProperty("difficulty", "easy")
                q.get("blacklist")?.takeIf { it.isJsonArray && it.asJsonArray.size() > 0 }?.let { generated.add("exclude", it) }
                generatedText?.get(full)?.takeIf { it.isJsonPrimitive }?.let { generated.add("text", it) }
                newQuestions.add(generated)
            } else {
                val manual = JsonObject()
                q.get("question")?.let { manual.add("question", it) }
                (q.get("answers") ?: q.get("answer"))?.let { manual.add("answers", it) }
                newQuestions.add(manual)
            }
        }
        out.add("questions", newQuestions)

        val newRewards = JsonArray()
        for (entry in rewards) {
            if (!entry.isJsonObject) continue
            val r = entry.asJsonObject
            val type = r.get("type")?.asString?.lowercase() ?: continue
            if (!type.startsWith("command_")) {
                newRewards.add(r) // já no formato daqui
                continue
            }
            val reward = JsonObject()
            reward.addProperty("type", "command")
            if (type == "command_player") reward.addProperty("as", "player")
            r.get("commands")?.let { reward.add("command", it) }
            r.get("weight")?.let { reward.add("weight", it) }
            newRewards.add(reward)
        }
        out.add("rewards", newRewards)

        settings?.get("messages")?.takeIf { it.isJsonObject }?.asJsonObject?.let { messages ->
            val kept = JsonObject()
            for (key in listOf("start", "hint", "win", "lose", "timed_out")) messages.get(key)?.takeIf { it.isJsonArray }?.let { kept.add(key, it) }
            if (kept.size() > 0) out.add("messages", kept)
        }
        settings?.get("sounds")?.takeIf { it.isJsonObject }?.let { out.add("sounds", it) }
        return out
    }
}
