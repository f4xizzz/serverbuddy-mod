package com.f4xizzz.serverbuddy.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The registry of config files the dashboard (and `/serverbuddy config check`) work from. */
class ConfigFilesTest {
    private class Basic(val on: Boolean = true)

    @TempDir
    lateinit var folder: File

    @BeforeEach
    fun setUp() {
        ConfigFiles.resetForTests()
        ConfigFiles.baseDir = { folder }
    }

    @AfterEach
    fun tearDown() {
        ConfigFiles.resetForTests()
    }

    private fun labels() = ConfigFiles.entries().map { it.label }

    @Test
    fun `a file its module loads itself is listed for the dashboard but not tracked by config check`() {
        var reloads = 0
        ConfigFiles.register("Java", "thing.conf", Basic::class.java, reload = { reloads++ }) { Basic() }
        assertEquals(listOf("Java/thing.conf"), labels())
        assertEquals(emptyList<String>(), ConfigFiles.tracked().map { it.label })
        val entry = ConfigFiles.entries().single()
        assertFalse(entry.tracked)
        entry.reload!!.invoke()
        assertEquals(1, reloads)
        assertEquals(File(folder, "Java/thing.conf"), entry.file)
    }

    @Test
    fun `a file loaded the usual way is tracked and listed, and written with its defaults when it is missing`() {
        val loaded = ConfigFiles.load("Kotlin", "basic.json", Basic::class.java) { Basic(on = false) }
        assertFalse(loaded.on)
        assertTrue(File(folder, "Kotlin/basic.json").isFile, "the default file is created")
        assertEquals(listOf("Kotlin/basic.json"), ConfigFiles.tracked().map { it.label })
        assertEquals(listOf("Kotlin/basic.json"), labels())
        val untracked = ConfigFiles.load("Kotlin/free", "pool.json", Basic::class.java, track = false) { Basic() }
        assertTrue(untracked.on)
        assertEquals(listOf("Kotlin/basic.json"), labels(), "an untracked load is not registered by itself")
    }

    @Test
    fun `a folder of content lists each file and sees the new ones without a restart`() {
        File(folder, "Content/items").apply { mkdirs() }
        File(folder, "Content/items/b.json").writeText("{}")
        File(folder, "Content/items/a.json").writeText("{}")
        File(folder, "Content/items/notes.txt").writeText("not config")
        File(folder, "Content/items/sub").mkdirs()
        ConfigFiles.collection("Content/items", Basic::class.java) { Basic() }
        assertEquals(listOf("Content/items/a.json", "Content/items/b.json"), labels(), "only .json files, sorted")
        assertEquals(emptyList<String>(), ConfigFiles.tracked().map { it.label }, "content is never part of config check")

        File(folder, "Content/items/c.json").writeText("{}")
        assertEquals(3, labels().size)
        File(folder, "Content/items/a.json").delete()
        assertEquals(listOf("Content/items/b.json", "Content/items/c.json"), labels())
    }

    @Test
    fun `a folder that does not exist yet lists nothing and is not created by looking`() {
        ConfigFiles.collection("Nowhere/at/all", Basic::class.java) { Basic() }
        assertEquals(emptyList<String>(), labels())
        assertFalse(File(folder, "Nowhere").exists())
    }

    @Test
    fun `a file registered by name wins over the same file found in a folder`() {
        File(folder, "Mixed").mkdirs()
        File(folder, "Mixed/one.json").writeText("{}")
        ConfigFiles.register("Mixed", "one.json", Basic::class.java, reload = { }) { Basic() }
        ConfigFiles.collection("Mixed", Basic::class.java) { Basic() }
        val entries = ConfigFiles.entries()
        assertEquals(1, entries.size)
        assertTrue(entries.single().reload != null, "the registered entry (with its own reload) is the one listed")
        assertNull(ConfigFiles.entries().firstOrNull { it.label == "Mixed/other.json" })
    }

    @Test
    fun `a recursive folder also lists the folders inside it, the ones right inside first, and skips hidden folders and other files`() {
        File(folder, "Menus/guis/kits").mkdirs()
        File(folder, "Menus/guis/.hidden").mkdirs()
        File(folder, "Menus/guis/b.json").writeText("{}")
        File(folder, "Menus/guis/a.json").writeText("{}")
        File(folder, "Menus/guis/kits/k.json").writeText("{}")
        File(folder, "Menus/guis/kits/notes.txt").writeText("x")
        File(folder, "Menus/guis/.hidden/h.json").writeText("{}")
        ConfigFiles.collection("Menus/guis", Basic::class.java, recursive = true) { Basic() }
        assertEquals(listOf("Menus/guis/a.json", "Menus/guis/b.json", "Menus/guis/kits/k.json"), labels())
        val nested = ConfigFiles.entries().single { it.fileName == "k.json" }
        assertEquals("Menus/guis/kits", nested.folder)
        assertEquals(File(folder, "Menus/guis/kits/k.json"), nested.file)
    }

    @Test
    fun `a folder that is not recursive does not list what is in the folders inside it`() {
        File(folder, "Flat/sub").mkdirs()
        File(folder, "Flat/a.json").writeText("{}")
        File(folder, "Flat/sub/b.json").writeText("{}")
        ConfigFiles.collection("Flat", Basic::class.java) { Basic() }
        assertEquals(listOf("Flat/a.json"), labels())
    }

    @Test
    fun `create writes a new file with the defaults of the class or a copy of another one, and the folder lists it`() {
        ConfigFiles.collection("Content/items", Basic::class.java) { Basic(on = false) }
        val made = ConfigFiles.create("Content/items", "fresh.json")
        assertEquals("Content/items/fresh.json", made.label)
        assertTrue(File(folder, "Content/items/fresh.json").readText().contains("\"on\": false"), "it starts as the defaults")
        File(folder, "Content/items/base.json").writeText("{ \"on\": true }")
        ConfigFiles.create("Content/items", "twin.json", from = "base.json")
        assertEquals("{ \"on\": true }", File(folder, "Content/items/twin.json").readText(), "a copy is the text of the other file")
        assertEquals(listOf("Content/items/base.json", "Content/items/fresh.json", "Content/items/twin.json"), labels())
    }

    @Test
    fun `create refuses a name that is taken, a name that is not plain, a missing source and a folder that is not content`() {
        ConfigFiles.collection("Content/items", Basic::class.java) { Basic() }
        ConfigFiles.register("Other", "thing.json", Basic::class.java) { Basic() }
        File(folder, "Content/items").mkdirs()
        File(folder, "Content/items/taken.json").writeText("{}")
        fun refused(folderName: String, file: String, from: String? = null): String =
            assertThrows(ConfigFiles.CreateException::class.java) { ConfigFiles.create(folderName, file, from) }.code
        assertEquals("exists", refused("Content/items", "taken.json"))
        for (bad in listOf("", ".json", "../x.json", "a/b.json", "a\\b.json", "x.txt", "bad name.json", "x".repeat(60) + ".json", "x.json.json")) {
            assertEquals("bad_params", refused("Content/items", bad), "'$bad'")
        }
        assertEquals("bad_params", refused("Content/items", "ok.json", from = "missing.json"))
        assertEquals("bad_params", refused("Content/items", "ok.json", from = "../taken.json"))
        assertEquals("bad_params", refused("Other", "new.json"), "the folder of a registered file is not a content folder")
        assertEquals("bad_params", refused("Content", "new.json"))
        assertEquals("bad_params", refused("Content/items/sub", "new.json"), "a folder inside a folder that is not recursive")
        assertEquals("bad_params", refused("Content/items/../../x", "new.json"))
        assertFalse(File(folder, "x").exists())
        assertEquals(listOf("Content/items/taken.json", "Other/thing.json"), labels().sorted())
    }

    @Test
    fun `create works inside a folder of a recursive collection, but only with plain folder names`() {
        ConfigFiles.collection("Menus/guis", Basic::class.java, recursive = true) { Basic() }
        val made = ConfigFiles.create("Menus/guis/kits", "extra.json")
        assertEquals("Menus/guis/kits/extra.json", made.label)
        assertTrue(File(folder, "Menus/guis/kits/extra.json").isFile)
        assertEquals(listOf("Menus/guis/kits/extra.json"), labels())
        assertThrows(ConfigFiles.CreateException::class.java) { ConfigFiles.create("Menus/guis/..", "x.json") }
        assertThrows(ConfigFiles.CreateException::class.java) { ConfigFiles.create("Menus/guis/a b", "x.json") }
    }

    @Test
    fun `create starts from the template of the folder when it has one, and a copy still wins over it`() {
        ConfigFiles.collection("Shops", Basic::class.java, recursive = true, template = { "{ \"made\": \"from the template\" }\n" }) { Basic() }
        ConfigFiles.create("Shops", "new.json")
        assertEquals("{ \"made\": \"from the template\" }\n", File(folder, "Shops/new.json").readText())
        File(folder, "Shops/base.json").writeText("{ \"base\": true }")
        ConfigFiles.create("Shops", "copy.json", from = "base.json")
        assertEquals("{ \"base\": true }", File(folder, "Shops/copy.json").readText())
        val deep = ConfigFiles.create("Shops/cat/a", "deep.json")
        assertEquals("Shops/cat/a/deep.json", deep.label)
        assertEquals("{ \"made\": \"from the template\" }\n", File(folder, "Shops/cat/a/deep.json").readText())
        assertEquals(listOf("Shops/base.json", "Shops/copy.json", "Shops/new.json", "Shops/cat/a/deep.json"), labels())
    }
}
