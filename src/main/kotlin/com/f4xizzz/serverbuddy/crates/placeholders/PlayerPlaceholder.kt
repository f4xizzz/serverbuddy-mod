package com.f4xizzz.serverbuddy.crates.placeholders

import net.minecraft.server.level.ServerPlayer

interface PlayerPlaceholder {
    fun handle(player: ServerPlayer, args: List<String>): GenericResult
    fun id(): String
}
