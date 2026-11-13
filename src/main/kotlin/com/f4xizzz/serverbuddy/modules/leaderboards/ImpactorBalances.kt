package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.core.Integrations
import net.impactdev.impactor.api.economy.EconomyService
import net.impactdev.impactor.api.economy.currency.Currency
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Impactor balances (offline players included) and the currency list. Only touch this object when Impactor is installed
 * ([Integrations.isLoaded]); everything here is safe to call off the server thread.
 */
object ImpactorBalances {
    class CurrencyInfo(val key: String, val name: String)

    private fun plain(component: Component): String = try {
        PlainTextComponentSerializer.plainText().serialize(component)
    } catch (e: Throwable) {
        ""
    }

    private fun service(): EconomyService? = try {
        EconomyService.instance()
    } catch (e: Throwable) {
        null
    }

    fun currencies(): List<CurrencyInfo> {
        val service = service() ?: return emptyList()
        return try {
            service.currencies().registered().map { CurrencyInfo(it.key().asString(), plain(it.plural()).ifBlank { it.key().value() }) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun currency(id: String?): Currency? {
        val service = service() ?: return null
        val provider = service.currencies()
        if (id.isNullOrBlank()) return provider.primary()
        return provider.currency(Key.key(id.trim())).orElse(null)
    }

    /** The full id of the currency ([id] may be empty = the primary one). */
    fun resolveKey(id: String?): String = currency(id)?.key()?.asString() ?: id.orEmpty()

    fun symbol(id: String?): String = currency(id)?.let { plain(it.symbol()) }.orEmpty()

    /** The balance of [uuid] in the currency with this id, or null if it cannot be read. */
    fun balance(currencyId: String?, uuid: UUID): Double? = try {
        val currency = currency(currencyId) ?: return null
        service()?.account(currency, uuid)?.get(5, TimeUnit.SECONDS)?.balance()?.toDouble()
    } catch (e: Exception) {
        null
    }
}
