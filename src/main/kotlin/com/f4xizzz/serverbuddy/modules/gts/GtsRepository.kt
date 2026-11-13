package com.f4xizzz.serverbuddy.modules.gts

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

enum class ListingKind { POKEMON, ITEM }

enum class ListingStatus { ACTIVE, SOLD, EXPIRED, CANCELLED, RETURNED }

/** A GTS listing. [payload] is the Pokémon as JSON, or the item as SNBT; [label] a plain name for logs and history. */
class Listing(
    val id: String,
    val sellerUuid: UUID,
    val sellerName: String,
    val kind: ListingKind,
    val price: Double,
    val created: Long,
    val expires: Long,
    val status: ListingStatus,
    val payload: String,
    val label: String,
    val buyerUuid: UUID? = null,
    val buyerName: String? = null,
    val soldAt: Long = 0
)

/** Tables `sb_gts_listings`, `sb_gts_payouts` and `sb_gts_timeouts` in the ServerBuddy database. */
object GtsRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/GTS")

    fun createTables() {
        run("create the GTS tables") { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_gts_listings (listing_id VARCHAR(36) NOT NULL PRIMARY KEY, seller_uuid VARCHAR(36) NOT NULL, seller_name VARCHAR(32) NOT NULL, " +
                        "kind VARCHAR(8) NOT NULL, price DOUBLE PRECISION NOT NULL, created BIGINT NOT NULL, expires BIGINT NOT NULL, status VARCHAR(10) NOT NULL, " +
                        "payload TEXT NOT NULL, label VARCHAR(100) NOT NULL, buyer_uuid VARCHAR(36), buyer_name VARCHAR(32), sold_at BIGINT NOT NULL)"
                )
                it.execute("CREATE TABLE IF NOT EXISTS sb_gts_payouts (payout_id VARCHAR(36) NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, amount DOUBLE PRECISION NOT NULL, created BIGINT NOT NULL)")
                it.execute("CREATE TABLE IF NOT EXISTS sb_gts_timeouts (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, until_time BIGINT NOT NULL)")
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        log.error("Error trying to $what", e)
        null
    }

    private const val COLUMNS = "listing_id, seller_uuid, seller_name, kind, price, created, expires, status, payload, label, buyer_uuid, buyer_name, sold_at"

    private fun read(rs: ResultSet): Listing? {
        val seller = runCatching { UUID.fromString(rs.getString(2)) }.getOrNull() ?: return null
        val buyer = rs.getString(11)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return Listing(
            rs.getString(1), seller, rs.getString(3), runCatching { ListingKind.valueOf(rs.getString(4)) }.getOrDefault(ListingKind.ITEM), rs.getDouble(5), rs.getLong(6), rs.getLong(7),
            runCatching { ListingStatus.valueOf(rs.getString(8)) }.getOrDefault(ListingStatus.EXPIRED), rs.getString(9), rs.getString(10), buyer, rs.getString(12), rs.getLong(13)
        )
    }

    fun add(listing: Listing): Boolean = run("save a listing") { conn ->
        conn.prepareStatement("INSERT INTO sb_gts_listings ($COLUMNS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use { st ->
            st.setString(1, listing.id)
            st.setString(2, listing.sellerUuid.toString())
            st.setString(3, listing.sellerName.take(32))
            st.setString(4, listing.kind.name)
            st.setDouble(5, listing.price)
            st.setLong(6, listing.created)
            st.setLong(7, listing.expires)
            st.setString(8, listing.status.name)
            st.setString(9, listing.payload)
            st.setString(10, listing.label.take(100))
            st.setString(11, listing.buyerUuid?.toString())
            st.setString(12, listing.buyerName)
            st.setLong(13, listing.soldAt)
            st.executeUpdate()
        }
        true
    } ?: false

    fun get(id: String): Listing? = run("read a listing") { conn ->
        conn.prepareStatement("SELECT $COLUMNS FROM sb_gts_listings WHERE listing_id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }
    }

    /** A page of listings. [kind] or [seller] may be null (no filter). Newest first. */
    fun page(status: ListingStatus, kind: ListingKind?, seller: UUID?, offset: Int, limit: Int): List<Listing> = run("read the listings") { conn ->
        val where = StringBuilder("status = ?")
        if (kind != null) where.append(" AND kind = ?")
        if (seller != null) where.append(" AND seller_uuid = ?")
        conn.prepareStatement("SELECT $COLUMNS FROM sb_gts_listings WHERE $where ORDER BY created DESC LIMIT ? OFFSET ?").use { st ->
            var i = 1
            st.setString(i++, status.name)
            if (kind != null) st.setString(i++, kind.name)
            if (seller != null) st.setString(i++, seller.toString())
            st.setInt(i++, limit)
            st.setInt(i, offset)
            st.executeQuery().use { rs ->
                val list = ArrayList<Listing>()
                while (rs.next()) read(rs)?.let { list.add(it) }
                list
            }
        }
    } ?: emptyList()

    fun count(status: ListingStatus, kind: ListingKind?, seller: UUID?): Int = run("count the listings") { conn ->
        val where = StringBuilder("status = ?")
        if (kind != null) where.append(" AND kind = ?")
        if (seller != null) where.append(" AND seller_uuid = ?")
        conn.prepareStatement("SELECT COUNT(*) FROM sb_gts_listings WHERE $where").use { st ->
            var i = 1
            st.setString(i++, status.name)
            if (kind != null) st.setString(i++, kind.name)
            if (seller != null) st.setString(i, seller.toString())
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    } ?: 0

    /** Listings waiting for [seller] to take them back (expired or cancelled). */
    fun returnable(seller: UUID, offset: Int, limit: Int): List<Listing> = run("read the returned listings") { conn ->
        conn.prepareStatement("SELECT $COLUMNS FROM sb_gts_listings WHERE seller_uuid = ? AND (status = 'EXPIRED' OR status = 'CANCELLED') ORDER BY created DESC LIMIT ? OFFSET ?").use { st ->
            st.setString(1, seller.toString())
            st.setInt(2, limit)
            st.setInt(3, offset)
            st.executeQuery().use { rs ->
                val list = ArrayList<Listing>()
                while (rs.next()) read(rs)?.let { list.add(it) }
                list
            }
        }
    } ?: emptyList()

    fun returnableCount(seller: UUID): Int = run("count the returned listings") { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM sb_gts_listings WHERE seller_uuid = ? AND (status = 'EXPIRED' OR status = 'CANCELLED')").use { st ->
            st.setString(1, seller.toString())
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    } ?: 0

    /** Changes the status only if it is still [from]. True when this call changed it (so a listing is never sold or returned twice). */
    fun move(id: String, from: ListingStatus, to: ListingStatus): Boolean = run("change a listing") { conn ->
        conn.prepareStatement("UPDATE sb_gts_listings SET status = ? WHERE listing_id = ? AND status = ?").use { st ->
            st.setString(1, to.name)
            st.setString(2, id)
            st.setString(3, from.name)
            st.executeUpdate() > 0
        }
    } ?: false

    /** ACTIVE → SOLD with the buyer. True when this call won the sale. */
    fun markSold(id: String, buyer: UUID, buyerName: String): Boolean = run("complete a sale") { conn ->
        conn.prepareStatement("UPDATE sb_gts_listings SET status = 'SOLD', buyer_uuid = ?, buyer_name = ?, sold_at = ? WHERE listing_id = ? AND status = 'ACTIVE'").use { st ->
            st.setString(1, buyer.toString())
            st.setString(2, buyerName.take(32))
            st.setLong(3, System.currentTimeMillis())
            st.setString(4, id)
            st.executeUpdate() > 0
        }
    } ?: false

    fun expireDue(now: Long): Int = run("expire listings") { conn ->
        conn.prepareStatement("UPDATE sb_gts_listings SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires < ?").use { st ->
            st.setLong(1, now)
            st.executeUpdate()
        }
    } ?: 0

    /** The newest finished sales where [uuid] was the seller or the buyer. */
    fun history(uuid: UUID, limit: Int): List<Listing> = run("read the GTS history") { conn ->
        conn.prepareStatement("SELECT $COLUMNS FROM sb_gts_listings WHERE status = 'SOLD' AND (seller_uuid = ? OR buyer_uuid = ?) ORDER BY sold_at DESC LIMIT ?").use { st ->
            st.setString(1, uuid.toString())
            st.setString(2, uuid.toString())
            st.setInt(3, limit)
            st.executeQuery().use { rs ->
                val list = ArrayList<Listing>()
                while (rs.next()) read(rs)?.let { list.add(it) }
                list
            }
        }
    } ?: emptyList()

    // ---------------------------------------------------------------- payouts and timeouts

    fun addPayout(uuid: UUID, amount: Double) {
        run("save a payout") { conn ->
            conn.prepareStatement("INSERT INTO sb_gts_payouts (payout_id, player_uuid, amount, created) VALUES (?, ?, ?, ?)").use { st ->
                st.setString(1, UUID.randomUUID().toString())
                st.setString(2, uuid.toString())
                st.setDouble(3, amount)
                st.setLong(4, System.currentTimeMillis())
                st.executeUpdate()
            }
        }
    }

    /** Takes (removes and returns) the unpaid proceeds of [uuid]. */
    fun takePayouts(uuid: UUID): List<Double> = run("read the payouts") { conn ->
        val amounts = conn.prepareStatement("SELECT amount FROM sb_gts_payouts WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs ->
                val list = ArrayList<Double>()
                while (rs.next()) list.add(rs.getDouble(1))
                list
            }
        }
        if (amounts.isNotEmpty()) conn.prepareStatement("DELETE FROM sb_gts_payouts WHERE player_uuid = ?").use { st -> st.setString(1, uuid.toString()); st.executeUpdate() }
        amounts
    } ?: emptyList()

    fun setTimeout(uuid: UUID, until: Long) {
        run("save a timeout") { conn ->
            if (until <= 0) {
                conn.prepareStatement("DELETE FROM sb_gts_timeouts WHERE player_uuid = ?").use { st -> st.setString(1, uuid.toString()); st.executeUpdate() }
                return@run
            }
            val updated = conn.prepareStatement("UPDATE sb_gts_timeouts SET until_time = ? WHERE player_uuid = ?").use { st ->
                st.setLong(1, until)
                st.setString(2, uuid.toString())
                st.executeUpdate()
            }
            if (updated == 0) conn.prepareStatement("INSERT INTO sb_gts_timeouts (player_uuid, until_time) VALUES (?, ?)").use { st ->
                st.setString(1, uuid.toString())
                st.setLong(2, until)
                st.executeUpdate()
            }
        }
    }

    fun timeoutUntil(uuid: UUID): Long = run("read a timeout") { conn ->
        conn.prepareStatement("SELECT until_time FROM sb_gts_timeouts WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    } ?: 0L
}
