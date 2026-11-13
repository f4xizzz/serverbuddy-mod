package com.f4xizzz.serverbuddy.crates.data.opening

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.managers.OpeningManager
import net.minecraft.server.level.ServerPlayer

abstract class OpeningInstance(
    val player: ServerPlayer,
    val crate: Crate,
) {
    abstract fun setup()

    abstract fun tick()

    open fun stop() {
        destroy()
    }

    open fun destroy() {
        OpeningManager.removeInstance(player.uuid)
    }

    override fun toString(): String {
        return "OpeningInstance(player=$player, crate=$crate)"
    }
}