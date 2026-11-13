package com.f4xizzz.serverbuddy.shop.data.entry.requirements.types.internal

import com.f4xizzz.serverbuddy.shop.data.entry.requirements.ComparisonType
import com.f4xizzz.serverbuddy.shop.data.entry.requirements.Requirement
import com.f4xizzz.serverbuddy.shop.data.entry.requirements.RequirementType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.utils.Utils
import net.minecraft.server.level.ServerPlayer

class DimensionRequirement(
    type: RequirementType = RequirementType.DIMENSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val id: String = ""
) : Requirement(type, comparison) {
    override fun checkRequirements(player: ServerPlayer, gui: IRefreshableGui): Boolean {
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
