package com.f4xizzz.serverbuddy.kits.placeholders

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.placeholders.services.DefaultPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.services.ImpactorPlaceholderService
import com.f4xizzz.serverbuddy.kits.placeholders.services.MiniPlaceholdersService
import com.f4xizzz.serverbuddy.kits.placeholders.services.PlaceholderAPIService
import com.f4xizzz.serverbuddy.kits.placeholders.type.player.*
import net.minecraft.server.level.ServerPlayer
import java.util.stream.Stream

object PlaceholderManager {
    private val services: MutableList<IPlaceholderService> = mutableListOf()

    fun init() {
        services.add(DefaultPlaceholderService())
        for (service in PlaceholderServiceType.entries) {
            if (service.isModPresent()) {
                services.add(getServiceForType(service))
            }
        }
        registerPlaceholders()
    }

    private fun registerPlaceholders() {
        // SERVER PLACEHOLDERS
//        Stream.of(
//        ).forEach { placeholder -> services.forEach { it.registerServer(placeholder) } }

        // PLAYER PLACEHOLDERS
        Stream.of(
            CooldownPlaceholder(), // Returns a formatted string of the remaining cooldown for the kit, or "None" if there is no cooldown or it has expired
            UsesPlaceholder(), // Returns the number of times the player has used the kit, or "0" if they haven't used it before
            LastUsedPlaceholder(), // Returns a formatted string of the last time the kit was used, or "Never" if it hasn't been used before
            IsMaxUsedPlaceholder(), // Returns a boolean of whether the player has reached the max uses for the kit
            IsOnCooldownPlaceholder(), // Returns a boolean of whether the player is currently on cooldown for the kit
            IsAvailablePlaceholder(), // Returns a boolean that checks both cooldown and use limits
        ).forEach { placeholder -> services.forEach { it.registerPlayer(placeholder) } }

        services.forEach { it.finalizeRegister() }
    }

    fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?, additionalPlaceholders: Map<String, String> = emptyMap()): String {
        var returnValue = text.let {
            additionalPlaceholders.entries.fold(it) { acc, (key, value) ->
                acc.replace(key, value)
            }
        }
        for (service in services) {
            returnValue = service.parse(player, returnValue, kitId, kit, kitData)
        }
        return returnValue
    }

    private fun getServiceForType(placeholderMod: PlaceholderServiceType): IPlaceholderService {
        return when (placeholderMod) {
            PlaceholderServiceType.IMPACTOR -> ImpactorPlaceholderService()
            PlaceholderServiceType.PLACEHOLDERAPI -> PlaceholderAPIService()
            PlaceholderServiceType.MINIPLACEHOLDERS -> MiniPlaceholdersService()
        }
    }
}
