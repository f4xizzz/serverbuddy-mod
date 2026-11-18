package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Describes a config file for the dashboard's visual editor, built from the same classes and `@Doc` comments that already write
 * the commented JSON files. The site draws a form from this (switches, number boxes with limits, drop-downs, lists, tables) and
 * sends back the whole value tree; [save] checks it by loading it into the config's own class and writes the file the same way
 * the mod does (comments included), keeping a `.bak`. The secrets of a file (database passwords, webhook addresses: [ConfigSecrets]) never go to the
 * site: the schema says which options they are (`secret: true`, no `default`), [values] has a marker in their place and [save] takes the marker back as
 * "keep what the file has".
 *
 * Schema node: `{ key, type, label, doc, default, min?, max?, options?, fields?, item?, value?, multiline? }` where `type` is
 * boolean | int | float | string | enum | list | map | object | json (anything the form cannot draw: edited as raw JSON).
 */
object ConfigSchema {
    /** `{ doc, fields: [...] }` for [entry]. */
    fun schema(entry: ConfigFiles.Entry): JsonObject {
        val defaults = ConfigSecrets.strip(entry.type, defaultTree(entry)) // a class that cannot be built empty takes its defaults from the file: no secret may get in
        return JsonObject().apply {
            entry.type.getAnnotation(Doc::class.java)?.let { addProperty("doc", it.value) }
            add("fields", if (defaults.isJsonObject) fields(entry.type, defaults.asJsonObject) else JsonArray())
        }
    }

    /** The current content of the file as the SITE may see it: a marker in place of every secret ([ConfigSecrets]). */
    fun values(entry: ConfigFiles.Entry): JsonElement = ConfigSecrets.mask(entry.type, rawValues(entry))

    /** The file as it is on disk, secrets included (never send this anywhere); null when there is no file or it cannot be read. */
    internal fun rawFile(entry: ConfigFiles.Entry): JsonElement? = try {
        if (entry.file.isFile) entry.file.reader(Charsets.UTF_8).use { com.google.gson.JsonParser.parseReader(it) } else null
    } catch (e: Exception) {
        null
    }

    /** The current content of the file as a JSON tree, secrets included (falls back to the defaults while the file does not exist). Never send this anywhere. */
    internal fun rawValues(entry: ConfigFiles.Entry): JsonElement {
        val file = entry.file
        if (file.isFile) {
            try {
                file.reader(Charsets.UTF_8).use {
                    val tree = com.google.gson.JsonParser.parseReader(it)
                    if (!tree.isJsonObject) throw DashboardError("bad_config", "The file must be a JSON object.")
                    return tree
                }
            } catch (e: Exception) {
                throw DashboardError("bad_config", "The file cannot be read. Repair it on the server before saving.")
            }
        }
        return defaultTree(entry)
    }

    /**
     * The defaults of [entry] as a JSON tree. Some classes cannot be built empty (a map that needs its adapter, a field that has to be set): then the
     * file as it is says which options exist, and a file that is not there shows no options (the raw JSON can still be edited).
     */
    private fun defaultTree(entry: ConfigFiles.Entry): JsonElement = try {
        entry.defaultsWriter.toJsonTree(entry.defaults())
    } catch (e: Exception) {
        try {
            if (entry.file.isFile) entry.file.reader(Charsets.UTF_8).use { com.google.gson.JsonParser.parseReader(it) } else JsonObject()
        } catch (unreadable: Exception) {
            JsonObject()
        }
    }

    /** What a save did: what stopped it (errors; warnings do not) and what it changed. */
    class SaveResult(val problems: List<ConfigTools.Problem>, val changes: List<JsonObject>, val warnings: List<String>) {
        val written: Boolean get() = problems.none { !it.warning }
    }

    /**
     * Checks [values] ([ConfigTools.validate]) and, when nothing is wrong, keeps a copy of the file ([ConfigTools.backup]) and writes it the way the
     * mod does (comments included). The warnings are for the person.
     */
    fun save(entry: ConfigFiles.Entry, posted: JsonElement): SaveResult {
        // An unreadable file must never become an empty source for preserved secrets.
        val before = rawValues(entry)
        // what the site posts has markers in the place of the secrets: the file gets the secret it has now, the new one the person typed, or an empty one
        val values = ConfigSecrets.resolve(entry.type, posted, before)
        val problems = ConfigTools.validateResolved(entry, values)
        if (problems.any { !it.warning }) return SaveResult(problems, emptyList(), emptyList())
        ConfigSecrets.remember(ConfigSecrets.secretValues(entry.type, before))
        ConfigSecrets.remember(ConfigSecrets.secretValues(entry.type, values))
        if (entry.file.isFile && values == before) return SaveResult(problems, emptyList(), emptyList())
        ConfigTools.backup(entry)
        val file = entry.file
        file.parentFile.mkdirs()
        file.writeText(com.f4xizzz.serverbuddy.core.config.ConfigDocs.renderTree(values, entry.type, entry.defaultsWriter), Charsets.UTF_8)
        ConfigSecrets.remember(ConfigSecrets.secretValues(entry.type, values))
        return SaveResult(problems, ConfigTools.changes(before, values, entry.type), listOf("Saved. Use \"Reload\" (or /sb reload) for the change to take effect; some options only apply after a restart."))
    }

    // ------------------------------------------------------------------ the schema walk

    private fun fields(owner: Class<*>, tree: JsonObject): JsonArray {
        val array = JsonArray()
        for ((key, value) in tree.entrySet()) {
            val field = field(owner, key)
            array.add(node(key, field?.genericType, field?.getAnnotation(Doc::class.java)?.value, value, ConfigSecrets.isSecretKey(owner, key)))
        }
        return array
    }

    private fun node(key: String, type: Type?, doc: String?, value: JsonElement?, secret: Boolean = false): JsonObject {
        val raw = rawClass(type)
        val node = JsonObject()
        node.addProperty("key", key)
        node.addProperty("label", key.replace('_', ' ').replaceFirstChar { it.uppercase() })
        if (doc != null) node.addProperty("doc", doc)
        if (secret) node.addProperty("secret", true) // the form draws it as "set, change, clear": it never has the value
        if (value != null && !value.isJsonNull) node.add("default", value)

        when {
            raw == java.lang.Boolean.TYPE || raw == java.lang.Boolean::class.java -> node.addProperty("type", "boolean")
            raw != null && (raw == Integer.TYPE || raw == java.lang.Long.TYPE || raw == java.lang.Short.TYPE || raw == java.lang.Byte.TYPE ||
                raw == Integer::class.java || raw == java.lang.Long::class.java) -> node.addProperty("type", "int")
            raw != null && (raw == java.lang.Double.TYPE || raw == java.lang.Float.TYPE || raw == java.lang.Double::class.java || raw == java.lang.Float::class.java) ->
                node.addProperty("type", "float")
            raw == String::class.java -> {
                node.addProperty("type", "string")
                val text = if (value != null && value.isJsonPrimitive) value.asString else ""
                if (text.contains('\n') || text.length > 80) node.addProperty("multiline", true)
            }
            raw != null && (raw.isEnum || raw.superclass?.isEnum == true) -> {
                node.addProperty("type", "enum")
                node.add("options", enumOptions(if (raw.isEnum) raw else raw.superclass))
                node.add("aliases", enumAliases(if (raw.isEnum) raw else raw.superclass))
            }
            raw != null && Map::class.java.isAssignableFrom(raw) -> {
                node.addProperty("type", "map")
                val sample = if (value != null && value.isJsonObject) value.asJsonObject.entrySet().firstOrNull()?.value else null
                node.add("value", node("value", typeArgument(type, 1), null, sample))
            }
            raw != null && (Collection::class.java.isAssignableFrom(raw) || raw.isArray) -> {
                node.addProperty("type", "list")
                val sample = if (value != null && value.isJsonArray) value.asJsonArray.firstOrNull() else null
                node.add("item", node("item", typeArgument(type, 0), null, sample))
            }
            raw != null && value != null && value.isJsonObject && isDataClass(raw) -> {
                node.addProperty("type", "object")
                node.add("fields", fields(raw, value.asJsonObject))
            }
            else -> inferFromValue(node, value)
        }
        return node
    }

    /** When the Java type says nothing (a field of an open type), the type of the default value decides. */
    private fun inferFromValue(node: JsonObject, value: JsonElement?) {
        val type = when {
            value == null || value.isJsonNull -> "json"
            value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> "boolean"
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> if (value.asString.contains('.')) "float" else "int"
            value.isJsonPrimitive -> "string"
            else -> "json"
        }
        node.addProperty("type", type)
    }

    private fun enumOptions(enumClass: Class<*>): JsonArray {
        val array = JsonArray()
        for (constant in enumClass.enumConstants) {
            val name = (constant as Enum<*>).name
            array.add(enumClass.getField(name).getAnnotation(SerializedName::class.java)?.value ?: name)
        }
        return array
    }

    /** The other spellings the mod accepts for a value: the constant names and the `alternate` names of the annotation (the case never matters). */
    private fun enumAliases(enumClass: Class<*>): JsonArray {
        val array = JsonArray()
        for (constant in enumClass.enumConstants) {
            val name = (constant as Enum<*>).name
            val annotation = enumClass.getField(name).getAnnotation(SerializedName::class.java)
            val primary = annotation?.value ?: name
            if (name != primary) array.add(name)
            annotation?.alternate?.forEach(array::add)
        }
        return array
    }

    private fun isDataClass(raw: Class<*>): Boolean =
        !raw.isPrimitive && !raw.isArray && !raw.isEnum && raw.name.startsWith("com.f4xizzz.") &&
            generateSequence(raw) { it.superclass }.takeWhile { it != Any::class.java }.any { c -> c.declaredFields.any { !Modifier.isStatic(it.modifiers) } }

    internal fun field(owner: Class<*>?, jsonName: String): Field? {
        var current = owner
        while (current != null && current != Any::class.java) {
            for (candidate in current.declaredFields) {
                if (Modifier.isStatic(candidate.modifiers) || Modifier.isTransient(candidate.modifiers)) continue
                val serialized = candidate.getAnnotation(SerializedName::class.java)
                val name = serialized?.value ?: candidate.name
                if (name == jsonName || serialized?.alternate?.contains(jsonName) == true) return candidate
            }
            current = current.superclass
        }
        return null
    }

    internal fun rawClass(type: Type?): Class<*>? = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
        is WildcardType -> rawClass(type.upperBounds.firstOrNull())
        else -> null
    }

    internal fun typeArgument(type: Type?, index: Int): Type? {
        val arg = (type as? ParameterizedType)?.actualTypeArguments?.getOrNull(index) ?: return null
        return if (arg is WildcardType) arg.upperBounds.firstOrNull() else arg
    }
}
