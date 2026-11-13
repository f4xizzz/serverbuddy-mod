package com.f4xizzz.serverbuddy.crates.data.opening.world

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateInstance
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import com.f4xizzz.serverbuddy.crates.data.opening.OpeningInstance
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.utils.RandomCollection
import net.minecraft.server.level.ServerPlayer

class WorldOpeningInstance(
    player: ServerPlayer,
    crate: Crate,
    val instance: CrateInstance,
    val animation: WorldOpeningAnimation,
    val randomBag: RandomCollection<Reward>,
    val openData: CrateOpenData,
): OpeningInstance(player, crate) {
    override fun tick() {
        animation.tick(this)
    }

    override fun setup() {
        animation.setup(this)
    }

    fun prepareRewards(): List<Reward> {
        return animation.prepareRewards(this)
    }

    override fun stop() {
        super.stop()
        animation.stop(this)
    }
}
