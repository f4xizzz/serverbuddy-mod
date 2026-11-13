package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/**
 * Table `sb_lb_stats`: the last known value of a statistic per player (the Pokédex is only readable for players who are online,
 * so it is remembered here and ranked from this table).
 */
object LeaderboardRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/Leaderboards")

    fun createTable() {
        run("create sb_lb_stats") { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_lb_stats (player_uuid VARCHAR(36) NOT NULL, stat VARCHAR(32) NOT NULL, " +
                        "player_name VARCHAR(32) NOT NULL, stat_value DOUBLE PRECISION NOT NULL, updated BIGINT NOT NULL, PRIMARY KEY (player_uuid, stat))"
                )
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        log.error("Error trying to $what", e)
        null
    }

    fun save(uuid: UUID, name: String, stat: String, value: Double) {
        run("save a leaderboard value") { conn ->
            val now = System.currentTimeMillis()
            val updated = conn.prepareStatement("UPDATE sb_lb_stats SET player_name = ?, stat_value = ?, updated = ? WHERE player_uuid = ? AND stat = ?").use { st ->
                st.setString(1, name)
                st.setDouble(2, value)
                st.setLong(3, now)
                st.setString(4, uuid.toString())
                st.setString(5, stat)
                st.executeUpdate()
            }
            if (updated == 0) {
                conn.prepareStatement("INSERT INTO sb_lb_stats (player_uuid, stat, player_name, stat_value, updated) VALUES (?, ?, ?, ?, ?)").use { st ->
                    st.setString(1, uuid.toString())
                    st.setString(2, stat)
                    st.setString(3, name)
                    st.setDouble(4, value)
                    st.setLong(5, now)
                    st.executeUpdate()
                }
            }
        }
    }

    /** Every remembered value of [stat], as (uuid, name, value). */
    fun all(stat: String, limit: Int): List<Triple<UUID, String, Double>> = run("read the leaderboard values") { conn ->
        conn.prepareStatement("SELECT player_uuid, player_name, stat_value FROM sb_lb_stats WHERE stat = ? ORDER BY stat_value DESC").use { st ->
            st.setString(1, stat)
            st.maxRows = limit
            st.executeQuery().use { rs ->
                val list = ArrayList<Triple<UUID, String, Double>>()
                while (rs.next()) {
                    val uuid = runCatching { UUID.fromString(rs.getString(1)) }.getOrNull() ?: continue
                    list.add(Triple(uuid, rs.getString(2), rs.getDouble(3)))
                }
                list
            }
        }
    } ?: emptyList()
}
