package com.f4xizzz.serverbuddy.kits.config.requirements.types.internal

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class EmptySlotsRequirement(
    type: RequirementType = RequirementType.ITEM,
    comparison: ComparisonType = ComparisonType.EQUALS,
    val amount: Int = 0,
) : Requirement(type, comparison) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        var amountFound = 0

        for (itemStack in player.inventory.items) {
            if (itemStack.isEmpty) {
                amountFound++
            }
        }

        Utils.printDebug("Checking a ${type?.identifier} Requirement with empty slots found='$amountFound': $this")

        return when (comparison) {
            ComparisonType.EQUALS -> amountFound == amount
            ComparisonType.NOT_EQUALS -> amountFound != amount
            ComparisonType.GREATER_THAN -> amountFound > amount
            ComparisonType.LESS_THAN -> amountFound < amount
            ComparisonType.GREATER_THAN_OR_EQUALS -> amountFound >= amount
            ComparisonType.LESS_THAN_OR_EQUALS -> amountFound <= amount
        }
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return ComparisonType.entries
    }

    override fun toString(): String {
        return "EmptySlotRequirement(amount=$amount)"
    }
}
