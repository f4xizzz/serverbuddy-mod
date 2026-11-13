package com.f4xizzz.serverbuddy.guis.api

import com.f4xizzz.serverbuddy.guis.config.ConfigManager
import com.f4xizzz.serverbuddy.guis.config.GuiConfig
import net.minecraft.server.level.ServerPlayer

object GUIsAPI {
    fun getGUIConfig(id: String): GuiConfig? {
        return ConfigManager.GUIS[id]
    }

    fun attemptGUIOpen(player: ServerPlayer, id: String) {
        getGUIConfig(id)?.openGUI(player, id)
    }
}
