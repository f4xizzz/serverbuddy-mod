package com.f4xizzz.serverbuddy.guis.config.requirements.types.internal

import com.f4xizzz.serverbuddy.guis.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.guis.config.requirements.Requirement
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class DimensionRequirement(
    type: RequirementType = RequirementType.DIMENSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val id: String = ""
) : Requirement(type, comparison) {
    override fun checkRequirements(player: ServerPlayer, gui: ChestGUI): Boolean {
        if (!checkComparison()) return false

        val value = id.equals(player.serverLevel().dimension().location().toString(), true)

        Utils.printDebug("[REQUIREMENT - ${type?.name}] Player(${player.gameProfile.name}), Player Dimension(${player.serverLevel().dimension().location()}): $this")

        return if (comparison == ComparisonType.NOT_EQUALS) !value else value
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return listOf(ComparisonType.EQUALS, ComparisonType.NOT_EQUALS)
    }

    override fun toString(): String {
        return "DimensionRequirement(comparison=$comparison, id='$id')"
    }
}
