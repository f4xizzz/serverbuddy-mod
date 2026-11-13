package com.f4xizzz.serverbuddy.testutil

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

/**
 * Some config classes name Minecraft objects in their defaults (menu types, items), and those only exist after the game's
 * registries are bootstrapped. Call [ensure] from a test that builds such a config.
 */
object MinecraftBootstrap {
    @Volatile
    private var done = false

    @Synchronized
    fun ensure() {
        if (done) return
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        done = true
    }
}
