package com.f4xizzz.serverbuddy.crates.integrations.holodisplays

import com.f4xizzz.serverbuddy.crates.integrations.IntegratedMod
import com.f4xizzz.serverbuddy.crates.managers.HologramsManager
import com.f4xizzz.serverbuddy.crates.utils.Utils

class HoloDisplaysIntegration: IntegratedMod {
    override fun onServerStarted() {
        Utils.printInfo("The mod HoloDisplays was found, enabling integrations...")
        HologramsManager.load()
    }

    override fun onServerShutdown() {
        Utils.printInfo("Shutting down HoloDisplays integrations...")
        HologramsManager.unload()
    }
}