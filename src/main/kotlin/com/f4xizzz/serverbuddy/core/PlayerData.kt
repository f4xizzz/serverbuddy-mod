package com.f4xizzz.serverbuddy.core

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID

/**
 * READ-ONLY access to the vanilla player files (`<world>/playerdata/<uuid>.dat`) of players who may be offline. This is the one place that
 * opens those files, so every feature that needs an offline player's data (the dashboard's offline inventory and ender chest today, the
 * offline `/invsee` later) reads it the same safe way:
 *
 *  - the file name comes ONLY from a [UUID] (parsed, never text from outside), resolved inside the folder; a link or anything that is not
 *    a plain file in that folder is refused (no path traversal, no following links out of the world);
 *  - a file bigger than [MAX_FILE_BYTES], or an NBT that wants more than [MAX_NBT_BYTES] of memory once read, is refused (a corrupted or
 *    hostile file cannot exhaust the server);
 *  - it never writes: vanilla saves with a temporary file and an atomic replace, so a read always sees a complete file;
 *  - an unreadable file answers [Unreadable] with a code the dashboard can show; it never throws anything else.
 *
 * Files of players who are ONLINE are not the truth (the server holds newer data in memory): callers read the live player instead.
 * [read] returns a copy, so a caller can change it without touching the cache.
 */
class PlayerDataFiles(private val dir: Path, private val now: () -> Long = System::currentTimeMillis) {
    /** One player with a data file; [lastModified] is when vanilla last saved it (for an offline player: about when they left). */
    class Entry(val uuid: UUID, val lastModified: Long, val size: Long)

    /** [code] is one of `player_data_missing`, `player_data_too_large`, `player_data_unreadable`. */
    class Unreadable(val code: String, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private val root: Path = dir.toAbsolutePath().normalize()

    @Volatile
    private var listed: Pair<Long, List<Entry>>? = null

    private class Cached(val modified: Long, val size: Long, val tag: CompoundTag)

    private val cache = object : LinkedHashMap<UUID, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Cached>?): Boolean = size > CACHE_SIZE
    }

    /** The data file of [uuid], or null when it is not a plain file inside the folder. */
    private fun fileOf(uuid: UUID): Path? {
        val file = root.resolve("$uuid.dat").normalize()
        if (file.parent != root) return null
        return if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) file else null
    }

    /** Every player that has a data file (the folder listing is kept for [LIST_TTL_MS], so a big server is not scanned for each request). */
    fun entries(): List<Entry> {
        val current = now()
        listed?.let { (at, list) -> if (current - at < LIST_TTL_MS) return list }
        val found = ArrayList<Entry>()
        if (Files.isDirectory(root)) {
            Files.newDirectoryStream(root, "*.dat").use { stream ->
                for (path in stream) {
                    val name = path.fileName.toString().removeSuffix(".dat")
                    val uuid = parse(name) ?: continue
                    if (uuid.toString() != name) continue
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                    found += Entry(uuid, Files.getLastModifiedTime(path).toMillis(), Files.size(path))
                }
            }
        }
        listed = current to found
        return found
    }

    /** Forgets the folder listing (after something saved a player file and the next list must be fresh). */
    fun refresh() {
        listed = null
    }

    fun has(uuid: UUID): Boolean = fileOf(uuid) != null

    /** When vanilla last saved [uuid]'s file, or null if there is none. */
    fun lastModified(uuid: UUID): Long? = fileOf(uuid)?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrNull() }

    /** Reads and decodes the file (a copy). Throws [Unreadable] when there is none or it cannot be trusted or decoded. */
    @Synchronized
    fun read(uuid: UUID): CompoundTag {
        val file = fileOf(uuid) ?: throw Unreadable("player_data_missing", "There is no saved data for $uuid.")
        val size: Long
        val modified: Long
        try {
            size = Files.size(file)
            modified = Files.getLastModifiedTime(file).toMillis()
        } catch (e: Exception) {
            throw Unreadable("player_data_unreadable", "Could not read the data of $uuid.", e)
        }
        if (size > MAX_FILE_BYTES) throw Unreadable("player_data_too_large", "The data file of $uuid is too big ($size bytes).")
        cache[uuid]?.let { if (it.modified == modified && it.size == size) return it.tag.copy() }
        val tag = try {
            NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES))
        } catch (e: Exception) {
            throw Unreadable("player_data_unreadable", "The data file of $uuid is corrupted or unreadable.", e)
        }
        cache[uuid] = Cached(modified, size, tag)
        return tag.copy()
    }

    companion object {
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
        const val MAX_NBT_BYTES = 64L * 1024 * 1024
        const val LIST_TTL_MS = 20_000L
        private const val CACHE_SIZE = 32

        private fun parse(text: String): UUID? = try {
            UUID.fromString(text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** The [PlayerDataFiles] of the world the server is running (one per world folder). */
object PlayerData {
    @Volatile
    private var current: PlayerDataFiles? = null

    @Volatile
    private var currentDir: Path? = null

    @JvmStatic
    @Synchronized
    fun of(server: MinecraftServer): PlayerDataFiles {
        val dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
        val existing = current
        if (existing != null && currentDir == dir) return existing
        return PlayerDataFiles(dir).also {
            current = it
            currentDir = dir
        }
    }
}
