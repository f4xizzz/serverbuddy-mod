package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.guis.economy.EconomyManager
import com.f4xizzz.serverbuddy.guis.economy.IEconomyService
import net.minecraft.server.level.ServerPlayer

/**
 * Dinheiro para os módulos novos (tags à venda, custo de comandos, recompensas em dinheiro...), em cima das economias
 * que o ServerBuddy já conhece (Impactor, CobbleDollars, BEconomy, Pebbles).
 *
 * A economia é escolhida por um texto: `""` (a primeira instalada), `"impactor"` ou `"impactor:moeda"` (serviço e moeda).
 */
object Economy {
    private fun ensure() {
        if (EconomyManager.getServices().isEmpty()) EconomyManager.init()
    }

    private fun split(spec: String?): Pair<String?, String> {
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return null to ""
        val parts = text.split(':', limit = 2)
        return parts[0].ifEmpty { null } to parts.getOrElse(1) { "" }
    }

    /** O serviço de economia pedido (ou o primeiro instalado), ou null se não há nenhum. */
    @JvmStatic
    fun service(spec: String? = null): IEconomyService? {
        ensure()
        return EconomyManager.getServiceOrDefault(split(spec).first)
    }

    @JvmStatic
    fun available(spec: String? = null): Boolean = service(spec) != null

    @JvmStatic
    fun balance(player: ServerPlayer, spec: String? = null): Double? = service(spec)?.balance(player, split(spec).second)

    /** Tira [amount] do jogador. false se não há economia ou não tem saldo. */
    @JvmStatic
    fun withdraw(player: ServerPlayer, amount: Double, spec: String? = null): Boolean {
        val service = service(spec) ?: return false
        val currency = split(spec).second
        if (service.balance(player, currency) < amount) return false
        return service.withdraw(player, amount, currency)
    }

    @JvmStatic
    fun deposit(player: ServerPlayer, amount: Double, spec: String? = null): Boolean =
        service(spec)?.deposit(player, amount, split(spec).second) ?: false

    /** O valor como texto simples (sem casas decimais quando é inteiro). */
    @JvmStatic
    fun format(amount: Double): String = if (amount == Math.floor(amount) && amount < 1e15) amount.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, amount)
}
