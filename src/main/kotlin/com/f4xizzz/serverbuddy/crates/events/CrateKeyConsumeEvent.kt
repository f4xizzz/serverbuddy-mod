package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import com.f4xizzz.serverbuddy.crates.data.key.Key
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateKeyConsumeEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateKeyConsumeEvent> =
            EventFactory.createArrayBacked(CrateKeyConsumeEvent::class.java) { listeners ->
                CrateKeyConsumeEvent { player, crate, openData, key, amount ->
                    for (listener in listeners) {
                        listener.onCrateKeyConsume(player, crate, openData, key, amount)
                    }
                }
            }
    }

    fun onCrateKeyConsume(player: ServerPlayer, crate: Crate, openData: CrateOpenData, key: Key, amount: Int)
}
