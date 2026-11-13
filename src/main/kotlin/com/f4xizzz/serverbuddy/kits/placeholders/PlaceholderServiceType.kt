package com.f4xizzz.serverbuddy.kits.placeholders

import com.f4xizzz.serverbuddy.kits.placeholders.services.ImpactorPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.services.MiniPlaceholdersService
import com.f4xizzz.serverbuddy.kits.placeholders.services.PlaceholderAPIService
import net.fabricmc.loader.api.FabricLoader

enum class PlaceholderServiceType(val id: String, val clazz: Class<out IPlaceholderService>) {
    MINIPLACEHOLDERS("miniplaceholders", MiniPlaceholdersService::class.java),
    PLACEHOLDERAPI("placeholder-api", PlaceholderAPIService::class.java),
    IMPACTOR("impactor", ImpactorPlaceholderService::class.java);

    fun isModPresent() : Boolean {
        return FabricLoader.getInstance().isModLoaded(id)
    }
}
