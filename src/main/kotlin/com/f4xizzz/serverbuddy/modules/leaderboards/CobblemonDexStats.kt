package com.f4xizzz.serverbuddy.modules.leaderboards

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.api.pokedex.CaughtPercent
import com.cobblemon.mod.common.api.pokedex.PokedexManager
import com.cobblemon.mod.common.api.pokedex.SeenCount
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.adapter.NbtBackedPlayerData
import com.cobblemon.mod.common.api.storage.player.factory.CachedPlayerDataStoreFactory
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Pokédex numbers of a player (Cobblemon). Only load this class when Cobblemon is installed. */
object CobblemonDexStats {
    class Dex(val caught: Int, val seen: Int, val percent: Double)

    /** A Pokédex file bigger than this is not read (a full Pokédex is about 100 KB). */
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024

    /** The numbers of an online player (Cobblemon's live data). */
    fun read(player: ServerPlayer): Dex? = try {
        calculate(Cobblemon.playerDataManager.getPokedexData(player))
    } catch (e: Throwable) {
        null
    }

    /**
     * The numbers of a player who is NOT online, read straight from the Pokédex file with Cobblemon's own codec. Cobblemon's loader
     * (`getPokedexData(uuid)`) is left alone on purpose: it keeps what it loads in memory (to be saved again later and handed to that
     * player's next login), and it creates or "repairs" a file that is missing or unreadable. A look from the dashboard must change nothing.
     *
     * Returns null when the player has no Pokédex file; throws when the file cannot be read (nothing is repaired).
     */
    fun readOffline(uuid: UUID): Dex? {
        val factory = Cobblemon.playerDataManager.factories[PlayerInstancedDataStoreTypes.POKEDEX] as? CachedPlayerDataStoreFactory<*>
            ?: throw IllegalStateException("Cobblemon has no Pokédex storage")
        val backend = factory.backend as? NbtBackedPlayerData<*>
            ?: throw IllegalStateException("the Pokédex of offline players can only be read when Cobblemon keeps it in its NBT files")
        val file = backend.filePath(uuid).toPath()
        if (!java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return null
        if (java.nio.file.Files.size(file) > MAX_FILE_BYTES) throw IllegalStateException("the Pokédex file of $uuid is too big")
        // Cobblemon writes this file as plain (not gzipped) NBT; the size was checked above
        val tag = NbtIo.read(file)
        val decoded = backend.codec.decode(NbtOps.INSTANCE, tag).getOrThrow { IllegalStateException("the Pokédex file of $uuid is unreadable: $it") }.first
        return calculate(decoded as PokedexManager)
    }

    private fun calculate(pokedex: PokedexManager): Dex {
        val caught = CaughtCount.calculate(pokedex)
        val seen = SeenCount.calculate(pokedex)
        val raw = CaughtPercent.calculate(pokedex)
        // the calculator already answers in percent (0.195 = 0.195%) when it says so; otherwise it is a 0..1 fraction
        val percent = if (raw == null) 0.0 else if (CaughtPercent.outputIsPercentage) raw.toDouble() else raw * 100.0
        return Dex(caught?.toInt() ?: 0, seen?.toInt() ?: 0, percent)
    }
}
