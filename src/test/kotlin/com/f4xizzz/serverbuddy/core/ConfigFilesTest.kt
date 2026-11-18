package com.f4xizzz.serverbuddy.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
}
