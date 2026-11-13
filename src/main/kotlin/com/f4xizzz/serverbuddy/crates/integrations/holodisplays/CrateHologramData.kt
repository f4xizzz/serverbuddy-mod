package com.f4xizzz.serverbuddy.crates.integrations.holodisplays

import com.f4xizzz.serverbuddy.crates.data.CrateInstance
import java.util.*

class CrateHologramData(
    val instance: CrateInstance,
    val hiddenPlayers: MutableList<UUID> = mutableListOf()
)