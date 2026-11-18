package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.core.PlayerData
import com.f4xizzz.serverbuddy.core.PlayerDataFiles
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import java.util.Locale
import java.util.UUID

/**
 * The players section of the dashboard (Plus): everyone the server has seen, ONLINE OR OFFLINE, with the profile, the inventory, the ender
 * chest, the Pokémon party and PC and the Pokédex. An online player is read live. An offline one comes from the files on disk: the vanilla
 * `playerdata/<uuid>.dat` through [PlayerDataFiles] (read-only) and Cobblemon's own storage by UUID, with a short cache so browsing does
 * not read the same files again and again. Nothing here changes a player.
 */
object PlayersView {
    /** A player the dashboard named: online, or offline but known (a saved file or the user cache). */
    class Who(val uuid: UUID, val name: String, val online: net.minecraft.server.level.ServerPlayer?)

    fun register() {
        DashboardProviders.register("players.list", ::list)
        DashboardProviders.register("players.get", ::profile)
        DashboardProviders.register("players.inventory") { ctx -> container(ctx, ctx.stringOrNull("container") == "ender") }
        DashboardProviders.register("players.enderchest") { ctx -> container(ctx, true) }
        DashboardProviders.register("players.dex") { ctx -> cobblemon { cached(ctx, who(ctx), "dex") { who -> CobblemonView.dex(ctx.server, who.uuid) } } }
        DashboardProviders.register("players.party") { ctx -> cobblemon { cached(ctx, who(ctx), "party") { who -> CobblemonView.party(ctx.server, who.uuid) } } }
        DashboardProviders.register("players.pc") { ctx -> cobblemon { cached(ctx, who(ctx), "pc") { who -> CobblemonView.pc(ctx.server, who.uuid) } } }
        // what was read before a player played is old news once they leave; and vanilla writes their file right after this event (same server
        // thread, before any request is answered), so the folder listing must be read again or a first-time player would be missing from the list
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            OfflineCache.forget(handler.player.uuid)
            files(server).refresh()
        }
    }

    // ------------------------------------------------------------------ who

    /** The player named by `player` (a name or a UUID). */
    fun who(ctx: Ctx): Who {
        val raw = ctx.stringOrNull("player")?.trim().orEmpty()
        if (raw.isEmpty()) throw DashboardError("bad_params", "missing parameter: player")
        val server = ctx.server
        server.playerList.getPlayerByName(raw)?.let { return Who(it.uuid, it.gameProfile.name, it) }
        val asUuid = runCatching { UUID.fromString(raw) }.getOrNull()
        if (asUuid != null) {
            server.playerList.getPlayer(asUuid)?.let { return Who(it.uuid, it.gameProfile.name, it) }
            if (!files(server).has(asUuid)) throw DashboardError("player_not_found", raw)
            return Who(asUuid, nameOf(server, asUuid), null)
        }
        val profile = server.profileCache?.get(raw)?.orElse(null)
        val uuid = profile?.id ?: KnownNames.of(server).entries.firstOrNull { it.value.equals(raw, ignoreCase = true) }?.key
        if (uuid == null || !(files(server).has(uuid))) throw DashboardError("player_not_found", raw)
        return Who(uuid, profile?.name ?: nameOf(server, uuid), null)
    }

    private fun files(server: MinecraftServer): PlayerDataFiles = PlayerData.of(server)

    /** The name of an offline player: the user cache, then Essentials, then the start of the UUID. */
    /** The name of any player (online or not) by UUID, for the lists that show who did something. */
    fun nameFor(server: MinecraftServer, uuid: UUID): String = nameOf(server, uuid)

    private fun nameOf(server: MinecraftServer, uuid: UUID, known: Map<UUID, String> = KnownNames.of(server)): String =
        knownName(server, uuid, known) ?: essentials(uuid)?.name?.takeIf { it.isNotBlank() } ?: uuid.toString().take(8)

    /** A name from the user cache file or, for somebody who just left and is not written there yet, from the server's cache in memory. */
    private fun knownName(server: MinecraftServer, uuid: UUID, known: Map<UUID, String>): String? =
        known[uuid] ?: server.profileCache?.get(uuid)?.orElse(null)?.name?.takeIf { it.isNotBlank() }

    private fun essentials(uuid: UUID) = if (Modules.isActive("essentials")) runCatching { EssentialsRepository.player(uuid) }.getOrNull() else null

    /**
     * When an offline player was last seen: what Essentials wrote when they left (it does not change if somebody later edits their file), or
     * else when the server last saved their player file. The list passes [bulk] (every Essentials value in one query).
     */
    private fun lastSeen(server: MinecraftServer, uuid: UUID, bulk: Map<UUID, Long>? = null): Long =
        (bulk?.get(uuid) ?: if (bulk == null) essentials(uuid)?.lastSeen else null)?.takeIf { it > 0 } ?: files(server).lastModified(uuid) ?: 0L

    /** The saved NBT of an offline player, or a dashboard error the person can read. */
    fun readPlayer(server: MinecraftServer, uuid: UUID): CompoundTag = try {
        files(server).read(uuid)
    } catch (e: PlayerDataFiles.Unreadable) {
        throw DashboardError(e.code, e.message)
    }

    // ------------------------------------------------------------------ list

    private class Row(val uuid: UUID, val name: String?, val online: Boolean, val modified: Long)

    /**
     * `players.list` `{ query?, status?: "all" | "online" | "offline", offset?, limit? }` → `{ total, counts: { online, offline },
     * players: [{ name, uuid, online, lastSeen?, dexPercent? }] }`. Online players first, then the offline ones, the most recent first.
     * `query` matches a name or the start of a UUID.
     */
    private fun list(ctx: Ctx): JsonObject {
        val server = ctx.server
        val query = ctx.stringOrNull("query")?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val status = ctx.stringOrNull("status")?.lowercase(Locale.ROOT) ?: "all"
        val offset = ctx.int("offset", 0).coerceAtLeast(0)
        val limit = ctx.int("limit", 50).coerceIn(1, 200)
        val names = KnownNames.of(server)
        val online = server.playerList.players
        val onlineIds = online.mapTo(HashSet()) { it.uuid }
        val offlineEntries = files(server).entries().filter { it.uuid !in onlineIds }

        fun matches(name: String?, uuid: UUID): Boolean =
            query.isEmpty() || name?.lowercase(Locale.ROOT)?.contains(query) == true || (query.length >= 3 && uuid.toString().startsWith(query))

        val bulk = if (Modules.isActive("essentials")) runCatching { EssentialsRepository.allLastSeen() }.getOrDefault(emptyMap()) else emptyMap()
        val rows = ArrayList<Row>()
        if (status != "offline") online.filter { matches(it.gameProfile.name, it.uuid) }.sortedBy { it.gameProfile.name.lowercase(Locale.ROOT) }
            .forEach { rows += Row(it.uuid, it.gameProfile.name, true, 0) }
        if (status != "online") offlineEntries.map { it to knownName(server, it.uuid, names) }.filter { (entry, name) -> matches(name, entry.uuid) }
            .map { (entry, name) -> Row(entry.uuid, name, false, bulk[entry.uuid]?.takeIf { it > 0 } ?: entry.lastModified) }
            .sortedByDescending { it.modified }.forEach { rows += it }

        return JsonObject().apply {
            addProperty("total", rows.size)
            add("counts", JsonObject().apply {
                addProperty("online", online.size)
                addProperty("offline", offlineEntries.size)
            })
            add("players", JsonArray().also { array ->
                for (row in rows.drop(offset).take(limit)) {
                    array.add(JsonObject().apply {
                        val record = if (row.online) null else essentials(row.uuid)
                        addProperty("name", row.name ?: record?.name?.takeIf { it.isNotBlank() } ?: row.uuid.toString().take(8))
                        addProperty("uuid", row.uuid.toString())
                        addProperty("online", row.online)
                        if (!row.online) addProperty("lastSeen", row.modified)
                        if (row.online && Integrations.cobblemon()) {
                            server.playerList.getPlayer(row.uuid)?.let { player -> CobblemonView.dexPercent(player)?.let { addProperty("dexPercent", it) } }
                        }
                    })
                }
            })
        }
    }

    // ------------------------------------------------------------------ profile

    /**
     * `players.get` `{ player }` → name, uuid, online, op, banned and, from the live player or from the saved file: health, food, xpLevel,
     * gamemode, dimension, x, y, z (+ ping online; lastSeen, firstJoin and the spawn point offline).
     */
    private fun profile(ctx: Ctx): JsonObject {
        val server = ctx.server
        val who = who(ctx)
        val gameProfile = GameProfile(who.uuid, who.name)
        return JsonObject().apply {
            addProperty("name", who.name)
            addProperty("uuid", who.uuid.toString())
            addProperty("online", who.online != null)
            addProperty("op", server.playerList.isOp(gameProfile))
            addProperty("banned", server.playerList.bans.isBanned(gameProfile))
            val player = who.online
            if (player != null) {
                addProperty("health", player.health)
                addProperty("food", player.foodData.foodLevel)
                addProperty("xpLevel", player.experienceLevel)
                addProperty("gamemode", player.gameMode.gameModeForPlayer.getName())
                addProperty("dimension", player.level().dimension().location().toString())
                addProperty("x", round(player.x))
                addProperty("y", round(player.y))
                addProperty("z", round(player.z))
                addProperty("ping", player.connection.latency())
                essentials(who.uuid)?.firstJoin?.takeIf { it > 0 }?.let { addProperty("firstJoin", it) }
                return@apply
            }
            val data = readPlayer(server, who.uuid)
            addProperty("lastSeen", lastSeen(server, who.uuid))
            essentials(who.uuid)?.firstJoin?.takeIf { it > 0 }?.let { addProperty("firstJoin", it) }
            offlineFields(data, this)
        }
    }

    /** What the saved NBT of a player says about them (pure: tested without a server). */
    internal fun offlineFields(data: CompoundTag, into: JsonObject) {
        if (data.contains("Health")) into.addProperty("health", data.getFloat("Health"))
        if (data.contains("foodLevel")) into.addProperty("food", data.getInt("foodLevel"))
        if (data.contains("XpLevel")) into.addProperty("xpLevel", data.getInt("XpLevel"))
        if (data.contains("playerGameType")) into.addProperty("gamemode", GameType.byId(data.getInt("playerGameType")).getName())
        data.getString("Dimension").takeIf { it.isNotBlank() }?.let { into.addProperty("dimension", it) }
        val pos = data.getList("Pos", 6)
        if (pos.size >= 3) {
            into.addProperty("x", round(pos.getDouble(0)))
            into.addProperty("y", round(pos.getDouble(1)))
            into.addProperty("z", round(pos.getDouble(2)))
        }
        if (data.contains("SpawnX") && data.contains("SpawnZ")) {
            into.add("spawn", JsonObject().apply {
                data.getString("SpawnDimension").takeIf { it.isNotBlank() }?.let { addProperty("dimension", it) }
                addProperty("x", data.getInt("SpawnX"))
                addProperty("y", data.getInt("SpawnY"))
                addProperty("z", data.getInt("SpawnZ"))
            })
        }
    }

    private fun round(value: Double): Double = Math.round(value * 10) / 10.0

    // ------------------------------------------------------------------ inventory and ender chest

    /**
     * `players.inventory` `{ player, container?: "ender" }` and `players.enderchest` `{ player }` → `{ container, online, items: [{ slot, id,
     * count, name, customName?, enchants?, lore?, damage?, maxDamage?, unknown? }], lastSeen? }`. Slots are the ones of the player file (see [ItemViews]).
     */
    private fun container(ctx: Ctx, ender: Boolean): JsonObject {
        val server = ctx.server
        val who = who(ctx)
        val items = JsonArray()
        val player = who.online
        return JsonObject().apply {
            addProperty("container", if (ender) "ender" else "inventory")
            addProperty("online", player != null)
            if (player != null) {
                val source = if (ender) player.enderChestInventory else player.inventory
                for (slot in 0 until source.containerSize) {
                    val stack = source.getItem(slot)
                    if (!stack.isEmpty) items.add(ItemViews.of(if (ender) slot else ItemViews.fileSlot(slot), stack))
                }
            } else {
                val data = readPlayer(server, who.uuid)
                items.addAll(ItemViews.ofList(data.getList(if (ender) "EnderItems" else "Inventory", 10), server.registryAccess()))
                addProperty("lastSeen", lastSeen(server, who.uuid))
            }
            add("items", items)
        }
    }

    // ------------------------------------------------------------------ Cobblemon (party, PC, Pokédex)

    private inline fun <T> cobblemon(block: () -> T): T {
        if (!Integrations.cobblemon()) throw DashboardError("needs_cobblemon")
        return block()
    }

    /**
     * An online player is read live. An offline one needs a saved player file (so a UUID nobody ever used creates no Cobblemon data) and is
     * cached for [OfflineCache.TTL_MS]; the answer says `offline: true` and when the player left.
     */
    private fun cached(ctx: Ctx, who: Who, kind: String, build: (Who) -> JsonObject): JsonObject {
        if (who.online != null) return build(who).also { it.addProperty("online", true) }
        val server = ctx.server
        if (!files(server).has(who.uuid)) throw DashboardError("player_not_found", who.name)
        val seen = lastSeen(server, who.uuid)
        // Cobblemon writes its files in the background for a few seconds after a player leaves: an answer read in that time is not kept
        val settled = System.currentTimeMillis() - seen >= OfflineCache.SETTLE_MS
        return OfflineCache.get("$kind:${who.uuid}", keep = settled) {
            val result = try {
                build(who)
            } catch (e: DashboardError) {
                throw e
            } catch (e: Exception) {
                throw DashboardError("cobblemon_data_unreadable", "Could not read the Cobblemon data of ${who.name}: ${e.message}")
            } finally {
                // Cobblemon would keep the party and PC it loaded for this offline player in memory until the player's next visit
                if (kind != "dex") CobblemonView.release(who.uuid)
            }
            result.addProperty("online", false)
            result.addProperty("lastSeen", seen)
            result
        }
    }

    /** Short-lived answers about offline players (reading Pokémon files is the slow part). */
    internal object OfflineCache {
        const val TTL_MS = 30_000L

        /** How long after a player leaves the files Cobblemon writes in the background may still be half done. */
        const val SETTLE_MS = 15_000L
        private const val MAX = 64

        private class Hit(val at: Long, val value: JsonObject)

        private val hits = object : LinkedHashMap<String, Hit>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Hit>?): Boolean = size > MAX
        }

        /** The answer for [key] from the last [TTL_MS], or a new one from [build] (kept only when [keep]). */
        @Synchronized
        fun get(key: String, keep: Boolean = true, build: () -> JsonObject): JsonObject {
            val now = System.currentTimeMillis()
            hits[key]?.let { if (now - it.at < TTL_MS) return it.value }
            return build().also { if (keep) hits[key] = Hit(now, it) }
        }

        @Synchronized
        fun forget(uuid: UUID) {
            hits.keys.removeIf { it.endsWith(":$uuid") }
        }

        @Synchronized
        fun clear() = hits.clear()
    }

    /** `usercache.json` (the server's list of names it has seen), reloaded only when the file changes. */
    internal object KnownNames {
        @Volatile
        private var loaded: Triple<Long, Long, Map<UUID, String>>? = null

        fun of(server: MinecraftServer): Map<UUID, String> = fromFile(server)

        private fun fromFile(server: MinecraftServer): Map<UUID, String> {
            val file = server.serverDirectory.resolve("usercache.json").toFile()
            if (!file.isFile) return emptyMap()
            val stamp = file.lastModified() to file.length()
            loaded?.let { if (it.first == stamp.first && it.second == stamp.second) return it.third }
            val map = try {
                parse(file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                emptyMap()
            }
            loaded = Triple(stamp.first, stamp.second, map)
            return map
        }

        internal fun parse(json: String): Map<UUID, String> {
            val map = LinkedHashMap<UUID, String>()
            for (element in JsonParser.parseString(json).asJsonArray) {
                val entry = element.asJsonObject
                val uuid = entry.get("uuid")?.asString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: continue
                val name = entry.get("name")?.asString?.takeIf { it.isNotBlank() } ?: continue
                map[uuid] = name
            }
            return map
        }
    }
}
