package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.database.DatabaseConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The secrets of the config files: database logins, webhook addresses, anything that lets someone else act as the server. The dashboard is a web
 * page that goes through the site, so the mod NEVER sends the value of one of these options there (not in `config.get`, `config.schema`,
 * `config.search`, `config.validate`, the diff of a save, `config.restore`, error messages, the audit or the live logs). What goes instead is a
 * MARKER in the place of the value, and the marker coming back means "keep what the file has".
 *
 * **The marker** is a JSON object, never a text, so it can never be mistaken for a real password:
 *  - `{"$secret":"set","at":[…]}` the option has a value; `at` is where it is in the file now (a path of names and list positions)
 *  - `{"$secret":"empty","at":[…]}` the option is there but empty (`at` only when the file has the option)
 *  - from the site: `{"$secret":"clear"}` = empty the option; any plain text = the new value; the option left out (or null) = keep what the file has
 *
 * A marker is only taken back at an option that is a secret itself, and `at` is only read from the file as it is: a marker can never carry a secret
 * into an option that is shown to the site (that would be a way to read it back).
 *
 * **Which options** is decided by the NAME the mod writes in its files ([NAMES]; a name that ENDS with one of them counts too: `mysql_password`,
 * `discordWebhookUrl`), looked up through the config class, so the other spellings the mod reads (`alternate` names, such as `url` for `webhook_url`
 * in the Crates webhooks) are secrets too. `bot-tests/config-secrets-test.js` fails when an option of any config file has a name that looks like a
 * secret and is neither covered by [NAMES] nor in its list of reasons why not.
 */
object ConfigSecrets {
    /**
     * The names (as written in the file; case, `_` and `-` do not matter, and a longer name that ends with one is covered too) of the options whose
     * value is a secret, at any depth. One list, on purpose: add a name here and every path above hides it.
     */
    val NAMES: List<String> = listOf(
        "password", "passwd", // the database logins (Shop storage and logging, Crates, Kits)
        "url_override", "connection_string", // a complete JDBC or MongoDB address: it carries the login (mongodb://user:password@host)
        "webhook_url", // Discord webhooks (webhook.conf and the files of Crates, Announcements, Events, GTS, the server logs...): the address IS the key
        "secret", "client_secret", "secret_key", "private_key", "api_key", "access_token", "auth_token", "bot_token" // nothing uses these today; here so the next one starts hidden
    )

    private const val MARKER = "\$secret"
    private const val AT = "at"

    private val normalized: List<String> = NAMES.map(::normalize)

    private fun normalize(name: String) = name.lowercase(Locale.ROOT).filter { it != '_' && it != '-' && it != ' ' }

    /** Is [name] the name of a secret option? (A name that ends with a listed one is: `mysql_password`, `discordWebhookUrl`.) */
    fun isSecretName(name: String): Boolean {
        val wanted = normalize(name)
        return normalized.any { wanted == it || wanted.endsWith(it) }
    }

    /**
     * Is the option [key] of [owner] (the type of the object it is in; null when unknown) a secret? The class tells the real name of an option that
     * the file spells another way; an option the class does not know is judged by the name it has in the file. The keys of a map are names the admin chose.
     */
    fun isSecretKey(owner: Type?, key: String): Boolean {
        if (isSecretName(key)) return true
        val raw = ConfigSchema.rawClass(owner) ?: return false
        if (Map::class.java.isAssignableFrom(raw)) return false
        val field = ConfigSchema.field(raw, key) ?: return false
        return isSecretName(field.getAnnotation(SerializedName::class.java)?.value ?: field.name)
    }

    /** The canonical spelling identifies aliases of the same option; map keys remain literal. */
    private fun optionName(type: Type?, key: String): String {
        val raw = ConfigSchema.rawClass(type) ?: return key
        if (Map::class.java.isAssignableFrom(raw)) return key
        val field = ConfigSchema.field(raw, key) ?: return key
        return field.getAnnotation(SerializedName::class.java)?.value ?: field.name
    }

    /** The type of the option [key] inside [type] (a data class, or a map whose values all have one type). */
    internal fun childType(type: Type?, key: String): Type? {
        val raw = ConfigSchema.rawClass(type) ?: return null
        return if (Map::class.java.isAssignableFrom(raw)) ConfigSchema.typeArgument(type, 1) else ConfigSchema.field(raw, key)?.genericType
    }

    /** The type of the items of a list. */
    internal fun itemType(type: Type?): Type? {
        val raw = ConfigSchema.rawClass(type) ?: return null
        return if (raw.isArray) raw.componentType else ConfigSchema.typeArgument(type, 0)
    }

    // ------------------------------------------------------------------ the marker

    fun marker(state: String, at: List<Any>? = null): JsonObject = JsonObject().apply {
        addProperty(MARKER, state)
        if (at != null) add(AT, JsonArray().also { array -> at.forEach { if (it is Int) array.add(it) else array.add(it.toString()) } })
    }

    fun isMarker(element: JsonElement?): Boolean =
        element != null && element.isJsonObject && element.asJsonObject.get(MARKER)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString } == true

    /** "set" when the value says something, "empty" when it does not (nothing, null, empty text, an empty list). */
    fun stateOf(value: JsonElement?): String = when {
        value == null || value.isJsonNull -> "empty"
        value.isJsonPrimitive -> if (value.asString.isEmpty()) "empty" else "set"
        value.isJsonArray -> if (value.asJsonArray.size() == 0) "empty" else "set"
        else -> if (value.asJsonObject.size() == 0) "empty" else "set"
    }

    // ------------------------------------------------------------------ out: what the site may see

    /** [tree] (a config file as it is on disk) with a marker in place of every secret. */
    fun mask(type: Type?, tree: JsonElement): JsonElement = maskAt(type, tree, emptyList())

    private fun maskAt(type: Type?, node: JsonElement, at: List<Any>): JsonElement = when {
        node.isJsonObject -> JsonObject().also { out ->
            for ((key, value) in node.asJsonObject.entrySet()) {
                val here = at + key
                out.add(key, if (isSecretKey(type, key)) marker(stateOf(value), here) else maskAt(childType(type, key), value, here))
            }
        }
        node.isJsonArray -> JsonArray().also { out -> node.asJsonArray.forEachIndexed { index, element -> out.add(maskAt(itemType(type), element, at + index)) } }
        else -> node
    }

    /** [tree] with the value of every secret replaced by null (the defaults of the schema: the option stays on the list, the value does not). */
    fun strip(type: Type?, tree: JsonElement): JsonElement = when {
        tree.isJsonObject -> JsonObject().also { out ->
            for ((key, value) in tree.asJsonObject.entrySet()) out.add(key, if (isSecretKey(type, key)) JsonNull.INSTANCE else strip(childType(type, key), value))
        }
        tree.isJsonArray -> JsonArray().also { out -> tree.asJsonArray.forEach { out.add(strip(itemType(type), it)) } }
        else -> tree
    }

    /** [tree] as text for a person to read (the review of a save): a secret that has a value shows as "(secret)", never as itself. */
    fun shown(type: Type?, tree: JsonElement): JsonElement = when {
        tree.isJsonObject -> JsonObject().also { out ->
            for ((key, value) in tree.asJsonObject.entrySet()) {
                out.add(key, if (isSecretKey(type, key)) (if (stateOf(value) == "set") JsonPrimitive("(secret)") else JsonPrimitive("")) else shown(childType(type, key), value))
            }
        }
        tree.isJsonArray -> JsonArray().also { out -> tree.asJsonArray.forEach { out.add(shown(itemType(type), it)) } }
        else -> tree
    }

    // ------------------------------------------------------------------ in: what the site posts

    /**
     * What a save puts in the file: [posted] (from the site) with every marker and every left-out secret taken from [disk] (the file as it is now, or
     * null). A plain text at a secret is the new value. A secret inside a list is only kept through its marker (the position of an item is not a
     * reliable way to find its old value after items were removed).
     */
    fun resolve(type: Type?, posted: JsonElement, disk: JsonElement?): JsonElement = resolveNode(type, posted, disk, disk, type) ?: JsonNull.INSTANCE

    private fun resolveNode(type: Type?, posted: JsonElement, sameOnDisk: JsonElement?, root: JsonElement?, rootType: Type?): JsonElement? {
        if (isMarker(posted)) return null // a marker anywhere but at a secret option carries nothing
        if (posted.isJsonObject) {
            val postedObject = posted.asJsonObject
            val diskObject = sameOnDisk as? JsonObject
            val out = JsonObject()
            val postedNames = postedObject.keySet().map { optionName(type, it) }.toSet()
            for ((key, value) in postedObject.entrySet()) {
                val diskKey = diskObject?.keySet()?.lastOrNull { optionName(type, it) == optionName(type, key) }
                val onDisk = diskObject?.get(diskKey ?: key)
                if (isSecretKey(type, key)) {
                    val kept = when {
                        isMarker(value) -> fromMarker(value.asJsonObject, onDisk, root, rootType)
                        value.isJsonNull -> onDisk // null = keep
                        else -> value // the new value
                    }
                    if (kept != null) out.add(key, kept)
                } else {
                    val child = childType(type, key)
                    val resolved = if (value.isJsonNull) secretProjection(child, onDisk) ?: value else resolveNode(child, value, onDisk, root, rootType)
                    if (resolved != null) out.add(key, resolved)
                }
            }
            // a secret the post leaves out stays as the file has it
            if (diskObject != null) for ((key, value) in diskObject.entrySet()) {
                if (optionName(type, key) in postedNames) continue
                if (isSecretKey(type, key)) out.add(key, value)
                else if (ConfigSchema.rawClass(type)?.let { !Map::class.java.isAssignableFrom(it) } != false) secretProjection(childType(type, key), value)?.let { out.add(key, it) }
            }
            return out
        }
        if (posted.isJsonArray) {
            val out = JsonArray()
            posted.asJsonArray.forEach { element -> out.add(resolveNode(itemType(type), element, null, root, rootType) ?: JsonNull.INSTANCE) }
            return out
        }
        return posted
    }

    /** Only fixed objects are projected: removing a list or a map entry must still remove that entry. */
    private fun secretProjection(type: Type?, disk: JsonElement?): JsonObject? {
        val raw = ConfigSchema.rawClass(type) ?: return null
        if (!raw.name.startsWith("com.f4xizzz.") || Map::class.java.isAssignableFrom(raw) || Collection::class.java.isAssignableFrom(raw) || raw.isArray || disk !is JsonObject) return null
        val out = JsonObject()
        for ((key, value) in disk.entrySet()) {
            if (isSecretKey(type, key)) out.add(key, value)
            else if (ConfigSchema.rawClass(type)?.let { !Map::class.java.isAssignableFrom(it) } != false) secretProjection(childType(type, key), value)?.let { out.add(key, it) }
        }
        return out.takeIf { it.size() > 0 }
    }

    private fun fromMarker(marker: JsonObject, sameOnDisk: JsonElement?, root: JsonElement?, rootType: Type?): JsonElement? {
        val state = marker.get(MARKER)?.takeIf { it.isJsonPrimitive }?.asString
        if (state == "clear") return JsonPrimitive("")
        if (state == "keep") return sameOnDisk
        val at = marker.get(AT)?.takeIf { it.isJsonArray }?.asJsonArray
        return when {
            at != null -> valueAt(root, at, rootType) // null = the option is not there any more: it is left out, the mod uses its default
            state == "set" || state == "keep" -> sameOnDisk
            else -> JsonPrimitive("") // "empty" (a new item) or something this mod does not know
        }
    }

    private fun valueAt(root: JsonElement?, at: JsonArray, rootType: Type?): JsonElement? {
        var current = root
        var type = rootType
        for (segment in at) {
            if (!segment.isJsonPrimitive) return null
            val step = segment.asJsonPrimitive
            current = when {
                current != null && current.isJsonObject && step.isString -> {
                    val wanted = optionName(type, step.asString)
                    val key = current.asJsonObject.keySet().lastOrNull { optionName(type, it) == wanted } ?: return null
                    val result = current.asJsonObject.get(key)
                    type = childType(type, key)
                    result
                }
                current != null && current.isJsonArray && step.isNumber -> current.asJsonArray.let { array ->
                    type = itemType(type)
                    array.takeIf { step.asInt in 0 until it.size() }?.get(step.asInt)
                }
                else -> return null
            }
        }
        return current
    }

    // ------------------------------------------------------------------ the values themselves (to keep them out of texts)

    /** Every secret value in [tree] that is not empty. */
    fun secretValues(type: Type?, tree: JsonElement, out: MutableSet<String> = LinkedHashSet()): MutableSet<String> {
        when {
            tree.isJsonObject -> for ((key, value) in tree.asJsonObject.entrySet()) {
                if (isSecretKey(type, key)) collectText(value, out) else secretValues(childType(type, key), value, out)
            }
            tree.isJsonArray -> tree.asJsonArray.forEach { secretValues(itemType(type), it, out) }
        }
        return out
    }

    private fun collectText(value: JsonElement, out: MutableSet<String>) {
        when {
            value.isJsonPrimitive -> value.asString.takeIf { it.isNotEmpty() && !isMarker(value) }?.let(out::add)
            value.isJsonArray -> value.asJsonArray.forEach { collectText(it, out) }
            value.isJsonObject && !isMarker(value) -> value.asJsonObject.entrySet().forEach { collectText(it.value, out) }
        }
    }

    /** [text] with every one of [values] replaced by "…" (an error message that quotes what it was reading, a log line). */
    fun redact(text: String, values: Collection<String>): String {
        var result = text
        for (value in values.filter { it.isNotEmpty() }.sortedByDescending { it.length }) result = result.replace(value, "…")
        return result
    }

    // The secrets of EVERY config file, kept for the texts that are not about one file (the live logs, a reload error). The first use reads the files;
    // after that they are read again in the background (and the save of a secret adds it at once).
    private val known = AtomicReference<Set<String>>(emptySet())
    @Volatile private var scannedAt = 0L
    private val scanning = AtomicBoolean(false)
    private const val SCAN_EVERY_MS = 30_000L
    private val scanner = Executors.newSingleThreadExecutor { task -> Thread(task, "ServerBuddy-secrets").apply { isDaemon = true } }

    private fun scan(): Set<String> {
        val found = LinkedHashSet<String>()
        // The private database file stays outside the dashboard registry, but its credentials must also be hidden in logs.
        val entries = try { ConfigFiles.entries() + ConfigFiles.Entry("Database", "database.conf", DatabaseConfig::class.java, { DatabaseConfig() }) } catch (e: Throwable) { return found }
        for (entry in entries) {
            val tree = ConfigSchema.rawFile(entry) ?: continue
            try { secretValues(entry.type, tree, found) } catch (e: Throwable) { /* a file that does not match its class: nothing to hide in it */ }
        }
        return found
    }

    /** Every secret seen during this server session, refreshed from disk at most every 30 s. Old log lines stay protected after rotation. */
    fun knownValues(): Set<String> {
        val now = System.currentTimeMillis()
        if (scannedAt == 0L) {
            synchronized(this) {
                if (scannedAt == 0L) {
                    val found = scan()
                    known.updateAndGet { it + found }
                    scannedAt = now
                }
            }
        } else if (now - scannedAt > SCAN_EVERY_MS && scanning.compareAndSet(false, true)) {
            scanner.execute {
                try {
                    val found = scan()
                    known.updateAndGet { it + found } // old lines can still contain a credential that was rotated or cleared
                    scannedAt = System.currentTimeMillis()
                } finally {
                    scanning.set(false)
                }
            }
        }
        return known.get()
    }

    /** Hides what a save just wrote (or restored) from the texts too, without waiting for the next read of the files. */
    fun remember(values: Collection<String>) {
        if (values.isNotEmpty()) known.updateAndGet { it + values }
    }

    /** [text] without any secret of any config file (for the live logs and the errors of a reload). */
    fun redactEverywhere(text: String): String = redact(text, knownValues())

    /** Hide already known values while capturing a log line, without doing file I/O inside the appender. */
    fun redactKnown(text: String): String = redact(text, known.get())

    /** A reload rereads the files while retaining credentials that may still be present in buffered logs. */
    @Synchronized
    fun refresh() {
        val found = scan()
        known.updateAndGet { it + found }
        scannedAt = System.currentTimeMillis()
    }

    /** Test isolation only. Production must retain old secrets for the lifetime of its log buffer. */
    internal fun forget() {
        known.set(emptySet())
        scannedAt = 0L
    }
}
