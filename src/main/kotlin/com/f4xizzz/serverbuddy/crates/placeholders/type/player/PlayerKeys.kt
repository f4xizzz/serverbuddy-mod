package com.f4xizzz.serverbuddy.crates.placeholders.type.player

import com.f4xizzz.serverbuddy.crates.managers.KeyManager
import com.f4xizzz.serverbuddy.crates.placeholders.GenericResult
import com.f4xizzz.serverbuddy.crates.placeholders.PlayerPlaceholder
import net.kyori.adventure.text.Component
import net.minecraft.server.level.ServerPlayer

class PlayerKeys : PlayerPlaceholder {
    override fun handle(player: ServerPlayer, args: List<String>): GenericResult {
        val keyId = args.firstOrNull() ?: return GenericResult.invalid(Component.text("Key ID Required"))
        return GenericResult.valid(Component.text(KeyManager.getCachedKeys(player.uuid, keyId).toString()))
    }

    override fun id(): String = "keys"
}
