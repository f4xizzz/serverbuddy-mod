package com.f4xizzz.serverbuddy.guis.economy

import com.f4xizzz.serverbuddy.guis.economy.services.BEconomyEconomyService
import com.f4xizzz.serverbuddy.guis.economy.services.CobbleDollarsEconomyService
import com.f4xizzz.serverbuddy.guis.economy.services.ImpactorEconomyService
import com.f4xizzz.serverbuddy.guis.economy.services.PebblesEconomyService
import net.fabricmc.loader.api.FabricLoader

enum class InternalEconomyTypes(
    val identifier: String,
    val modId: String,
    val clazz: Class<out IEconomyService>
) {
    IMPACTOR("impactor", "impactor", ImpactorEconomyService::class.java),
    PEBBLES("pebbles", "pebbles-economy", PebblesEconomyService::class.java),
    COBBLEDOLLARS("cobbledollars", "cobbledollars", CobbleDollarsEconomyService::class.java),
    BECONOMY("beconomy", "beconomy", BEconomyEconomyService::class.java);

    fun isModPresent() : Boolean {
        return FabricLoader.getInstance().isModLoaded(modId)
    }
}
