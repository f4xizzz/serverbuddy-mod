package com.f4xizzz.serverbuddy.kits.config.requirements.types.internal

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class PlaceholderRequirement(
    type: RequirementType = RequirementType.PERMISSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val input: String = "",
    private val output: String = "",
    private val strict: Boolean = false
) : Requirement(type, comparison) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        val parsed = Utils.parsePlaceholders(player, input, kitId, kit, kitData)

        Utils.printDebug("Checking a ${type?.identifier} Requirement with parsed input='$parsed': $this")

        val result = parsed.equals(output, strict)

        return if (comparison == ComparisonType.EQUALS) result else !result
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return listOf(ComparisonType.EQUALS, ComparisonType.NOT_EQUALS)
    }

    override fun toString(): String {
        return "PlaceholderRequirement(comparison=$comparison, input='$input', output='$output', strict=$strict)"
    }

}
