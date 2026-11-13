package com.f4xizzz.serverbuddy.kits.config.requirements.types.internal

import com.f4xizzz.serverbuddy.core.script.GraalScripts
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class JavaScriptRequirement(
    type: RequirementType = RequirementType.PERMISSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val expression: String = ""
) : Requirement(type, comparison) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        try {
            val parsed = Utils.parsePlaceholders(player, expression, kitId, kit, kitData)

            Utils.printDebug("Checking a ${type?.identifier} Requirement with parsed expression='$parsed': $this")

            val result = GraalScripts.evalBoolean(parsed)

            if (result == null) {
                Utils.printError("A Javascript Requirement expression '$expression' did not return a boolean!")
                return false
            }

            return if (comparison == ComparisonType.EQUALS) result else !result
        } catch (e: Exception) {
            Utils.printError("An error occurred while parsing the Javascript Requirement expression '$expression': ${e.printStackTrace()}")
        }

        return false
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return listOf(ComparisonType.EQUALS, ComparisonType.NOT_EQUALS)
    }

    override fun toString(): String {
        return "JavascriptRequirement(comparison=$comparison, expression='$expression')"
    }

}
