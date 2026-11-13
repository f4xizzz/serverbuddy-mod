package com.f4xizzz.serverbuddy.shop.config

import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.data.entry.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.shop.economy.EconomyType
import com.f4xizzz.serverbuddy.shop.utils.asPlain
import com.f4xizzz.serverbuddy.shop.utils.trimTrailingZeros

class PriceOption(
    val price: Double,
    val economy: EconomyType,
    val currency: String,
    val name: String?,
    @SerializedName("max_amount")
    val maxAmount: Int? = null,
    var requirements: RequirementOptions? = null
) {
    companion object {
        fun formatPricing(price: Double): String {
            return if (ConfigManager.CONFIG.truncatePrices)
                price.trimTrailingZeros() else price.toString()
        }
    }

    fun getCurrencyName(): String {
        return name ?: ShopModule.INSTANCE.getEconomyService(economy)?.name(price, currency)?.asPlain() ?: ""
    }

    override fun toString(): String {
        return "PriceOption(price=$price, economy=$economy, currency='$currency', name='$name')"
    }
}
