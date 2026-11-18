package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the dashboard's config editor needs besides the schema: checking a value tree the way the mod will read it ([validate]), the history of
 * `.bak` copies ([backup], [history], [restore]), what a save changes ([changes]) and a search across every file ([search]).
 */
object ConfigTools {
    /** One thing wrong (or suspicious) at [path] of a file ("" = the file as a whole). Only errors stop a save. */
    class Problem(val path: String, val code: String, val message: String, val warning: Boolean = false) {
        fun toJson(): JsonObject = JsonObject().apply {
            addProperty("path", path)
            addProperty("code", code)
            addProperty("message", message)
            addProperty("warning", warning)
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Checks [values] against the schema of [entry], then loads them into the config class like the mod does. What the site posts has markers in the
     * place of the secrets ([ConfigSecrets]): they are taken from the file first, so the check sees what the file would have.
     */
    fun validate(entry: ConfigFiles.Entry, values: JsonElement): List<Problem> =
        validateResolved(entry, ConfigSecrets.resolve(entry.type, values, ConfigSchema.rawFile(entry)))

    /** The same, for values that already have their secrets (what a save is about to write). No message quotes a secret. */
    internal fun validateResolved(entry: ConfigFiles.Entry, values: JsonElement): List<Problem> {
        val problems = ArrayList<Problem>()
        if (!values.isJsonObject) return listOf(Problem("", "type", "The file must be a JSON object."))
        val root = JsonObject().apply {
            addProperty("type", "object")
            add("fields", ConfigSchema.schema(entry).get("fields") ?: JsonArray())
        }
        walk(root, values, "", problems)
        if (problems.none { !it.warning }) {
            try {
                if (entry.gson.fromJson(values, entry.type) == null) problems += Problem("", "empty", "The config is empty.")
            } catch (e: Exception) {
                // the message of a parser can quote what it was reading
                problems += Problem("", "load", "The mod cannot read this: ${ConfigSecrets.redact(e.message.orEmpty(), ConfigSecrets.secretValues(entry.type, values)).take(240)}")
            }
        }
        return problems
    }

    /**
     * The shape check per option. It only refuses what the mod really cannot read: many options take more than their Java type says (a slot list
     * written as "0-44", a lore that is one text or a list), so a value of an unexpected SHAPE is left to the load check that follows. An
     * unknown name is a warning (a typo is the most common mistake, but a few classes read more names than they list).
     */
    private fun walk(node: JsonObject, value: JsonElement?, path: String, out: MutableList<Problem>) {
        if (value == null || value.isJsonNull) return // an empty value means "use the default"
        when (node.get("type")?.asString) {
            "boolean" -> if (!(value.isJsonPrimitive && (value.asJsonPrimitive.isBoolean || value.asString.lowercase() in setOf("true", "false")))) out += Problem(path, "boolean", "Must be true or false.")
            "int" -> if (!isWholeNumber(value)) out += Problem(path, "int", "Must be a whole number.")
            "float" -> if (!isNumber(value)) out += Problem(path, "number", "Must be a number.")
            "enum" -> {
                val options = (node.getAsJsonArray("options") ?: JsonArray()).map { it.asString }
                val accepted = options + (node.getAsJsonArray("aliases") ?: JsonArray()).map { it.asString }
                if (value.isJsonPrimitive && accepted.none { it.equals(value.asString, ignoreCase = true) }) out += Problem(path, "option", "Must be one of: ${options.joinToString(", ")}.")
            }
            "list" -> {
                val item = node.getAsJsonObject("item")
                if (item != null && value.isJsonArray) value.asJsonArray.forEachIndexed { index, element -> walk(item, element, "$path[$index]", out) }
            }
            "map" -> {
                val item = node.getAsJsonObject("value")
                if (item != null && value.isJsonObject) for ((key, element) in value.asJsonObject.entrySet()) walk(item, element, join(path, key), out)
            }
            "object" -> if (value.isJsonObject) {
                val fields = node.getAsJsonArray("fields")?.map { it.asJsonObject } ?: emptyList()
                val known = fields.associateBy { it.get("key").asString }
                for ((key, element) in value.asJsonObject.entrySet()) {
                    val child = known[key]
                    if (child == null) out += Problem(join(path, key), "unknown", "The mod does not know this option (a typo?). It will be ignored.", warning = true)
                    else walk(child, element, join(path, key), out)
                }
            }
        }
    }

    private fun isNumber(value: JsonElement): Boolean =
        value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || (value.asJsonPrimitive.isString && value.asString.trim().toDoubleOrNull() != null))

    /** A whole number, as a number or as text; a text like "0-44" counts too (the slot lists of the menus take ranges). */
    private fun isWholeNumber(value: JsonElement): Boolean {
        if (!value.isJsonPrimitive) return false
        val text = value.asString.trim()
        if (value.asJsonPrimitive.isString && Regex("""-?\d+\s*-\s*\d+""").matches(text)) return true
        return isNumber(value) && text.toDoubleOrNull()?.let { it % 1.0 == 0.0 } == true
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    // ------------------------------------------------------------------ backups

    /** One saved copy of a file: [id] is its file name (never a path the browser makes up). */
    class Backup(val id: String, val modified: Long, val size: Long)

    private const val KEEP = 20
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault())

    private fun backupPattern(entry: ConfigFiles.Entry) = Regex("^" + Regex.escape(entry.fileName) + "\\.(\\d{8}-\\d{6}-\\d{3})\\.bak$")

    /** Copies the file as it is now to a time-stamped `.bak` (the last [KEEP] are kept) and to the single `.bak` that older versions wrote. */
    fun backup(entry: ConfigFiles.Entry) {
        val file = entry.file
        if (!file.isFile) return
        val folder = file.parentFile
        file.copyTo(File(folder, "${entry.fileName}.${STAMP.format(Instant.now())}.bak"), overwrite = true)
        file.copyTo(File(folder, entry.fileName + ".bak"), overwrite = true)
        val pattern = backupPattern(entry)
        folder.listFiles { f -> pattern.matches(f.name) }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /** The saved copies of [entry], newest first (the single `.bak` only shows when there is no time-stamped copy). */
    fun history(entry: ConfigFiles.Entry): List<Backup> {
        val folder = entry.file.parentFile ?: return emptyList()
        val pattern = backupPattern(entry)
        val stamped = folder.listFiles { f -> f.isFile && pattern.matches(f.name) }?.sortedByDescending { it.name }.orEmpty()
        val files = stamped.ifEmpty { listOfNotNull(File(folder, entry.fileName + ".bak").takeIf { it.isFile }) }
        return files.map { Backup(it.name, it.lastModified(), it.length()) }
    }

    /**
     * Puts the saved copy [id] back (the file as it is now is backed up first). Returns the values the file has afterwards, with a marker in the place
     * of each secret ([ConfigSchema.values]). The file becomes exactly the copy, secret and all: that is what undoing means.
     */
    fun restore(entry: ConfigFiles.Entry, id: String): JsonElement {
        val source = history(entry).firstOrNull { it.id == id } ?: throw DashboardError("not_found", "backup $id")
        val copy = File(entry.file.parentFile, source.id)
        // Capture the bytes first: backup() replaces the legacy .bak and may prune the selected oldest copy.
        val contents = try { copy.readBytes() } catch (e: Exception) {
            throw DashboardError("bad_config", "That backup cannot be read.")
        }
        val tree = try {
            JsonParser.parseString(contents.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw DashboardError("bad_config", "That backup cannot be read. Repair it on the server before restoring.")
        }
        if (!tree.isJsonObject) throw DashboardError("bad_config", "That backup is not a config file")
        ConfigSchema.rawFile(entry)?.let { ConfigSecrets.remember(ConfigSecrets.secretValues(entry.type, it)) }
        ConfigSecrets.remember(ConfigSecrets.secretValues(entry.type, tree))
        backup(entry)
        entry.file.writeBytes(contents)
        return ConfigSchema.values(entry)
    }

    // ------------------------------------------------------------------ what a save changes

    /**
     * The options that differ between [old] and [new] (both with the real secrets; [type] is the config class), as `{ path, from?, to? }` (at most
     * [cap]; a list that differs counts as one change). A secret never shows its value, only that it changed:
     * `{ path, secret: true, change: "set" | "changed" | "cleared" }`.
     */
    fun changes(old: JsonElement?, new: JsonElement, type: Type? = null, cap: Int = 40): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        diff(old, new, "", out, cap, type)
        return out
    }

    private fun diff(old: JsonElement?, new: JsonElement?, path: String, out: MutableList<JsonObject>, cap: Int, type: Type?) {
        if (out.size >= cap || old == new) return
        if (old != null && new != null && old.isJsonObject && new.isJsonObject) {
            val keys = LinkedHashSet<String>().apply { addAll(old.asJsonObject.keySet()); addAll(new.asJsonObject.keySet()) }
            for (key in keys) {
                val before = old.asJsonObject.get(key)
                val after = new.asJsonObject.get(key)
                if (ConfigSecrets.isSecretKey(type, key)) secretChange(before, after, join(path, key))?.let { if (out.size < cap) out += it }
                else diff(before, after, join(path, key), out, cap, ConfigSecrets.childType(type, key))
            }
            return
        }
        // a list or a plain value is one change; a secret inside a list shows as "(secret)"
        val shownOld = old?.let { ConfigSecrets.shown(type, it) }
        val shownNew = new?.let { ConfigSecrets.shown(type, it) }
        if (shownOld == shownNew) { // only a secret inside is different
            out += JsonObject().apply {
                addProperty("path", path)
                addProperty("secret", true)
                addProperty("change", "changed")
            }
            return
        }
        out += JsonObject().apply {
            addProperty("path", path)
            shownOld?.let { addProperty("from", brief(it)) }
            shownNew?.let { addProperty("to", brief(it)) }
        }
    }

    /** What happened to a secret, without saying what it is (null when nothing did: both empty). */
    private fun secretChange(before: JsonElement?, after: JsonElement?, path: String): JsonObject? {
        if (before == after) return null
        val was = ConfigSecrets.stateOf(before)
        val now = ConfigSecrets.stateOf(after)
        val change = when {
            was == "empty" && now == "empty" -> return null
            was == "empty" -> "set"
            now == "empty" -> "cleared"
            else -> "changed"
        }
        return JsonObject().apply {
            addProperty("path", path)
            addProperty("secret", true)
            addProperty("change", change)
        }
    }

    private fun brief(value: JsonElement): String = value.toString().let { if (it.length > 80) it.take(77) + "..." else it }

    // ------------------------------------------------------------------ search

    /** Looks for [query] in the names, comments and text values of every config file: `{ module, file, path, label, doc?, value? }`. */
    fun search(query: String, limit: Int = 60): JsonArray {
        val text = query.trim().lowercase()
        val hits = JsonArray()
        if (text.length < 2) return hits
        for (entry in ConfigFiles.entries()) {
            if (hits.size() >= limit) break
            val schema = try { ConfigSchema.schema(entry) } catch (e: Throwable) { continue }
            val values = try { ConfigSchema.values(entry) } catch (e: Throwable) { continue }
            val fields = schema.getAsJsonArray("fields") ?: continue
            collect(entry, fields, values, "", text, hits, limit)
        }
        return hits
    }

    private fun collect(entry: ConfigFiles.Entry, fields: JsonArray, values: JsonElement?, path: String, text: String, hits: JsonArray, limit: Int) {
        for (element in fields) {
            if (hits.size() >= limit) return
            val node = element.asJsonObject
            val key = node.get("key").asString
            val here = join(path, key)
            val value = values?.takeIf { it.isJsonObject }?.asJsonObject?.get(key)
            val doc = node.get("doc")?.asString
            val secret = node.get("secret")?.asBoolean == true // its name and comment can be found, its value never
            val shown = if (secret) null else value?.takeIf { it.isJsonPrimitive }?.asString
            val matches = key.lowercase().contains(text) || node.get("label")?.asString?.lowercase()?.contains(text) == true ||
                doc?.lowercase()?.contains(text) == true || shown?.lowercase()?.contains(text) == true
            if (matches) {
                hits.add(JsonObject().apply {
                    addProperty("module", entry.folder)
                    addProperty("file", entry.fileName)
                    addProperty("path", here)
                    addProperty("label", entry.label)
                    doc?.let { addProperty("doc", it.take(160)) }
                    shown?.let { addProperty("value", it.take(80)) }
                })
            }
            val children = node.getAsJsonArray("fields")
            if (node.get("type")?.asString == "object" && children != null) collect(entry, children, value, here, text, hits, limit)
        }
    }
}
