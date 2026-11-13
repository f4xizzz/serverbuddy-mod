package com.f4xizzz.serverbuddy.guis.config.requirements.types.internal

import com.f4xizzz.serverbuddy.core.script.GraalScripts
import com.f4xizzz.serverbuddy.guis.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.guis.config.requirements.Requirement
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class JavaScriptRequirement(
    type: RequirementType = RequirementType.JAVASCRIPT,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val expression: String = ""
) : Requirement(type, comparison) {
    override fun checkRequirements(player: ServerPlayer, gui: ChestGUI): Boolean {
        if (!checkComparison()) return false

        try {
            val parsed = Utils.parsePlaceholders(player, expression)

            Utils.printDebug("[REQUIREMENT - ${type?.name}] Player(${player.gameProfile.name}), Parsed Expression($parsed): $this")

            val result = GraalScripts.evalBoolean(parsed)

            if (result == null) {
                Utils.printError("[REQUIREMENT - ${type?.name}] Expression '$expression' did not return a boolean!")
                return false
            }

            return if (comparison == ComparisonType.EQUALS) result else !result
        } catch (e: Exception) {
            Utils.printError("[REQUIREMENT - ${type?.name}] An error occurred while parsing the expression '$expression': ${e.printStackTrace()}")
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
