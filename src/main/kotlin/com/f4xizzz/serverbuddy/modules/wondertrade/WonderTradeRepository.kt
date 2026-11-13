package com.f4xizzz.serverbuddy.modules.wondertrade

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** A Pokémon waiting in the pool: who offered it, what it is, and the whole Pokémon as JSON. */
class PoolEntry(val id: String, val ownerUuid: UUID, val ownerName: String, val level: Int, val shiny: Boolean, val legendary: Boolean, val species: String, val json: String)

/** One finished trade, for `/wondertrade history`. */
class TradeRecord(val time: Long, val gave: String, val got: String, val level: Int)

/** Tables `sb_wt_pool`, `sb_wt_history` and `sb_wt_cooldown` in the ServerBuddy database. */
object WonderTradeRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/WonderTrade")

    fun createTables() {
        run("create the Wonder Trade tables") { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_wt_pool (entry_id VARCHAR(36) NOT NULL PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL, owner_name VARCHAR(32) NOT NULL, " +
                        "level INTEGER NOT NULL, shiny INTEGER NOT NULL, legendary INTEGER NOT NULL, species VARCHAR(64) NOT NULL, added BIGINT NOT NULL, pokemon TEXT NOT NULL)"
                )
                it.execute("CREATE TABLE IF NOT EXISTS sb_wt_history (player_uuid VARCHAR(36) NOT NULL, traded BIGINT NOT NULL, gave VARCHAR(80) NOT NULL, got VARCHAR(80) NOT NULL, got_level INTEGER NOT NULL)")
                it.execute("CREATE TABLE IF NOT EXISTS sb_wt_cooldown (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, last_trade BIGINT NOT NULL)")
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        log.error("Error trying to $what", e)
        null
    }

    fun poolSize(): Int = run("count the pool") { conn ->
        conn.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM sb_wt_pool").use { rs -> if (rs.next()) rs.getInt(1) else 0 } }
    } ?: 0

    /** Empties the pool. Returns how many Pokémon were dropped. */
    fun clear(): Int = run("clear the pool") { conn -> conn.createStatement().use { it.executeUpdate("DELETE FROM sb_wt_pool") } } ?: 0

    /** Every entry that someone else offered (the caller filters by level and rarity). */
    fun candidates(excludeOwner: UUID): List<PoolEntry> = run("read the pool") { conn ->
        conn.prepareStatement("SELECT entry_id, owner_uuid, owner_name, level, shiny, legendary, species, pokemon FROM sb_wt_pool WHERE owner_uuid <> ?").use { st ->
            st.setString(1, excludeOwner.toString())
            st.executeQuery().use { rs ->
                val list = ArrayList<PoolEntry>()
                while (rs.next()) {
                    val owner = runCatching { UUID.fromString(rs.getString(2)) }.getOrNull() ?: continue
                    list.add(PoolEntry(rs.getString(1), owner, rs.getString(3), rs.getInt(4), rs.getInt(5) == 1, rs.getInt(6) == 1, rs.getString(7), rs.getString(8)))
                }
                list
            }
        }
    } ?: emptyList()

    fun add(entry: PoolEntry): Boolean = run("add to the pool") { conn ->
        conn.prepareStatement("INSERT INTO sb_wt_pool (entry_id, owner_uuid, owner_name, level, shiny, legendary, species, added, pokemon) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)").use { st ->
            st.setString(1, entry.id)
            st.setString(2, entry.ownerUuid.toString())
            st.setString(3, entry.ownerName.take(32))
            st.setInt(4, entry.level)
            st.setInt(5, if (entry.shiny) 1 else 0)
            st.setInt(6, if (entry.legendary) 1 else 0)
            st.setString(7, entry.species.take(64))
            st.setLong(8, System.currentTimeMillis())
            st.setString(9, entry.json)
            st.executeUpdate()
        }
        true
    } ?: false

    /** True when this call removed the entry (two simultaneous trades cannot take the same Pokémon). */
    fun take(id: String): Boolean = run("take from the pool") { conn ->
        conn.prepareStatement("DELETE FROM sb_wt_pool WHERE entry_id = ?").use { st ->
            st.setString(1, id)
            st.executeUpdate() > 0
        }
    } ?: false

    /** Drops the oldest entries beyond [max]. */
    fun trim(max: Int) {
        run("trim the pool") { conn ->
            // count with this same connection: the SQLite pool has a single one, asking for a second would wait for ever
            val size = conn.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM sb_wt_pool").use { rs -> if (rs.next()) rs.getInt(1) else 0 } }
            val excess = size - max
            if (excess <= 0) return@run
            val ids = conn.prepareStatement("SELECT entry_id FROM sb_wt_pool ORDER BY added ASC").use { st ->
                st.maxRows = excess
                st.executeQuery().use { rs ->
                    val list = ArrayList<String>()
                    while (rs.next()) list.add(rs.getString(1))
                    list
                }
            }
            for (id in ids) conn.prepareStatement("DELETE FROM sb_wt_pool WHERE entry_id = ?").use { st -> st.setString(1, id); st.executeUpdate() }
        }
    }

    fun lastTrade(uuid: UUID): Long = run("read the cooldown") { conn ->
        conn.prepareStatement("SELECT last_trade FROM sb_wt_cooldown WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    } ?: 0L

    fun setLastTrade(uuid: UUID, time: Long) {
        run("save the cooldown") { conn ->
            val updated = conn.prepareStatement("UPDATE sb_wt_cooldown SET last_trade = ? WHERE player_uuid = ?").use { st ->
                st.setLong(1, time)
                st.setString(2, uuid.toString())
                st.executeUpdate()
            }
            if (updated == 0) conn.prepareStatement("INSERT INTO sb_wt_cooldown (player_uuid, last_trade) VALUES (?, ?)").use { st ->
                st.setString(1, uuid.toString())
                st.setLong(2, time)
                st.executeUpdate()
            }
        }
    }

    fun addHistory(uuid: UUID, record: TradeRecord, keep: Int) {
        run("save the trade history") { conn ->
            conn.prepareStatement("INSERT INTO sb_wt_history (player_uuid, traded, gave, got, got_level) VALUES (?, ?, ?, ?, ?)").use { st ->
                st.setString(1, uuid.toString())
                st.setLong(2, record.time)
                st.setString(3, record.gave.take(80))
                st.setString(4, record.got.take(80))
                st.setInt(5, record.level)
                st.executeUpdate()
            }
            // keep only the newest [keep] rows of this player
            val times = conn.prepareStatement("SELECT traded FROM sb_wt_history WHERE player_uuid = ? ORDER BY traded DESC").use { st ->
                st.setString(1, uuid.toString())
                st.executeQuery().use { rs ->
                    val list = ArrayList<Long>()
                    while (rs.next()) list.add(rs.getLong(1))
                    list
                }
            }
            if (times.size > keep && keep > 0) {
                conn.prepareStatement("DELETE FROM sb_wt_history WHERE player_uuid = ? AND traded < ?").use { st ->
                    st.setString(1, uuid.toString())
                    st.setLong(2, times[keep - 1])
                    st.executeUpdate()
                }
            }
        }
    }

    fun history(uuid: UUID, limit: Int): List<TradeRecord> = run("read the trade history") { conn ->
        conn.prepareStatement("SELECT traded, gave, got, got_level FROM sb_wt_history WHERE player_uuid = ? ORDER BY traded DESC").use { st ->
            st.setString(1, uuid.toString())
            st.maxRows = limit
            st.executeQuery().use { rs ->
                val list = ArrayList<TradeRecord>()
                while (rs.next()) list.add(TradeRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)))
                list
            }
        }
    } ?: emptyList()
}
