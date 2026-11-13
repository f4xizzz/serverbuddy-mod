package com.f4xizzz.serverbuddy.announcements.config.requirements.types.internal

import com.f4xizzz.serverbuddy.announcements.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.announcements.config.requirements.Requirement
import com.f4xizzz.serverbuddy.announcements.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.server.level.ServerPlayer

class PermissionRequirement(
    type: RequirementType = RequirementType.PERMISSION,
    comparison: ComparisonType = ComparisonType.EQUALS,
    private val permission: String = ""
) : Requirement(type, comparison) {
    override fun checkRequirements(player: ServerPlayer): Boolean {
        if (!checkComparison())
            return false

        Utils.printDebug("Checking a ${type?.identifier} Requirement with permission='$permission': $this")

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
        return "PermissionRequirement(comparison=$comparison, permission='$permission')"
    }
}
