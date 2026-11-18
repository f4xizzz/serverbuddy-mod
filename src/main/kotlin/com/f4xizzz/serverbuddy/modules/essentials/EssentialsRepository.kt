package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.database.DatabaseManager
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

/** Um lugar do mundo (casa, warp, spawn, cela, onde o jogador estava antes de teleportar). */
class SavedLocation(val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
    /** Teleporta [player] para cá. false se a dimensão não existe mais. */
    fun teleport(player: ServerPlayer): Boolean {
        val level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(world))) ?: return false
        player.teleportTo(level, x, y, z, yaw, pitch)
        return true
    }

    fun distanceSquared(player: ServerPlayer): Double {
        if (player.level().dimension().location().toString() != world) return Double.MAX_VALUE
        val dx = player.x - x
        val dy = player.y - y
        val dz = player.z - z
        return dx * dx + dy * dy + dz * dz
    }

    companion object {
        fun of(player: ServerPlayer) = SavedLocation(player.level().dimension().location().toString(), player.x, player.y, player.z, player.yRot, player.xRot)

        fun existsIn(server: MinecraftServer, world: String): Boolean =
            server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(world))) != null
    }
}

class PlayerRecord(
    val uuid: UUID,
    var name: String,
    var firstJoin: Long,
    var lastSeen: Long,
    var nick: String?,
    var mutedUntil: Long,
    var muteReason: String?
)

class MailEntry(val id: String, val toUuid: UUID, val fromName: String, val message: String, val sentAt: Long)

class NoteEntry(val id: String, val author: String, val text: String, val createdAt: Long)

class JailRecord(val uuid: UUID, val jail: String, val until: Long, val reason: String?, val previousMode: String?)

/** As tabelas `sb_*` do módulo de essentials no banco do ServerBuddy. */
object EssentialsRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Essentials")

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Failed to $what!", e)
        null
    }

    private fun exec(conn: Connection, sql: String, vararg args: Any?): Int = conn.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, value ->
            when (value) {
                null -> st.setObject(i + 1, null)
                is Int -> st.setInt(i + 1, value)
                is Long -> st.setLong(i + 1, value)
                is Double -> st.setDouble(i + 1, value)
                is Float -> st.setFloat(i + 1, value)
                else -> st.setString(i + 1, value.toString())
            }
        }
        st.executeUpdate()
    }

    private fun <T> query(conn: Connection, sql: String, vararg args: Any?, read: (ResultSet) -> T): List<T> = conn.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, value -> if (value is Long) st.setLong(i + 1, value) else st.setString(i + 1, value.toString()) }
        st.executeQuery().use { rs ->
            val out = ArrayList<T>()
            while (rs.next()) out.add(read(rs))
            out
        }
    }

    fun createTables() {
        val loc = "world VARCHAR(80) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL, pitch FLOAT NOT NULL"
        run("create essentials tables") { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE IF NOT EXISTS sb_homes (player_uuid VARCHAR(36) NOT NULL, name VARCHAR(32) NOT NULL, $loc, PRIMARY KEY (player_uuid, name))")
                st.execute("CREATE TABLE IF NOT EXISTS sb_warps (name VARCHAR(32) NOT NULL PRIMARY KEY, $loc)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_jails (name VARCHAR(32) NOT NULL PRIMARY KEY, $loc)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_players (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, first_join BIGINT NOT NULL DEFAULT 0, last_seen BIGINT NOT NULL DEFAULT 0, nick VARCHAR(64), muted_until BIGINT NOT NULL DEFAULT 0, mute_reason VARCHAR(200))")
                st.execute("CREATE TABLE IF NOT EXISTS sb_ignores (player_uuid VARCHAR(36) NOT NULL, ignored_uuid VARCHAR(36) NOT NULL, PRIMARY KEY (player_uuid, ignored_uuid))")
                st.execute("CREATE TABLE IF NOT EXISTS sb_mail (id VARCHAR(36) NOT NULL PRIMARY KEY, to_uuid VARCHAR(36) NOT NULL, from_name VARCHAR(32) NOT NULL, message TEXT NOT NULL, sent_at BIGINT NOT NULL)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_notes (id VARCHAR(36) NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, author VARCHAR(32) NOT NULL, text TEXT NOT NULL, created_at BIGINT NOT NULL)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_jailed (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, jail VARCHAR(32) NOT NULL, until_ms BIGINT NOT NULL, reason VARCHAR(200), previous_mode VARCHAR(16))")
                st.execute("CREATE TABLE IF NOT EXISTS sb_jail_info (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, by_name VARCHAR(32) NOT NULL, at BIGINT NOT NULL)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_logoff (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, $loc)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_activetime (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, seconds BIGINT NOT NULL DEFAULT 0)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_powertools (player_uuid VARCHAR(36) NOT NULL, item VARCHAR(100) NOT NULL, action VARCHAR(24) NOT NULL, command VARCHAR(300) NOT NULL, PRIMARY KEY (player_uuid, item, action))")
                st.execute("CREATE TABLE IF NOT EXISTS sb_notify (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, enabled INT NOT NULL DEFAULT 1, sound VARCHAR(100), pitch DOUBLE, volume DOUBLE, afk_only INT, on_chat INT)")
            }
        }
    }

    private fun location(rs: ResultSet) = SavedLocation(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"))

    private fun saveLocation(table: String, keys: List<Pair<String, String>>, loc: SavedLocation) {
        run("save a location in $table") { conn ->
            val where = keys.joinToString(" AND ") { "${it.first} = ?" }
            val keyArgs = keys.map { it.second }.toTypedArray()
            val updated = exec(conn, "UPDATE $table SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? WHERE $where", loc.world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch, *keyArgs)
            if (updated == 0) {
                val columns = keys.joinToString(", ") { it.first }
                exec(conn, "INSERT INTO $table ($columns, world, x, y, z, yaw, pitch) VALUES (${keys.joinToString(", ") { "?" }}, ?, ?, ?, ?, ?, ?)", *keyArgs, loc.world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
            }
        }
    }

    // ---------------------------------------------------------------- casas

    fun homes(uuid: UUID): Map<String, SavedLocation> = run("read homes for $uuid") { conn ->
        query(conn, "SELECT * FROM sb_homes WHERE player_uuid = ? ORDER BY name", uuid.toString()) { it.getString("name") to location(it) }.toMap(LinkedHashMap())
    } ?: emptyMap()

    fun saveHome(uuid: UUID, name: String, loc: SavedLocation) = saveLocation("sb_homes", listOf("player_uuid" to uuid.toString(), "name" to name), loc)

    fun deleteHome(uuid: UUID, name: String): Boolean = run("delete home $name") { conn -> exec(conn, "DELETE FROM sb_homes WHERE player_uuid = ? AND name = ?", uuid.toString(), name) > 0 } ?: false

    // ---------------------------------------------------------------- warps e celas (mesma forma)

    fun warps(): Map<String, SavedLocation> = places("sb_warps")

    fun saveWarp(name: String, loc: SavedLocation) = saveLocation("sb_warps", listOf("name" to name), loc)

    fun deleteWarp(name: String): Boolean = run("delete warp $name") { conn -> exec(conn, "DELETE FROM sb_warps WHERE name = ?", name) > 0 } ?: false

    fun jails(): Map<String, SavedLocation> = places("sb_jails")

    fun saveJail(name: String, loc: SavedLocation) = saveLocation("sb_jails", listOf("name" to name), loc)

    fun deleteJail(name: String): Boolean = run("delete jail $name") { conn -> exec(conn, "DELETE FROM sb_jails WHERE name = ?", name) > 0 } ?: false

    private fun places(table: String): Map<String, SavedLocation> = run("read $table") { conn ->
        query(conn, "SELECT * FROM $table ORDER BY name") { it.getString("name") to location(it) }.toMap(LinkedHashMap())
    } ?: emptyMap()

    // ---------------------------------------------------------------- jogadores

    private fun record(rs: ResultSet) = PlayerRecord(
        UUID.fromString(rs.getString("player_uuid")), rs.getString("name"), rs.getLong("first_join"), rs.getLong("last_seen"),
        rs.getString("nick"), rs.getLong("muted_until"), rs.getString("mute_reason")
    )

    fun player(uuid: UUID): PlayerRecord? = run("read player $uuid") { conn -> query(conn, "SELECT * FROM sb_players WHERE player_uuid = ?", uuid.toString()) { record(it) }.firstOrNull() }

    /** When every known player was last seen, in ONE query (the dashboard's player list sorts by it). */
    fun allLastSeen(): Map<UUID, Long> = run("read when every player was last seen") { conn ->
        query(conn, "SELECT player_uuid, last_seen FROM sb_players") { rs -> runCatching { UUID.fromString(rs.getString(1)) to rs.getLong(2) }.getOrNull() }.filterNotNull().toMap()
    } ?: emptyMap()

    fun playerByName(name: String): PlayerRecord? = run("look up player $name") { conn -> query(conn, "SELECT * FROM sb_players WHERE LOWER(name) = ?", name.lowercase()) { record(it) }.firstOrNull() }

    fun playerByNick(nick: String): PlayerRecord? = run("look up nickname $nick") { conn -> query(conn, "SELECT * FROM sb_players WHERE LOWER(nick) = ?", nick.lowercase()) { record(it) }.firstOrNull() }

    fun savePlayer(rec: PlayerRecord) {
        run("save player ${rec.uuid}") { conn ->
            val updated = exec(conn, "UPDATE sb_players SET name = ?, first_join = ?, last_seen = ?, nick = ?, muted_until = ?, mute_reason = ? WHERE player_uuid = ?", rec.name, rec.firstJoin, rec.lastSeen, rec.nick, rec.mutedUntil, rec.muteReason, rec.uuid.toString())
            if (updated == 0) exec(conn, "INSERT INTO sb_players (player_uuid, name, first_join, last_seen, nick, muted_until, mute_reason) VALUES (?, ?, ?, ?, ?, ?, ?)", rec.uuid.toString(), rec.name, rec.firstJoin, rec.lastSeen, rec.nick, rec.mutedUntil, rec.muteReason)
        }
    }

    // ---------------------------------------------------------------- ignorar, correio e anotações

    fun ignored(uuid: UUID): Set<UUID> = run("read ignored players for $uuid") { conn -> query(conn, "SELECT ignored_uuid FROM sb_ignores WHERE player_uuid = ?", uuid.toString()) { UUID.fromString(it.getString(1)) }.toSet() } ?: emptySet()

    fun setIgnored(uuid: UUID, other: UUID, value: Boolean) {
        run("ignorar $other") { conn ->
            exec(conn, "DELETE FROM sb_ignores WHERE player_uuid = ? AND ignored_uuid = ?", uuid.toString(), other.toString())
            if (value) exec(conn, "INSERT INTO sb_ignores (player_uuid, ignored_uuid) VALUES (?, ?)", uuid.toString(), other.toString())
        }
    }

    fun addMail(to: UUID, from: String, message: String) {
        run("guardar a mensagem") { conn -> exec(conn, "INSERT INTO sb_mail (id, to_uuid, from_name, message, sent_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID().toString(), to.toString(), from, message, System.currentTimeMillis()) }
    }

    fun mail(to: UUID): List<MailEntry> = run("read mail for $to") { conn ->
        query(conn, "SELECT * FROM sb_mail WHERE to_uuid = ? ORDER BY sent_at", to.toString()) { MailEntry(it.getString("id"), to, it.getString("from_name"), it.getString("message"), it.getLong("sent_at")) }
    } ?: emptyList()

    fun clearMail(to: UUID) {
        run("clear mail for $to") { conn -> exec(conn, "DELETE FROM sb_mail WHERE to_uuid = ?", to.toString()) }
    }

    fun addNote(player: UUID, author: String, text: String) {
        run("save the note") { conn -> exec(conn, "INSERT INTO sb_notes (id, player_uuid, author, text, created_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID().toString(), player.toString(), author, text, System.currentTimeMillis()) }
    }

    fun notes(player: UUID): List<NoteEntry> = run("read notes for $player") { conn ->
        query(conn, "SELECT * FROM sb_notes WHERE player_uuid = ? ORDER BY created_at", player.toString()) { NoteEntry(it.getString("id"), it.getString("author"), it.getString("text"), it.getLong("created_at")) }
    } ?: emptyList()

    fun clearNotes(player: UUID) {
        run("delete notes for $player") { conn -> exec(conn, "DELETE FROM sb_notes WHERE player_uuid = ?", player.toString()) }
    }

    // ---------------------------------------------------------------- presos

    fun jailed(): List<JailRecord> = run("read jailed players") { conn ->
        query(conn, "SELECT * FROM sb_jailed") { JailRecord(UUID.fromString(it.getString("player_uuid")), it.getString("jail"), it.getLong("until_ms"), it.getString("reason"), it.getString("previous_mode")) }
    } ?: emptyList()

    fun saveJailed(record: JailRecord) {
        run("prender ${record.uuid}") { conn ->
            exec(conn, "DELETE FROM sb_jailed WHERE player_uuid = ?", record.uuid.toString())
            exec(conn, "INSERT INTO sb_jailed (player_uuid, jail, until_ms, reason, previous_mode) VALUES (?, ?, ?, ?, ?)", record.uuid.toString(), record.jail, record.until, record.reason, record.previousMode)
        }
    }

    fun deleteJailed(uuid: UUID) {
        run("soltar $uuid") { conn -> exec(conn, "DELETE FROM sb_jailed WHERE player_uuid = ?", uuid.toString()) }
    }

    /** Quem prendeu e quando (para o /checkjail). */
    fun saveJailInfo(uuid: UUID, by: String, at: Long) {
        run("guardar quem prendeu $uuid") { conn ->
            exec(conn, "DELETE FROM sb_jail_info WHERE player_uuid = ?", uuid.toString())
            exec(conn, "INSERT INTO sb_jail_info (player_uuid, by_name, at) VALUES (?, ?, ?)", uuid.toString(), by, at)
        }
    }

    fun jailInfo(uuid: UUID): Pair<String, Long>? = run("read who jailed $uuid") { conn ->
        query(conn, "SELECT by_name, at FROM sb_jail_info WHERE player_uuid = ?", uuid.toString()) { it.getString("by_name") to it.getLong("at") }.firstOrNull()
    }

    // ---------------------------------------------------------------- onde o jogador estava ao sair (/tpoffline)

    fun saveLogoff(uuid: UUID, loc: SavedLocation) = saveLocation("sb_logoff", listOf("player_uuid" to uuid.toString()), loc)

    fun logoff(uuid: UUID): SavedLocation? = run("read logout location for $uuid") { conn ->
        query(conn, "SELECT * FROM sb_logoff WHERE player_uuid = ?", uuid.toString()) { location(it) }.firstOrNull()
    }

    // ---------------------------------------------------------------- tempo ativo (/activetime)

    fun activeSeconds(uuid: UUID): Long = run("read active time for $uuid") { conn ->
        query(conn, "SELECT seconds FROM sb_activetime WHERE player_uuid = ?", uuid.toString()) { it.getLong(1) }.firstOrNull()
    } ?: 0L

    fun setActiveSeconds(uuid: UUID, seconds: Long) {
        run("save active time for $uuid") { conn ->
            val updated = exec(conn, "UPDATE sb_activetime SET seconds = ? WHERE player_uuid = ?", seconds, uuid.toString())
            if (updated == 0) exec(conn, "INSERT INTO sb_activetime (player_uuid, seconds) VALUES (?, ?)", uuid.toString(), seconds)
        }
    }

    /** Os que mais ficaram ativos: (nome, segundos). */
    fun topActive(limit: Int): List<Pair<String, Long>> = run("read active time rankings") { conn ->
        query(conn, "SELECT a.player_uuid, a.seconds, p.name FROM sb_activetime a LEFT JOIN sb_players p ON p.player_uuid = a.player_uuid ORDER BY a.seconds DESC") {
            (it.getString("name") ?: it.getString("player_uuid")) to it.getLong("seconds")
        }.take(limit)
    } ?: emptyList()

    // ---------------------------------------------------------------- powertools

    /** (item, ação, comando) de [uuid]. */
    fun powertools(uuid: UUID): List<Triple<String, String, String>> = run("read powertools for $uuid") { conn ->
        query(conn, "SELECT item, action, command FROM sb_powertools WHERE player_uuid = ?", uuid.toString()) { Triple(it.getString("item"), it.getString("action"), it.getString("command")) }
    } ?: emptyList()

    fun savePowertool(uuid: UUID, item: String, action: String, command: String) {
        run("save powertool") { conn ->
            exec(conn, "DELETE FROM sb_powertools WHERE player_uuid = ? AND item = ? AND action = ?", uuid.toString(), item, action)
            exec(conn, "INSERT INTO sb_powertools (player_uuid, item, action, command) VALUES (?, ?, ?, ?)", uuid.toString(), item, action, command)
        }
    }

    /** Apaga o powertool de [item]; sem [action], apaga todas as ações dele. */
    fun deletePowertool(uuid: UUID, item: String, action: String?) {
        run("delete powertool") { conn ->
            if (action == null) exec(conn, "DELETE FROM sb_powertools WHERE player_uuid = ? AND item = ?", uuid.toString(), item)
            else exec(conn, "DELETE FROM sb_powertools WHERE player_uuid = ? AND item = ? AND action = ?", uuid.toString(), item, action)
        }
    }

    // ---------------------------------------------------------------- notificações (menções)

    class NotifyRow(val enabled: Boolean, val sound: String?, val pitch: Double?, val volume: Double?, val afkOnly: Boolean?, val onChat: Boolean?)

    fun notify(uuid: UUID): NotifyRow? = run("read notifications for $uuid") { conn ->
        query(conn, "SELECT * FROM sb_notify WHERE player_uuid = ?", uuid.toString()) {
            fun flag(column: String): Boolean? = it.getInt(column).let { v -> if (it.wasNull()) null else v != 0 }
            fun real(column: String): Double? = it.getDouble(column).let { v -> if (it.wasNull()) null else v }
            NotifyRow(it.getInt("enabled") != 0, it.getString("sound"), real("pitch"), real("volume"), flag("afk_only"), flag("on_chat"))
        }.firstOrNull()
    }

    fun saveNotify(uuid: UUID, row: NotifyRow) {
        run("save notifications for $uuid") { conn ->
            exec(conn, "DELETE FROM sb_notify WHERE player_uuid = ?", uuid.toString())
            exec(
                conn, "INSERT INTO sb_notify (player_uuid, enabled, sound, pitch, volume, afk_only, on_chat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                uuid.toString(), if (row.enabled) 1 else 0, row.sound, row.pitch, row.volume, row.afkOnly?.let { if (it) 1 else 0 }, row.onChat?.let { if (it) 1 else 0 }
            )
        }
    }

    fun deleteNotify(uuid: UUID) {
        run("delete notifications for $uuid") { conn -> exec(conn, "DELETE FROM sb_notify WHERE player_uuid = ?", uuid.toString()) }
    }
}
