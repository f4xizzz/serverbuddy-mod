package com.f4xizzz.serverbuddy.modules.astral

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** One Pokémon a pool shows right now. The price is not stored: it is read from the config when the offer is shown or bought. */
class Offer(val pool: String, val slot: Int, val speciesId: String, val level: Int, val shiny: Boolean, val perfectIvs: Int)

/** Tables `sb_astral_tokens`, `sb_astral_offers`, `sb_astral_state` and `sb_astral_buys` in the ServerBuddy database. */
object AstralRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/Astral")

    fun createTables() {
        run("create the Astral Trader tables") { conn ->
            conn.createStatement().use {
                it.execute("CREATE TABLE IF NOT EXISTS sb_astral_tokens (player_uuid VARCHAR(36) NOT NULL, currency VARCHAR(16) NOT NULL, amount BIGINT NOT NULL, PRIMARY KEY (player_uuid, currency))")
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_astral_offers (pool VARCHAR(24) NOT NULL, slot INTEGER NOT NULL, species VARCHAR(80) NOT NULL, " +
                        "level INTEGER NOT NULL, shiny INTEGER NOT NULL, perfect_ivs INTEGER NOT NULL, PRIMARY KEY (pool, slot))"
                )
                it.execute("CREATE TABLE IF NOT EXISTS sb_astral_state (pool VARCHAR(24) NOT NULL PRIMARY KEY, rotated_at BIGINT NOT NULL)")
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_astral_buys (player_uuid VARCHAR(36) NOT NULL, pool VARCHAR(24) NOT NULL, slot INTEGER NOT NULL, " +
                        "rotated_at BIGINT NOT NULL, bought INTEGER NOT NULL, PRIMARY KEY (player_uuid, pool, slot, rotated_at))"
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

    // ---------------------------------------------------------------- tokens

    fun balance(player: UUID, currency: String): Long = run("read a token balance") { conn ->
        conn.prepareStatement("SELECT amount FROM sb_astral_tokens WHERE player_uuid = ? AND currency = ?").use { st ->
            st.setString(1, player.toString())
            st.setString(2, currency)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    } ?: 0L

    /** Adds [delta] (it can be negative; the balance never goes below zero). Returns the new balance. */
    fun add(player: UUID, currency: String, delta: Long): Long = set(player, currency, (balance(player, currency) + delta).coerceAtLeast(0L))

    fun set(player: UUID, currency: String, amount: Long): Long {
        val value = amount.coerceAtLeast(0L)
        run("save a token balance") { conn ->
            val updated = conn.prepareStatement("UPDATE sb_astral_tokens SET amount = ? WHERE player_uuid = ? AND currency = ?").use { st ->
                st.setLong(1, value)
                st.setString(2, player.toString())
                st.setString(3, currency)
                st.executeUpdate()
            }
            if (updated == 0) {
                conn.prepareStatement("INSERT INTO sb_astral_tokens (player_uuid, currency, amount) VALUES (?, ?, ?)").use { st ->
                    st.setString(1, player.toString())
                    st.setString(2, currency)
                    st.setLong(3, value)
                    st.executeUpdate()
                }
            }
        }
        return value
    }

    /** Takes [amount] only if the player has that much (all or nothing). */
    fun trySpend(player: UUID, currency: String, amount: Long): Boolean = run("spend tokens") { conn ->
        conn.prepareStatement("UPDATE sb_astral_tokens SET amount = amount - ? WHERE player_uuid = ? AND currency = ? AND amount >= ?").use { st ->
            st.setLong(1, amount)
            st.setString(2, player.toString())
            st.setString(3, currency)
            st.setLong(4, amount)
            st.executeUpdate() == 1
        }
    } ?: false

    // ---------------------------------------------------------------- offers

    fun offers(pool: String): List<Offer> = run("read the offers") { conn ->
        conn.prepareStatement("SELECT slot, species, level, shiny, perfect_ivs FROM sb_astral_offers WHERE pool = ? ORDER BY slot").use { st ->
            st.setString(1, pool)
            st.executeQuery().use { rs ->
                val list = ArrayList<Offer>()
                while (rs.next()) list.add(Offer(pool, rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4) == 1, rs.getInt(5)))
                list
            }
        }
    } ?: emptyList()

    fun rotatedAt(pool: String): Long? = run("read the rotation time") { conn ->
        conn.prepareStatement("SELECT rotated_at FROM sb_astral_state WHERE pool = ?").use { st ->
            st.setString(1, pool)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }

    /** Swaps the pool's offers for [offers] and starts a new rotation at [now] (earlier purchases stop counting). */
    fun replaceOffers(pool: String, offers: List<Offer>, now: Long) {
        run("save the new offers") { conn ->
            conn.prepareStatement("DELETE FROM sb_astral_offers WHERE pool = ?").use { st ->
                st.setString(1, pool)
                st.executeUpdate()
            }
            for (offer in offers) {
                conn.prepareStatement("INSERT INTO sb_astral_offers (pool, slot, species, level, shiny, perfect_ivs) VALUES (?, ?, ?, ?, ?, ?)").use { st ->
                    st.setString(1, pool)
                    st.setInt(2, offer.slot)
                    st.setString(3, offer.speciesId)
                    st.setInt(4, offer.level)
                    st.setInt(5, if (offer.shiny) 1 else 0)
                    st.setInt(6, offer.perfectIvs)
                    st.executeUpdate()
                }
            }
            val updated = conn.prepareStatement("UPDATE sb_astral_state SET rotated_at = ? WHERE pool = ?").use { st ->
                st.setLong(1, now)
                st.setString(2, pool)
                st.executeUpdate()
            }
            if (updated == 0) {
                conn.prepareStatement("INSERT INTO sb_astral_state (pool, rotated_at) VALUES (?, ?)").use { st ->
                    st.setString(1, pool)
                    st.setLong(2, now)
                    st.executeUpdate()
                }
            }
            conn.prepareStatement("DELETE FROM sb_astral_buys WHERE pool = ? AND rotated_at <> ?").use { st ->
                st.setString(1, pool)
                st.setLong(2, now)
                st.executeUpdate()
            }
        }
    }

    // ---------------------------------------------------------------- purchases

    /** How many times [player] traded for this offer in the rotation that started at [rotatedAt]. */
    fun boughtBy(player: UUID, pool: String, slot: Int, rotatedAt: Long): Int = run("read purchases") { conn ->
        conn.prepareStatement("SELECT bought FROM sb_astral_buys WHERE player_uuid = ? AND pool = ? AND slot = ? AND rotated_at = ?").use { st ->
            st.setString(1, player.toString())
            st.setString(2, pool)
            st.setInt(3, slot)
            st.setLong(4, rotatedAt)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    } ?: 0

    /** How many copies of this offer were traded by everybody in the rotation that started at [rotatedAt]. */
    fun boughtTotal(pool: String, slot: Int, rotatedAt: Long): Int = run("count purchases") { conn ->
        conn.prepareStatement("SELECT COALESCE(SUM(bought), 0) FROM sb_astral_buys WHERE pool = ? AND slot = ? AND rotated_at = ?").use { st ->
            st.setString(1, pool)
            st.setInt(2, slot)
            st.setLong(3, rotatedAt)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    } ?: 0

    fun recordPurchase(player: UUID, pool: String, slot: Int, rotatedAt: Long) {
        run("record a purchase") { conn ->
            val updated = conn.prepareStatement("UPDATE sb_astral_buys SET bought = bought + 1 WHERE player_uuid = ? AND pool = ? AND slot = ? AND rotated_at = ?").use { st ->
                st.setString(1, player.toString())
                st.setString(2, pool)
                st.setInt(3, slot)
                st.setLong(4, rotatedAt)
                st.executeUpdate()
            }
            if (updated == 0) {
                conn.prepareStatement("INSERT INTO sb_astral_buys (player_uuid, pool, slot, rotated_at, bought) VALUES (?, ?, ?, ?, 1)").use { st ->
                    st.setString(1, player.toString())
                    st.setString(2, pool)
                    st.setInt(3, slot)
                    st.setLong(4, rotatedAt)
                    st.executeUpdate()
                }
            }
        }
    }
}
