package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateCooldownCheckEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateCooldownCheckEvent> =
            EventFactory.createArrayBacked(CrateCooldownCheckEvent::class.java) { listeners ->
                CrateCooldownCheckEvent { player, crate, openData, lastOpened, endsAt, onCooldown ->
                    for (listener in listeners) {
                        listener.onCrateCooldownCheck(player, crate, openData, lastOpened, endsAt, onCooldown)
                    }
                }
            }
    }

    fun onCrateCooldownCheck(
        player: ServerPlayer,
        crate: Crate,
        openData: CrateOpenData,
        lastOpened: Long?,
        endsAt: Long?,
        onCooldown: Boolean,
    )
}
