package com.f4xizzz.serverbuddy.modules.daycare

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** A Pokémon left in the daycare: the whole Pokémon as JSON, when it was left and at which level. */
class Deposit(val slot: Int, val depositTime: Long, val depositLevel: Int, val pokemonJson: String)

/** Table `sb_daycare`: one row per occupied slot of a player. */
object DaycareRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/Daycare")

    fun createTable() {
        run("create sb_daycare") { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_daycare (player_uuid VARCHAR(36) NOT NULL, slot INTEGER NOT NULL, deposit_time BIGINT NOT NULL, " +
                        "deposit_level INTEGER NOT NULL, pokemon TEXT NOT NULL, PRIMARY KEY (player_uuid, slot))"
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

    fun deposits(uuid: UUID): List<Deposit> = run("read the daycare of $uuid") { conn ->
        conn.prepareStatement("SELECT slot, deposit_time, deposit_level, pokemon FROM sb_daycare WHERE player_uuid = ? ORDER BY slot").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs ->
                val list = ArrayList<Deposit>()
                while (rs.next()) list.add(Deposit(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getString(4)))
                list
            }
        }
    } ?: emptyList()

    /** True when the row was written. */
    fun add(uuid: UUID, deposit: Deposit): Boolean = run("save a daycare deposit") { conn ->
        conn.prepareStatement("INSERT INTO sb_daycare (player_uuid, slot, deposit_time, deposit_level, pokemon) VALUES (?, ?, ?, ?, ?)").use { st ->
            st.setString(1, uuid.toString())
            st.setInt(2, deposit.slot)
            st.setLong(3, deposit.depositTime)
            st.setInt(4, deposit.depositLevel)
            st.setString(5, deposit.pokemonJson)
            st.executeUpdate()
        }
        true
    } ?: false

    /** True when a row was removed (so two quick clicks cannot take the same Pokémon twice). */
    fun remove(uuid: UUID, slot: Int): Boolean = run("remove a daycare deposit") { conn ->
        conn.prepareStatement("DELETE FROM sb_daycare WHERE player_uuid = ? AND slot = ?").use { st ->
            st.setString(1, uuid.toString())
            st.setInt(2, slot)
            st.executeUpdate() > 0
        }
    } ?: false
}
