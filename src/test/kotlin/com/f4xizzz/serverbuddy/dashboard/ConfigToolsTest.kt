package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What the dashboard's config editor does besides drawing the form: checking values, the history of copies, the diff of a save and the search. */
class ConfigToolsTest {
    private enum class Mode {
        @SerializedName(value = "fast", alternate = ["quick"])
        FAST,
        SLOW
    }

    private class Limits(val max: Int = 10, val min: Int = 1)

    private class Sample(
        @Doc("Turns the thing on.") val enabled: Boolean = true,
        val count: Int = 3,
        val ratio: Double = 0.5,
        val title: String = "Hello world",
        val mode: Mode = Mode.FAST,
        val items: List<Int> = listOf(1, 2),
        val prices: Map<String, Int> = mapOf("apple" to 2),
        val limits: Limits = Limits()
    )

    @TempDir
    lateinit var folder: File

    private fun entry() = ConfigFiles.Entry("Test", "sample.json", Sample::class.java, { Sample() })

    @BeforeEach
    fun setUp() {
        ConfigFiles.resetForTests()
        ConfigFiles.baseDir = { folder }
    }

    @AfterEach
    fun tearDown() {
        ConfigFiles.resetForTests()
    }

    private fun json(text: String) = JsonParser.parseString(text)

    private fun problems(text: String) = ConfigTools.validate(entry(), json(text)).map { it.path to (if (it.warning) "warning" else it.code) }

    // ------------------------------------------------------------------ validation

    @Test
    fun `the defaults and a sensible edit are valid`() {
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"enabled":false,"count":7,"ratio":1.5,"title":"x","mode":"slow","items":[4],"prices":{"a":1},"limits":{"max":5}}"""))
        assertEquals(emptyList<Pair<String, String>>(), problems("{}"), "what the file leaves out keeps its default")
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"count":null,"title":null}"""), "null means the default")
    }

    @Test
    fun `a wrong type is an error with the path of the option`() {
        assertEquals(listOf("count" to "int"), problems("""{"count":"abc"}"""))
        assertEquals(listOf("count" to "int"), problems("""{"count":2.5}"""))
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"count":"5","ratio":"0.25","enabled":"true"}"""), "the numbers and flags as text are read like the mod reads them")
        assertEquals(listOf("enabled" to "boolean"), problems("""{"enabled":"yes"}"""))
        assertEquals(listOf("ratio" to "number"), problems("""{"ratio":true}"""))
        // a value of the wrong SHAPE is left to the load check (the options of many classes take more than their Java type says)
        assertEquals(listOf("" to "load"), problems("""{"title":{"a":1}}"""))
        assertEquals(listOf("" to "load"), problems("""{"items":3}"""))
        assertEquals(listOf("" to "load"), problems("""{"limits":[1]}"""))
    }

    @Test
    fun `lists maps and nested options are checked item by item`() {
        assertEquals(listOf("items[1]" to "int"), problems("""{"items":[1,"x",3]}"""))
        assertEquals(listOf("prices.pear" to "int"), problems("""{"prices":{"apple":1,"pear":"cheap"}}"""))
        assertEquals(listOf("limits.max" to "int"), problems("""{"limits":{"max":"lots"}}"""))
    }

    @Test
    fun `an option that is not on the list names the valid ones`() {
        val found = ConfigTools.validate(entry(), json("""{"mode":"medium"}"""))
        assertEquals(listOf("mode"), found.map { it.path })
        assertEquals("option", found[0].code)
        assertTrue(found[0].message.contains("fast") && found[0].message.contains("SLOW"), found[0].message)
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"mode":"Fast"}"""), "the case does not matter")
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"mode":"quick"}"""), "the other spellings the mod accepts are fine")
        assertEquals(emptyList<Pair<String, String>>(), problems("""{"mode":"FAST"}"""), "so is the name of the constant")
    }

    @Test
    fun `an option the mod does not know is only a warning that does not stop a save`() {
        val found = ConfigTools.validate(entry(), json("""{"colour":"red","limits":{"top":1}}"""))
        assertEquals(setOf("colour", "limits.top"), found.map { it.path }.toSet())
        assertTrue(found.all { it.warning }, "a typo is a warning")
    }

    @Test
    fun `the file must be an object`() {
        val found = ConfigTools.validate(entry(), json("[1,2]"))
        assertEquals(1, found.size)
        assertFalse(found[0].warning)
        assertEquals("", found[0].path)
    }

    // ------------------------------------------------------------------ what a save changes

    @Test
    fun `the diff names only the options that changed`() {
        val old = json("""{"count":3,"title":"a","limits":{"max":10,"min":1},"items":[1,2]}""")
        val new = json("""{"count":4,"title":"a","limits":{"max":10,"min":0},"items":[1,2,3]}""")
        val changes = ConfigTools.changes(old, new)
        assertEquals(listOf("count", "limits.min", "items"), changes.map { it.get("path").asString })
        assertEquals("3", changes[0].get("from").asString)
        assertEquals("4", changes[0].get("to").asString)
        assertEquals(emptyList<JsonObject>(), ConfigTools.changes(old, old))
    }

    @Test
    fun `an option that appears or disappears is a change too, and the list is capped`() {
        val added = ConfigTools.changes(json("""{"a":1}"""), json("""{"a":1,"b":2}"""))
        assertEquals(listOf("b"), added.map { it.get("path").asString })
        assertFalse(added[0].has("from"))
        val many = ConfigTools.changes(json("{}"), JsonObject().apply { for (i in 1..100) addProperty("k$i", i) }, cap = 10)
        assertEquals(10, many.size)
    }

    // ------------------------------------------------------------------ the history of copies

    private fun write(entry: ConfigFiles.Entry, text: String) {
        entry.file.parentFile.mkdirs()
        entry.file.writeText(text)
    }

    @Test
    fun `every backup keeps a time-stamped copy and the old single bak, newest first`() {
        val entry = entry()
        assertEquals(emptyList<String>(), ConfigTools.history(entry).map { it.id }, "no file, no history")
        write(entry, """{"count":1}""")
        ConfigTools.backup(entry)
        Thread.sleep(5)
        write(entry, """{"count":2}""")
        ConfigTools.backup(entry)
        val history = ConfigTools.history(entry)
        assertEquals(2, history.size)
        assertTrue(history.all { Regex("""sample\.json\.\d{8}-\d{6}-\d{3}\.bak""").matches(it.id) })
        assertTrue(history[0].id > history[1].id, "newest first")
        assertEquals("""{"count":2}""", File(entry.file.parentFile, "sample.json.bak").readText(), "the single .bak is the last copy")
    }

    @Test
    fun `only the last twenty copies are kept`() {
        val entry = entry()
        write(entry, """{"count":1}""")
        repeat(25) {
            ConfigTools.backup(entry)
            Thread.sleep(3)
        }
        assertEquals(20, ConfigTools.history(entry).size)
    }

    @Test
    fun `a file with only the old single bak still shows it as the history`() {
        val entry = entry()
        write(entry, """{"count":1}""")
        File(entry.file.parentFile, "sample.json.bak").writeText("""{"count":0}""")
        assertEquals(listOf("sample.json.bak"), ConfigTools.history(entry).map { it.id })
    }

    @Test
    fun `restoring puts the copy back and backs the current file up first`() {
        val entry = entry()
        write(entry, """{"count":1}""")
        ConfigTools.backup(entry)
        val first = ConfigTools.history(entry)[0].id
        Thread.sleep(5)
        write(entry, """{"count":9}""")
        val values = ConfigTools.restore(entry, first).asJsonObject
        assertEquals(1, values.get("count").asInt)
        assertEquals(1, JsonParser.parseString(entry.file.readText()).asJsonObject.get("count").asInt)
        val ids = ConfigTools.history(entry).map { File(entry.file.parentFile, it.id).readText() }
        assertTrue(ids.any { it.contains("9") }, "what was there before the restore is in the history: $ids")
    }

    @Test
    fun `only a copy that is on the list can be restored, never a path`() {
        val entry = entry()
        write(entry, """{"count":1}""")
        File(folder, "secret.json").writeText("""{"count":5}""")
        for (id in listOf("../secret.json", "sample.json", "sample.json.20260101-000000-000.bak", "")) {
            val error = assertThrows<DashboardError> { ConfigTools.restore(entry, id) }
            assertEquals("not_found", error.code, id)
        }
        assertEquals(1, JsonParser.parseString(entry.file.readText()).asJsonObject.get("count").asInt, "nothing changed")
    }

    @Test
    fun `a copy that is not a config file is refused`() {
        val entry = entry()
        write(entry, """{"count":1}""")
        File(entry.file.parentFile, "sample.json.20260101-000000-000.bak").writeText("this is not json {{{")
        val error = assertThrows<DashboardError> { ConfigTools.restore(entry, "sample.json.20260101-000000-000.bak") }
        assertEquals("bad_config", error.code)
        assertEquals(1, JsonParser.parseString(entry.file.readText()).asJsonObject.get("count").asInt)
    }

    // ------------------------------------------------------------------ search

    @Test
    fun `the search finds names comments and text values in every registered file`() {
        ConfigFiles.register("Test", "sample.json", Sample::class.java) { Sample() }
        write(entry(), """{"title":"Welcome to the server","limits":{"max":99}}""")
        fun paths(query: String) = ConfigTools.search(query).map { it.asJsonObject.get("path").asString }

        assertEquals(listOf("enabled"), paths("Turns the thing"), "a comment")
        assertEquals(listOf("limits.max"), paths("max"), "the name of a nested option")
        assertTrue(paths("welcome").contains("title"), "a text value, any case")
        assertEquals(emptyList<String>(), paths("w"), "one letter is not a search")
        assertEquals(emptyList<String>(), paths("zzzzzz"))
        val hit = ConfigTools.search("welcome")[0].asJsonObject
        assertEquals("Test", hit.get("module").asString)
        assertEquals("sample.json", hit.get("file").asString)
        assertEquals("Welcome to the server", hit.get("value").asString)
    }

    @Test
    fun `the search stops at the limit`() {
        ConfigFiles.register("Test", "sample.json", Sample::class.java) { Sample() }
        assertEquals(3, ConfigTools.search("it").size(), "title, items and limits")
        assertEquals(2, ConfigTools.search("it", limit = 2).size())
    }

    @Test
    fun `restoring the legacy bak reads it before backing up overwrites that name`() {
        val entry = entry()
        write(entry, """{"count":9}""")
        File(entry.file.parentFile, "sample.json.bak").writeText("// original copy\n".replace("\\n", "\n") + """{"count":1}""")
        val expected = File(entry.file.parentFile, "sample.json.bak").readText()
        val values = ConfigTools.restore(entry, "sample.json.bak").asJsonObject
        assertEquals(1, values.get("count").asInt)
        assertEquals(expected, entry.file.readText())
        assertTrue(ConfigTools.history(entry).any { File(entry.file.parentFile, it.id).readText().contains("9") })
    }

    @Test
    fun `restoring the oldest of twenty copies survives pruning and retains the current file`() {
        val entry = entry()
        write(entry, """{"count":9}""")
        repeat(20) { i ->
            File(entry.file.parentFile, "sample.json.20260101-000000-${i.toString().padStart(3, '0')}.bak").writeText("""{"count":$i}""")
        }
        val oldest = ConfigTools.history(entry).last().id
        val expected = File(entry.file.parentFile, oldest).readBytes()
        val values = ConfigTools.restore(entry, oldest).asJsonObject
        assertEquals(0, values.get("count").asInt)
        assertTrue(expected.contentEquals(entry.file.readBytes()))
        assertEquals(20, ConfigTools.history(entry).size)
        assertTrue(ConfigTools.history(entry).any { File(entry.file.parentFile, it.id).readText().contains("9") })
    }

}
