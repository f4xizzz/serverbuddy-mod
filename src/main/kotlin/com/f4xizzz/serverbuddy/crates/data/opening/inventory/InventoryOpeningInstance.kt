package com.f4xizzz.serverbuddy.crates.data.opening.inventory

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import com.f4xizzz.serverbuddy.crates.data.opening.OpeningInstance
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.data.userdata.UserData
import com.f4xizzz.serverbuddy.crates.gui.CrateInventory
import com.f4xizzz.serverbuddy.crates.utils.RandomCollection
import net.minecraft.server.level.ServerPlayer

class InventoryOpeningInstance(
    player: ServerPlayer,
    crate: Crate,
    val animation: InventoryOpeningAnimation,
    val randomBag: RandomCollection<Reward>,
    val openData: CrateOpenData,
    val userData: UserData,
): OpeningInstance(player, crate) {
    private val gui = CrateInventory(player, this)

    override fun setup() {
        gui.open()
    }

    override fun tick() {
        gui.tick()
    }

    fun getFinalRewards(): List<Reward> {
        return gui.getFinalRewards()
    }
}
