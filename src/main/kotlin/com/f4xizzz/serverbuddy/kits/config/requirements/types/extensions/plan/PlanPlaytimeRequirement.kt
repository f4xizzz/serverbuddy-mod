package com.f4xizzz.serverbuddy.kits.config.requirements.types.extensions.plan

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.PlanExtensionHelper
import com.f4xizzz.serverbuddy.kits.utils.Utils
import net.minecraft.server.level.ServerPlayer

class PlanPlaytimeRequirement(
    type: RequirementType = RequirementType.PERMISSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val time: Long = 0
) : Requirement(type, comparison) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        val playtime = PlanExtensionHelper.getPlaytime(player.uuid)

        Utils.printDebug("Checking a ${type?.identifier} Requirement with playtime='$playtime': $this")

        return when (comparison) {
            ComparisonType.EQUALS -> playtime == time
            ComparisonType.NOT_EQUALS -> playtime != time
            ComparisonType.GREATER_THAN -> playtime > time
            ComparisonType.LESS_THAN -> playtime < time
            ComparisonType.GREATER_THAN_OR_EQUALS -> playtime >= time
            ComparisonType.LESS_THAN_OR_EQUALS -> playtime <= time
        }
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return ComparisonType.values().toList()
    }

    override fun toString(): String {
        return "PlanPlaytimeRequirement(comparison=$comparison, time=$time)"
    }

}
