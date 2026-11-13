package com.f4xizzz.serverbuddy.kits.config.requirements.types.internal

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.server.level.ServerPlayer

class PermissionRequirement(
    type: RequirementType = RequirementType.PERMISSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    denyActions: Map<String, Action> = emptyMap(),
    successActions: Map<String, Action> = emptyMap(),
    private val permission: String = ""
) : Requirement(type, comparison, denyActions, successActions) {
    override fun passesRequirements(player: ServerPlayer, kitId: String, kit: Kit, kitData: KitData): Boolean {
        if (!checkComparison())
            return false

        if (permission.isNotEmpty()) {
            val value = Permissions.check(player, permission)
            return if (comparison == ComparisonType.NOT_EQUALS) !value else value
        }

        return true
    }

    override fun allowedComparisons(): List<ComparisonType> {
        return listOf(ComparisonType.EQUALS, ComparisonType.NOT_EQUALS)
    }

    override fun toString(): String {
        return "PermissionRequirement(permission='$permission')"
    }
}
