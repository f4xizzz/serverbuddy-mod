package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import com.f4xizzz.serverbuddy.crates.data.key.Key
import com.f4xizzz.serverbuddy.crates.data.key.KeyCheckResult
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateKeyCheckEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateKeyCheckEvent> =
            EventFactory.createArrayBacked(CrateKeyCheckEvent::class.java) { listeners ->
                CrateKeyCheckEvent { player, crate, openData, keyId, key, amount, requiresHolding, result ->
                    var currentResult = result
                    for (listener in listeners) {
                        currentResult = listener.onCrateKeyCheck(
                            player,
                            crate,
                            openData,
                            keyId,
                            key,
                            amount,
                            requiresHolding,
                            currentResult
                        )
                    }

                    currentResult
                }
            }
    }

    fun onCrateKeyCheck(
        player: ServerPlayer,
        crate: Crate,
        openData: CrateOpenData,
        keyId: String,
        key: Key?,
        amount: Int,
        requiresHolding: Boolean,
        result: KeyCheckResult,
    ): KeyCheckResult
}
