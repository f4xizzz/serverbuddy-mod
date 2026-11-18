package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The secrets of the config files never leave the mod: the dashboard gets a marker in their place and the marker coming back means "keep what the file
 * has". These tests are about that promise: what is hidden, what is taken back, and that the rest of the file is never touched.
 */
class ConfigSecretsTest {
    private class Storage(
        val type: String = "json",
        val host: String = "",
        val username: String = "root",
        val password: String = "",
        @SerializedName("url_override") val urlOverride: String = ""
    )

    private class Hook(
        @SerializedName(value = "webhook_url", alternate = ["url"]) val webhookUrl: String = "",
        val username: String = "",
        @SerializedName("avatar_url") val avatarUrl: String = ""
    )

    private class Embed(val title: String = "", val url: String = "")

    private class Sample(
        val enabled: Boolean = true,
        val motd: String = "Hello",
        val storage: Storage = Storage(),
        val hook: Hook = Hook(),
        val hooks: List<Hook> = emptyList(),
        val embeds: List<Embed> = emptyList(),
        val properties: Map<String, String> = emptyMap(),
        val named: Map<String, Hook> = emptyMap()
    )

    private val sample = Sample::class.java

    private val diskText = """
        {
          "enabled": true,
          "motd": "Hello",
          "storage": { "type": "mysql", "host": "db.local", "username": "root", "password": "hunter2-hunter2", "url_override": "mongodb://root:hunter2-hunter2@db.local/x" },
          "hook": { "url": "https://discord.com/api/webhooks/1/AAAA-old-key", "username": "Bot", "avatar_url": "https://img.example/a.png" },
          "hooks": [
            { "webhook_url": "https://discord.com/api/webhooks/2/BBBB-first-one", "username": "one" },
            { "webhook_url": "https://discord.com/api/webhooks/3/CCCC-second-one", "username": "two" }
          ],
          "embeds": [ { "title": "Hi", "url": "https://example.com/page" } ],
          "properties": { "useUnicode": "true", "password": "pw-from-properties" },
          "named": { "alpha": { "webhook_url": "https://discord.com/api/webhooks/4/DDDD-alpha-one" } }
        }
    """.trimIndent()

    private val allSecrets = listOf(
        "hunter2-hunter2", "AAAA-old-key", "BBBB-first-one", "CCCC-second-one", "pw-from-properties", "DDDD-alpha-one"
    )

    @TempDir
    lateinit var folder: File

    private fun entry() = ConfigFiles.Entry("Test", "sample.json", sample, { Sample() })

    @BeforeEach
    fun setUp() {
        ConfigFiles.resetForTests()
        ConfigFiles.baseDir = { folder }
        ConfigSecrets.forget()
    }

    @AfterEach
    fun tearDown() {
        ConfigFiles.resetForTests()
        ConfigSecrets.forget()
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)
    private fun obj(text: String): JsonObject = json(text).asJsonObject
    private fun write(entry: ConfigFiles.Entry, text: String) {
        entry.file.parentFile.mkdirs()
        entry.file.writeText(text)
    }

    private fun assertNoSecret(text: String, what: String, secrets: List<String> = allSecrets) {
        for (secret in secrets) assertFalse(text.contains(secret), "$what shows a secret ($secret): $text")
    }

    private fun strings(array: JsonArray) = array.map { if (it.asJsonPrimitive.isNumber) it.asInt else it.asString }

    // ------------------------------------------------------------------ which options are secrets

    @Test
    fun `a name is a secret whatever its case, underscores or dashes, and so is one that ends with a listed name`() {
        for (name in listOf("password", "PASSWORD", "passwd", "webhookURL", "webhook-url", "WEBHOOK_URL", "urlOverride", "url_override", "apiKey", "api_key", "connection_string")) {
            assertTrue(ConfigSecrets.isSecretName(name), name)
        }
        for (name in listOf("mysql_password", "discordWebhookUrl", "db-Password", "discord_webhook_url", "my_client_secret")) {
            assertTrue(ConfigSecrets.isSecretName(name), name)
        }
        for (name in listOf("username", "avatar_url", "icon_url", "url", "host", "keys", "hold_key", "unknown_token", "password_hint", "passwords", "webhooks", "webhook_rare", "clear_passengers", "check_bypass")) {
            assertFalse(ConfigSecrets.isSecretName(name), name)
        }
    }

    @Test
    fun `the class tells that another spelling of a secret is one too, and a plain url is not`() {
        assertTrue(ConfigSecrets.isSecretKey(Hook::class.java, "url"), "url is the other spelling of webhook_url there")
        assertFalse(ConfigSecrets.isSecretKey(Embed::class.java, "url"), "the link of an embed is public")
        assertFalse(ConfigSecrets.isSecretKey(Hook::class.java, "avatar_url"))
        assertTrue(ConfigSecrets.isSecretKey(null, "password"), "with no class the name decides")
        assertFalse(ConfigSecrets.isSecretKey(null, "url"))
    }

    // ------------------------------------------------------------------ out: markers

    @Test
    fun `mask puts a marker where every secret was, at any depth, and keeps everything else`() {
        val masked = ConfigSecrets.mask(sample, json(diskText)).asJsonObject
        val password = masked.getAsJsonObject("storage").get("password")
        assertTrue(ConfigSecrets.isMarker(password))
        assertEquals("set", password.asJsonObject.get("\$secret").asString)
        assertEquals(listOf("storage", "password"), strings(password.asJsonObject.getAsJsonArray("at")))
        assertTrue(ConfigSecrets.isMarker(masked.getAsJsonObject("storage").get("url_override")))
        assertTrue(ConfigSecrets.isMarker(masked.getAsJsonObject("hook").get("url")), "the other spelling of webhook_url")
        assertEquals(listOf("hooks", 1, "webhook_url"), strings(masked.getAsJsonArray("hooks")[1].asJsonObject.get("webhook_url").asJsonObject.getAsJsonArray("at")), "a list position is a number")
        assertTrue(ConfigSecrets.isMarker(masked.getAsJsonObject("properties").get("password")), "a map key that is a secret name")
        assertTrue(ConfigSecrets.isMarker(masked.getAsJsonObject("named").getAsJsonObject("alpha").get("webhook_url")), "inside a map value")
        // what is not a secret is untouched
        assertEquals("root", masked.getAsJsonObject("storage").get("username").asString)
        assertEquals("mysql", masked.getAsJsonObject("storage").get("type").asString)
        assertEquals("https://img.example/a.png", masked.getAsJsonObject("hook").get("avatar_url").asString)
        assertEquals("https://example.com/page", masked.getAsJsonArray("embeds")[0].asJsonObject.get("url").asString)
        assertEquals("true", masked.getAsJsonObject("properties").get("useUnicode").asString)
        assertNoSecret(masked.toString(), "the masked tree")
    }

    @Test
    fun `an option that is empty says so and one that has a value says set`() {
        val masked = ConfigSecrets.mask(sample, obj("""{"storage":{"password":"","url_override":null},"hook":{"url":"https://x.example/hook-123456"}}""")).asJsonObject
        assertEquals("empty", masked.getAsJsonObject("storage").get("password").asJsonObject.get("\$secret").asString)
        assertEquals("empty", masked.getAsJsonObject("storage").get("url_override").asJsonObject.get("\$secret").asString)
        assertEquals("set", masked.getAsJsonObject("hook").get("url").asJsonObject.get("\$secret").asString)
    }

    @Test
    fun `strip leaves the option on the list and takes the value out`() {
        val stripped = ConfigSecrets.strip(sample, json(diskText)).asJsonObject
        assertTrue(stripped.getAsJsonObject("storage").get("password").isJsonNull)
        assertTrue(stripped.getAsJsonObject("hook").get("url").isJsonNull)
        assertEquals("root", stripped.getAsJsonObject("storage").get("username").asString)
        assertNoSecret(stripped.toString(), "the stripped tree")
    }

    @Test
    fun `the schema marks the secret options and has no value for them, even when its defaults come from the file`() {
        val ordinary = ConfigSchema.schema(entry())
        fun node(fields: JsonArray, vararg path: String): JsonObject {
            var current = fields
            var found: JsonObject? = null
            for (key in path) {
                found = current.map { it.asJsonObject }.first { it.get("key").asString == key }
                current = found.getAsJsonArray("fields") ?: JsonArray()
            }
            return found!!
        }
        val password = node(ordinary.getAsJsonArray("fields"), "storage", "password")
        assertTrue(password.get("secret").asBoolean)
        assertFalse(password.has("default"), "a secret has no default to show")
        assertNull(node(ordinary.getAsJsonArray("fields"), "storage", "username").get("secret"))

        // a class that cannot be built empty takes the options (and their defaults) from the file as it is: the secrets must not get in
        write(entry(), diskText)
        val unbuildable = ConfigFiles.Entry("Test", "sample.json", sample, { throw IllegalStateException("cannot be built empty") })
        val fromFile = ConfigSchema.schema(unbuildable)
        assertNoSecret(fromFile.toString(), "a schema built from the file")
        assertTrue(node(fromFile.getAsJsonArray("fields"), "hook", "url").get("secret").asBoolean, "and the option is still marked")
    }

    // ------------------------------------------------------------------ in: taking the marker back

    private fun resolve(posted: JsonElement, disk: JsonElement? = json(diskText)) = ConfigSecrets.resolve(sample, posted, disk).asJsonObject

    @Test
    fun `what comes back from the site as it went is exactly the file`() {
        val disk = json(diskText)
        assertEquals(disk, ConfigSecrets.resolve(sample, ConfigSecrets.mask(sample, disk), disk))
    }

    @Test
    fun `a new text replaces the secret, clear empties it, and left out or null keeps it`() {
        val masked = ConfigSecrets.mask(sample, json(diskText)).asJsonObject
        masked.getAsJsonObject("storage").addProperty("password", "brand-new-pass")
        masked.getAsJsonObject("storage").add("url_override", ConfigSecrets.marker("clear"))
        masked.getAsJsonObject("hook").remove("url")
        val resolved = resolve(masked)
        assertEquals("brand-new-pass", resolved.getAsJsonObject("storage").get("password").asString)
        assertEquals("", resolved.getAsJsonObject("storage").get("url_override").asString)
        assertEquals("https://discord.com/api/webhooks/1/AAAA-old-key", resolved.getAsJsonObject("hook").get("url").asString, "left out = keep")

        val withNull = ConfigSecrets.mask(sample, json(diskText)).asJsonObject
        withNull.getAsJsonObject("storage").add("password", com.google.gson.JsonNull.INSTANCE)
        assertEquals("hunter2-hunter2", resolve(withNull).getAsJsonObject("storage").get("password").asString, "null = keep")
    }

    @Test
    fun `a marker is only taken back at a secret, so it can never carry a secret into an option the site sees`() {
        // somebody asks for the password to be copied into the motd, to read it back from there
        val posted = obj("""{"motd":{"${'$'}secret":"set","at":["storage","password"]},"storage":{"password":{"${'$'}secret":"set","at":["storage","password"]}}}""")
        val resolved = resolve(posted)
        assertFalse(resolved.has("motd"), "the marker at a plain option carries nothing")
        assertEquals("hunter2-hunter2", resolved.getAsJsonObject("storage").get("password").asString)
        // a marker that points at nothing leaves the option out (the mod uses its default)
        assertFalse(resolve(obj("""{"storage":{"password":{"${'$'}secret":"set","at":["nowhere","here"]}}}""")).getAsJsonObject("storage").has("password"))
    }

    @Test
    fun `items of a list keep their own secret when others are removed or added`() {
        val masked = ConfigSecrets.mask(sample, json(diskText)).asJsonObject
        val hooks = masked.getAsJsonArray("hooks")
        val second = hooks[1].deepCopy()
        // the first item is deleted in the form: the second one is now at position 0
        masked.add("hooks", JsonArray().also { it.add(second) })
        assertEquals("https://discord.com/api/webhooks/3/CCCC-second-one", resolve(masked).getAsJsonArray("hooks")[0].asJsonObject.get("webhook_url").asString)

        // a new item has the "empty" marker the form makes for it; one with no way to find its old value gets nothing
        val newItem = obj("""{"webhook_url":{"${'$'}secret":"empty"},"username":"new"}""")
        val lost = obj("""{"webhook_url":{"${'$'}secret":"set"},"username":"lost"}""")
        masked.add("hooks", JsonArray().also { it.add(second); it.add(newItem); it.add(lost) })
        val items = resolve(masked).getAsJsonArray("hooks")
        assertEquals("", items[1].asJsonObject.get("webhook_url").asString)
        assertFalse(items[2].asJsonObject.has("webhook_url"), "the position of an item in the list says nothing about its old secret")
    }

    @Test
    fun `an option in a map keeps its secret through the marker even when the entry is renamed`() {
        val masked = ConfigSecrets.mask(sample, json(diskText)).asJsonObject
        val alpha = masked.getAsJsonObject("named").remove("alpha")
        masked.getAsJsonObject("named").add("beta", alpha)
        assertEquals("https://discord.com/api/webhooks/4/DDDD-alpha-one", resolve(masked).getAsJsonObject("named").getAsJsonObject("beta").get("webhook_url").asString)
    }

    @Test
    fun `a secret under the other spelling stays where the file has it when the post leaves it out`() {
        val posted = obj("""{"motd":"changed","hook":{"username":"Bot"}}""")
        val resolved = resolve(posted)
        assertEquals("https://discord.com/api/webhooks/1/AAAA-old-key", resolved.getAsJsonObject("hook").get("url").asString)
    }

    @Test
    fun `the secrets of a file that is not there yet come out empty or left out`() {
        val resolved = resolve(ConfigSecrets.mask(sample, json(diskText)), disk = null)
        assertFalse(resolved.getAsJsonObject("storage").has("password"), "nothing on disk to keep")
        assertEquals("root", resolved.getAsJsonObject("storage").get("username").asString)
    }

    // ------------------------------------------------------------------ a whole save

    private fun canonical(entry: ConfigFiles.Entry, text: String): String = ConfigDocs.renderTree(json(text), sample, entry.defaultsWriter)

    @Test
    fun `saving what the site got, without touching a secret, leaves the file identical byte by byte`() {
        val entry = entry()
        write(entry, "// A hand-written comment stays, including CRLF and spacing.\r\n" + diskText.replace("\n", "\r\n") + "\r\n")
        entry.file.setLastModified(1700000000000L)
        val modified = entry.file.lastModified()
        val before = entry.file.readText()
        val masked = ConfigSchema.values(entry)
        assertNoSecret(masked.toString(), "config.get")
        val result = ConfigSchema.save(entry, masked)
        assertTrue(result.written, result.problems.joinToString { it.message })
        assertEquals(before, entry.file.readText())
        assertEquals(emptyList<JsonObject>(), result.changes)
        assertEquals(modified, entry.file.lastModified(), "a no-op does not rewrite the file")
        assertTrue(ConfigTools.history(entry).isEmpty(), "a no-op creates no backup")
    }

    @Test
    fun `changing another option keeps every secret as it was and the answer never shows one`() {
        val entry = entry()
        write(entry, canonical(entry, diskText))
        val masked = ConfigSchema.values(entry).asJsonObject
        masked.addProperty("motd", "A new motd")
        val result = ConfigSchema.save(entry, masked)
        assertTrue(result.written)
        assertEquals(listOf("motd"), result.changes.map { it.get("path").asString })
        val saved = JsonParser.parseString(entry.file.readText()).asJsonObject
        assertEquals("A new motd", saved.get("motd").asString)
        assertEquals("hunter2-hunter2", saved.getAsJsonObject("storage").get("password").asString)
        assertEquals("https://discord.com/api/webhooks/2/BBBB-first-one", saved.getAsJsonArray("hooks")[0].asJsonObject.get("webhook_url").asString)
        assertNoSecret(result.changes.toString(), "the diff")
    }

    @Test
    fun `a new secret is written, said to be changed, and never repeated`() {
        val entry = entry()
        write(entry, canonical(entry, diskText))
        val masked = ConfigSchema.values(entry).asJsonObject
        masked.getAsJsonObject("storage").addProperty("password", "a-different-pass-1")
        masked.getAsJsonObject("hook").add("url", ConfigSecrets.marker("clear"))
        val result = ConfigSchema.save(entry, masked)
        assertTrue(result.written)
        val saved = JsonParser.parseString(entry.file.readText()).asJsonObject
        assertEquals("a-different-pass-1", saved.getAsJsonObject("storage").get("password").asString)
        assertEquals("", saved.getAsJsonObject("hook").get("url").asString)
        val changes = result.changes.associateBy { it.get("path").asString }
        assertEquals("changed", changes.getValue("storage.password").get("change").asString)
        assertTrue(changes.getValue("storage.password").get("secret").asBoolean)
        assertEquals("cleared", changes.getValue("hook.url").get("change").asString)
        assertFalse(changes.getValue("storage.password").has("from") || changes.getValue("storage.password").has("to"))
        assertNoSecret(result.changes.toString(), "the diff", allSecrets + "a-different-pass-1")
    }

    @Test
    fun `the check of a form with markers does not need the secrets and refuses a real mistake`() {
        val entry = entry()
        write(entry, canonical(entry, diskText))
        val masked = ConfigSchema.values(entry)
        assertEquals(emptyList<ConfigTools.Problem>(), ConfigTools.validate(entry, masked).filter { !it.warning })
        val broken = masked.deepCopy().asJsonObject.apply { addProperty("enabled", "maybe") }
        assertEquals(listOf("enabled"), ConfigTools.validate(entry, broken).filter { !it.warning }.map { it.path })
    }

    @Test
    fun `restoring a copy gives the file back exactly and answers with markers`() {
        val entry = entry()
        write(entry, canonical(entry, diskText))
        ConfigTools.backup(entry)
        val copy = ConfigTools.history(entry)[0].id
        Thread.sleep(5)
        write(entry, canonical(entry, diskText.replace("hunter2-hunter2", "later-pass-9999")))
        val answer = ConfigTools.restore(entry, copy)
        assertNoSecret(answer.toString(), "config.restore", allSecrets + "later-pass-9999")
        assertTrue(entry.file.readText().contains("hunter2-hunter2"), "undoing puts the old secret back")
    }

    // ------------------------------------------------------------------ search, texts and logs

    @Test
    fun `the search finds the name of a secret but never its value`() {
        ConfigFiles.register("Test", "sample.json", sample) { Sample() }
        write(entry(), diskText)
        fun hits(query: String) = ConfigTools.search(query)
        assertTrue(hits("password").any { it.asJsonObject.get("path").asString == "storage.password" }, "the name is found")
        assertEquals(0, hits("hunter2").size(), "the value is not")
        assertEquals(0, hits("AAAA-old").size())
        assertNoSecret(hits("password").toString() + hits("webhook").toString() + hits("url").toString(), "the search")
    }

    @Test
    fun `a diff of lists shows (secret) instead of the value`() {
        val old = obj("""{"hooks":[{"webhook_url":"https://discord.com/api/webhooks/2/BBBB-first-one","username":"one"}]}""")
        val new = obj("""{"hooks":[{"webhook_url":"https://discord.com/api/webhooks/2/BBBB-first-one","username":"two"}]}""")
        val changes = ConfigTools.changes(old, new, sample)
        assertEquals(1, changes.size)
        assertNoSecret(changes.toString(), "the diff of a list")
        assertTrue(changes[0].get("from").asString.contains("(secret)"))
        // only a secret inside a list is different
        val onlySecret = ConfigTools.changes(old, obj("""{"hooks":[{"webhook_url":"https://discord.com/api/webhooks/9/ZZZZ-other-one","username":"one"}]}"""), sample)
        assertTrue(onlySecret[0].get("secret").asBoolean)
        assertNoSecret(onlySecret.toString(), "that diff", allSecrets + "ZZZZ-other-one")
    }

    @Test
    fun `a secret that goes from nothing to something, or back, is said so without its value`() {
        val none = obj("""{"storage":{"password":""}}""")
        val some = obj("""{"storage":{"password":"fresh-pass-1234"}}""")
        assertEquals("set", ConfigTools.changes(none, some, sample)[0].get("change").asString)
        assertEquals("cleared", ConfigTools.changes(some, none, sample)[0].get("change").asString)
        assertEquals(emptyList<JsonObject>(), ConfigTools.changes(none, obj("""{"storage":{"password":null}}"""), sample), "empty to empty is no change")
    }

    @Test
    fun `texts never quote a secret including a short configured password`() {
        val text = "failed to open mongodb://root:hunter2-hunter2@db.local/x with the key AAAA-old-key"
        val redacted = ConfigSecrets.redact(text, listOf("hunter2-hunter2", "AAAA-old-key"))
        assertEquals("failed to open mongodb://root:…@db.local/x with the key …", redacted)
        assertEquals("the … user", ConfigSecrets.redact("the root user", listOf("root")))
        assertEquals("password=…", ConfigSecrets.redact("password=12345", listOf("12345")))
    }

    @Test
    fun `everything the files hold secret is hidden from the live logs and the reload errors`() {
        ConfigFiles.register("Test", "sample.json", sample) { Sample() }
        write(entry(), diskText)
        ConfigSecrets.forget()
        val line = com.f4xizzz.serverbuddy.dashboard.LogBuffer.Line(1L, "ERROR", "could not post to https://discord.com/api/webhooks/1/AAAA-old-key (HTTP 404)", "serverbuddy")
        val text = com.f4xizzz.serverbuddy.dashboard.LogBuffer.toJson(line).get("text").asString
        assertFalse(text.contains("AAAA-old-key"), text)
        assertTrue(text.contains("could not post to"), "the rest of the line stays")
        // a secret that was just saved is known at once, before the files are read again
        ConfigSecrets.remember(listOf("just-saved-secret-1"))
        assertEquals("pw …", ConfigSecrets.redactEverywhere("pw just-saved-secret-1"))
    }

    @Test
    fun `a canonical secret replaces clears or preserves its legacy alias without adding it back`() {
        val disk = obj("""{"hook":{"url":"old-private-value"}}""")
        for (posted in listOf(
            obj("""{"hook":{"webhook_url":"new-private-value"}}"""),
            JsonObject().apply { add("hook", JsonObject().apply { add("webhook_url", ConfigSecrets.marker("clear")) }) }
        )) {
            val resolved = ConfigSecrets.resolve(sample, posted, disk).asJsonObject
            val hook = resolved.getAsJsonObject("hook")
            assertFalse(hook.has("url"), "the old alias must not override the explicit new value")
            val expected = if (posted.getAsJsonObject("hook").get("webhook_url").isJsonObject) "" else "new-private-value"
            assertEquals(expected, entry().gson.fromJson(resolved, sample).hook.webhookUrl)
        }
        val preserving = JsonObject().apply { add("hook", JsonObject().apply { add("webhook_url", ConfigSecrets.marker("keep")) }) }
        assertEquals("old-private-value", entry().gson.fromJson(ConfigSecrets.resolve(sample, preserving, disk), sample).hook.webhookUrl)
        assertFalse(ConfigSecrets.resolve(sample, preserving, JsonObject()).asJsonObject.getAsJsonObject("hook").has("webhook_url"))
    }

    @Test
    fun `an unreadable file changed after opening the form is refused instead of discarding preserved secrets`() {
        val entry = entry()
        write(entry, diskText)
        val posted = ConfigSchema.values(entry)
        val broken = """{"storage":{"password":"private-value-in-broken-file" """
        write(entry, broken)
        val error = assertThrows(DashboardError::class.java) { ConfigSchema.save(entry, posted) }
        assertEquals("bad_config", error.code)
        assertFalse(error.message.orEmpty().contains("private-value-in-broken-file"))
        assertEquals(broken, entry.file.readText())
        assertTrue(ConfigTools.history(entry).isEmpty())
    }

    @Test
    fun `old log lines remain redacted after a refresh replaces clears or restores a credential`() {
        ConfigFiles.register("Test", "sample.json", sample) { Sample() }
        val entry = entry()
        write(entry, diskText)
        ConfigSecrets.knownValues()
        val old = LogBuffer.Line(1, "WARN", "password hunter2-hunter2", "ServerBuddy/Test")
        write(entry, diskText.replace("hunter2-hunter2", "changed-private-value"))
        ConfigSecrets.refresh()
        assertEquals("password …", LogBuffer.toJson(old).get("text").asString)
        assertEquals("pw …", ConfigSecrets.redactEverywhere("pw changed-private-value"))
        val live = ConfigSchema.values(entry).asJsonObject
        live.getAsJsonObject("storage").add("password", ConfigSecrets.marker("clear"))
        ConfigSchema.save(entry, live)
        ConfigSecrets.refresh()
        assertEquals("pw …", ConfigSecrets.redactEverywhere("pw changed-private-value"))
        assertFalse(LogBuffer.Filter(query = "hunter2-hunter2").matches(old), "log search must not expose the old credential by matching it")
    }


    @Test
    fun `null or omitted fixed objects retain only their secret options but removed lists and maps stay removed`() {
        val disk = obj(diskText)
        for (posted in listOf(obj("{}"), obj("""{"storage":null,"hook":null,"hooks":[],"named":{}}"""))) {
            val resolved = ConfigSecrets.resolve(sample, posted, disk).asJsonObject
            assertEquals(setOf("password", "url_override"), resolved.getAsJsonObject("storage").keySet())
            assertEquals("hunter2-hunter2", resolved.getAsJsonObject("storage").get("password").asString)
            assertEquals(setOf("url"), resolved.getAsJsonObject("hook").keySet())
            assertFalse(resolved.has("motd"), "ordinary options remain omitted")
            assertTrue(!resolved.has("hooks") || resolved.getAsJsonArray("hooks").size() == 0)
            assertTrue(!resolved.has("named") || resolved.getAsJsonObject("named").size() == 0)
            assertFalse(resolved.has("properties"), "a removed map is not rebuilt from old entries")
        }
    }

    @Test
    fun `a marker from before an alias rename resolves the current secret by its typed path`() {
        val old = obj("""{"hook":{"url":"old-private-value"}}""")
        val posted = ConfigSecrets.mask(sample, old)
        val current = obj("""{"hook":{"webhook_url":"current-private-value"}}""")
        val resolved = ConfigSecrets.resolve(sample, posted, current)
        assertEquals("current-private-value", entry().gson.fromJson(resolved, sample).hook.webhookUrl)
    }

    @Test
    fun `keep preserves a null secret and an absent secret even if the marker contains a path`() {
        val keep = ConfigSecrets.marker("keep", listOf("hook", "url"))
        val posted = obj("""{"storage":{}}""").apply { getAsJsonObject("storage").add("password", keep) }
        val current = obj("""{"storage":{"password":null},"hook":{"url":"other-private-value"}}""")
        assertTrue(ConfigSecrets.resolve(sample, posted, current).asJsonObject.getAsJsonObject("storage").get("password").isJsonNull)
        assertFalse(ConfigSecrets.resolve(sample, posted, obj("{}" )).asJsonObject.getAsJsonObject("storage").has("password"))
    }


    @Test
    fun `the private database file remains unregistered but its password is hidden in outgoing logs`() {
        val file = File(folder, "Database/database.conf")
        file.parentFile.mkdirs()
        file.writeText("""{"mysql":{"password":"private-database-password"}}""")
        assertTrue(ConfigFiles.entries().none { it.folder == "Database" })
        val line = LogBuffer.Line(1, "ERROR", "Connection failed with private-database-password", "ServerBuddy")
        assertEquals("Connection failed with …", LogBuffer.toJson(line).get("text").asString)
    }

}
