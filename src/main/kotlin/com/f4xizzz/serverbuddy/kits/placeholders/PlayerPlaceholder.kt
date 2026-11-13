package com.f4xizzz.serverbuddy.kits.placeholders

import net.minecraft.server.level.ServerPlayer

interface PlayerPlaceholder {
    fun handle(player: ServerPlayer, args: List<String>): GenericResult
    fun id(): List<String>
}
