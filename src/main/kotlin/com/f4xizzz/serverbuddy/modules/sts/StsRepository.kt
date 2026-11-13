package com.f4xizzz.serverbuddy.modules.sts

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** What the STS remembers about a player. */
class StsPlayer(val uuid: UUID, var lastSale: Long = 0, var sold: Int = 0, var earned: Double = 0.0)

/** Table `sb_sts` in the ServerBuddy database: the last sale (for the cooldown) and totals per player. */
object StsRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/STS")

    fun createTable() {
        run("create sb_sts") { conn ->
            conn.createStatement().use {
                it.execute("CREATE TABLE IF NOT EXISTS sb_sts (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, last_sale BIGINT NOT NULL, sold INTEGER NOT NULL, earned DOUBLE PRECISION NOT NULL)")
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        log.error("Error trying to $what", e)
        null
    }

    fun load(uuid: UUID): StsPlayer = run("read the STS data of $uuid") { conn ->
        conn.prepareStatement("SELECT last_sale, sold, earned FROM sb_sts WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs -> if (rs.next()) StsPlayer(uuid, rs.getLong(1), rs.getInt(2), rs.getDouble(3)) else StsPlayer(uuid) }
        }
    } ?: StsPlayer(uuid)

    fun save(player: StsPlayer) {
        run("save the STS data of ${player.uuid}") { conn ->
            val updated = conn.prepareStatement("UPDATE sb_sts SET last_sale = ?, sold = ?, earned = ? WHERE player_uuid = ?").use { st ->
                st.setLong(1, player.lastSale)
                st.setInt(2, player.sold)
                st.setDouble(3, player.earned)
                st.setString(4, player.uuid.toString())
                st.executeUpdate()
            }
            if (updated == 0) {
                conn.prepareStatement("INSERT INTO sb_sts (player_uuid, last_sale, sold, earned) VALUES (?, ?, ?, ?)").use { st ->
                    st.setString(1, player.uuid.toString())
                    st.setLong(2, player.lastSale)
                    st.setInt(3, player.sold)
                    st.setDouble(4, player.earned)
                    st.executeUpdate()
                }
            }
        }
    }
}
