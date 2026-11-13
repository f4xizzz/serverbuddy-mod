package com.f4xizzz.serverbuddy.guis.config.actions.types

import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolve
import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.guis.config.actions.Action
import com.f4xizzz.serverbuddy.guis.config.actions.ActionType
import com.f4xizzz.serverbuddy.guis.config.actions.ClickType
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.minecraft.server.level.ServerPlayer

class Molang(
    type: ActionType = ActionType.MOLANG,
    click: List<ClickType> = listOf(ClickType.ANY),
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val script: List<String> = listOf()
) : Action(
    type,
    click,
    delay,
    chance,
    requirements
) {
    override fun executeAction(player: ServerPlayer, gui: ChestGUI) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}): $this")
        gui.manager?.runtime?.resolve(
            script.asExpressionLike(),
            mapOf("player" to player.asMoLangValue())
        )
    }

    override fun toString(): String {
        return "MolangAction(click=$click, delay=$delay, chance=$chance, requirements=$requirements, " +
                "script=$script)"
    }
}
