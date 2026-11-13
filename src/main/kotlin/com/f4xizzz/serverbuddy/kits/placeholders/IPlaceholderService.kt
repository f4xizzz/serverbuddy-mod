package com.f4xizzz.serverbuddy.kits.placeholders

import com.f4xizzz.serverbuddy.kits.config.Kit
import com.f4xizzz.serverbuddy.kits.data.KitData
import net.minecraft.server.level.ServerPlayer

interface IPlaceholderService {
    fun registerPlayer(placeholder: PlayerPlaceholder)
    fun registerServer(placeholder: ServerPlaceholder)
    fun finalizeRegister()
    fun parse(player: ServerPlayer, text: String, kitId: String?, kit: Kit?, kitData: KitData?): String
}
