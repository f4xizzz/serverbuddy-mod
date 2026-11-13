package com.f4xizzz.serverbuddy.crates.economy.services

import com.f4xizzz.serverbuddy.crates.economy.IEconomyService
import com.f4xizzz.serverbuddy.crates.utils.Utils
import fr.harmex.cobbledollars.common.utils.extensions.cobbleDollars
import net.minecraft.server.level.ServerPlayer
import java.math.BigInteger

// Usa a API do CobbleDollars 2.0.0 Beta-5.1 (propriedade `cobbleDollars`), a mesma do módulo Shop.
class CobbleDollarsEconomyService : IEconomyService {
    init {
        Utils.printInfo("CobbleDollars has been found and loaded for any Currency actions/requirements!")
    }

    override fun balance(player: ServerPlayer, currency: String) : Double {
        return player.cobbleDollars.toDouble()
    }

    override fun withdraw(player: ServerPlayer, amount: Double, currency: String) : Boolean {
        player.cobbleDollars = player.cobbleDollars - BigInteger.valueOf(amount.toLong())
        return true
    }

    override fun deposit(player: ServerPlayer, amount: Double, currency: String) : Boolean {
        player.cobbleDollars = player.cobbleDollars + BigInteger.valueOf(amount.toLong())
        return true
    }

    override fun set(player: ServerPlayer, amount: Double, currency: String) : Boolean {
        player.cobbleDollars = BigInteger.valueOf(amount.toLong())
        return true
    }
}
