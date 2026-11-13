package com.f4xizzz.serverbuddy.dashboard

import net.fabricmc.loader.api.FabricLoader
import java.util.Properties

internal object DashboardBuild {
    val rank = mapOf("basic" to 0, "plus" to 1, "pro" to 2)

    private val properties by lazy {
        Properties().apply {
            DashboardBuild::class.java.getResourceAsStream("/serverbuddy-build.properties")?.use { load(it) }
        }
    }

    val apiBase: String
        get() {
            val override = if (FabricLoader.getInstance().isDevelopmentEnvironment) System.getProperty("serverbuddy.api") else null
            return (override ?: properties.getProperty("apiBase", "https://servebuddy.site")).trimEnd('/')
        }

    val version: String get() = properties.getProperty("version", "dev")
}
