package com.f4xizzz.serverbuddy.kits.config.actions.types

import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.kits.data.KitData
import com.f4xizzz.serverbuddy.kits.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer

class PreviewKit(
    delay: Long = 0,
    chance: Double = 0.0,
    requirements: RequirementOptions? = RequirementOptions(),
    val id: String = "",
) : Action(ActionType.PREVIEW_KIT, delay, chance, requirements) {
    override fun executeAction(player: ServerPlayer, kitId: String?, kit: Kit?, kitData: KitData?, gui: SimpleGui?) {
        Utils.printDebug("[ACTION - ${type.name}] Player(${player.gameProfile.name}) $this")

        val kit = ConfigManager.KITS[id]
        if (kit == null) {
            Utils.printError("The kit $id was not a valid kit id.")
            return
        }

        val preview = kit.createPreview(player) ?: run {
            Utils.printError("The kit $id does not have a preview menu configured or the preview id was invalid.")
            return
        }

        preview.open()
    }

    override fun toString(): String {
        return "PreviewKit(id=$id)"
    }
}
