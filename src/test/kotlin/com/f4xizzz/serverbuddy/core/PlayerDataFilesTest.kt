package com.f4xizzz.serverbuddy.core

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * The safe, read-only reader of `playerdata/<uuid>.dat`. The offline inventory of the dashboard (and the offline /invsee later) stands on
 * it, so what it refuses matters as much as what it reads.
 */
class PlayerDataFilesTest {
    private lateinit var dir: Path
    private var clock = 1_000L
    private lateinit var files: PlayerDataFiles

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("sb-playerdata")
        clock = 1_000L
        files = PlayerDataFiles(dir) { clock }
    }

    @AfterEach
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun write(uuid: UUID, build: CompoundTag.() -> Unit = { putString("marker", "hello") }): Path {
        val path = dir.resolve("$uuid.dat")
        NbtIo.writeCompressed(CompoundTag().apply(build), path)
        return path
    }

    @Test
    fun `lists only the files named after a UUID`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        write(a)
        write(b)
        Files.writeString(dir.resolve("notes.dat"), "x")
        Files.writeString(dir.resolve("$a.dat_old"), "x")
        Files.writeString(dir.resolve("${a.toString().uppercase()}.dat"), "x") // not the canonical spelling: ignored
        Files.createDirectory(dir.resolve("${UUID.randomUUID()}.dat")) // a folder is not a player
        assertEquals(setOf(a, b), files.entries().map { it.uuid }.toSet())
        assertTrue(files.has(a))
        assertFalse(files.has(UUID.randomUUID()))
        assertTrue(files.entries().all { it.lastModified > 0 && it.size > 0 })
    }

    @Test
    fun `a missing folder is an empty list and a missing file says so`() {
        val empty = PlayerDataFiles(dir.resolve("does-not-exist"))
        assertEquals(0, empty.entries().size)
        val error = assertThrows(PlayerDataFiles.Unreadable::class.java) { empty.read(UUID.randomUUID()) }
        assertEquals("player_data_missing", error.code)
        assertNull(empty.lastModified(UUID.randomUUID()))
    }

    @Test
    fun `reads the file and hands out a copy`() {
        val id = UUID.randomUUID()
        write(id) { putString("marker", "hello"); putInt("XpLevel", 7) }
        val first = files.read(id)
        assertEquals("hello", first.getString("marker"))
        assertEquals(7, first.getInt("XpLevel"))
        first.putString("marker", "changed by the caller")
        val second = files.read(id)
        assertEquals("hello", second.getString("marker"), "the cache is not touched by whoever changes the copy")
        assertNotSame(first, second)
    }

    @Test
    fun `a file that changed is read again`() {
        val id = UUID.randomUUID()
        val path = write(id) { putString("marker", "old") }
        assertEquals("old", files.read(id).getString("marker"))
        write(id) { putString("marker", "newer and longer value") }
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000))
        assertEquals("newer and longer value", files.read(id).getString("marker"))
    }

    @Test
    fun `the folder listing is kept for a while and refresh makes it fresh`() {
        val a = UUID.randomUUID()
        write(a)
        assertEquals(1, files.entries().size)
        write(UUID.randomUUID())
        clock += PlayerDataFiles.LIST_TTL_MS - 1
        assertEquals(1, files.entries().size, "still the old listing")
        clock += 2
        assertEquals(2, files.entries().size, "the listing expired")
        write(UUID.randomUUID())
        files.refresh()
        assertEquals(3, files.entries().size)
    }

    @Test
    fun `a corrupted file is refused with a clear code`() {
        val id = UUID.randomUUID()
        Files.write(dir.resolve("$id.dat"), ByteArray(300) { (it * 31).toByte() })
        val error = assertThrows(PlayerDataFiles.Unreadable::class.java) { files.read(id) }
        assertEquals("player_data_unreadable", error.code)
    }

    @Test
    fun `a huge file is refused before it is opened`() {
        val id = UUID.randomUUID()
        Files.newByteChannel(dir.resolve("$id.dat"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE).use {
            it.position(PlayerDataFiles.MAX_FILE_BYTES + 1)
            it.write(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
        }
        val error = assertThrows(PlayerDataFiles.Unreadable::class.java) { files.read(id) }
        assertEquals("player_data_too_large", error.code)
    }

    @Test
    fun `a small file that expands into too much NBT is refused`() {
        val id = UUID.randomUUID()
        // 80 MB of zeros compress to a few dozen KB: the file is small, the memory it asks for is not
        write(id) { putByteArray("bomb", ByteArray(80 * 1024 * 1024)) }
        assertTrue(Files.size(dir.resolve("$id.dat")) < PlayerDataFiles.MAX_FILE_BYTES)
        val error = assertThrows(PlayerDataFiles.Unreadable::class.java) { files.read(id) }
        assertEquals("player_data_unreadable", error.code)
    }

    @Test
    fun `only a plain file inside the folder counts (not a folder, not a link out of it)`() {
        val id = UUID.randomUUID()
        Files.createDirectory(dir.resolve("$id.dat"))
        assertFalse(files.has(id))
        assertThrows(PlayerDataFiles.Unreadable::class.java) { files.read(id) }

        val outside = Files.createTempFile("sb-outside", ".dat")
        try {
            NbtIo.writeCompressed(CompoundTag().apply { putString("secret", "outside the world") }, outside)
            val linked = UUID.randomUUID()
            val link = try {
                Files.createSymbolicLink(dir.resolve("$linked.dat"), outside)
            } catch (e: Exception) {
                null
            }
            assumeTrue(link != null, "this system does not allow creating links")
            assertFalse(files.has(linked), "a link is not followed")
            assertTrue(files.entries().none { it.uuid == linked })
            assertEquals("player_data_missing", assertThrows(PlayerDataFiles.Unreadable::class.java) { files.read(linked) }.code)
        } finally {
            Files.deleteIfExists(outside)
        }
    }
}
