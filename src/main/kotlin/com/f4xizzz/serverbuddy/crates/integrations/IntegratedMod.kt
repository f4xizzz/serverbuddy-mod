package com.f4xizzz.serverbuddy.crates.integrations

interface IntegratedMod {
    fun onInit() {}
    fun onServerStarted() {}
    fun onServerStarting() {}
    fun onServerShutdown() {}
}