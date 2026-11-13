package com.f4xizzz.serverbuddy.kits.config.requirements.types.internal

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CurrencyRequirement(
    type: RequirementType = RequirementType.CURRENCY,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val currency: String = "",
    private val amount: Double = 0.0
) : Requirement(type, comparison) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        val service = KitsModule.INSTANCE.economyService
        if (service == null) {
            Utils.printError("Currency Requirement was checked but no valid Economy Service could be found.")
            return false
        }

        val balance = service.balance(player, currency)

        return when (comparison) {
            ComparisonType.EQUALS -> balance == amount
            ComparisonType.NOT_EQUALS -> balance != amount
            ComparisonType.GREATER_THAN -> balance > amount
            ComparisonType.LESS_THAN -> balance < amount
            ComparisonType.GREATER_THAN_OR_EQUALS -> balance >= amount
            ComparisonType.LESS_THAN_OR_EQUALS -> balance <= amount
        }
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return ComparisonType.entries
    }

    override fun toString(): String {
        return "CurrencyRequirement(currency='$currency', amount=$amount)"
    }
}
