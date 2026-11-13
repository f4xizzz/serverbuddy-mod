package com.f4xizzz.serverbuddy.clear.config

class MainConfig(
    var debug: Boolean = false,
    val clears: Map<String, ClearConfig> = emptyMap(),
) {
    override fun toString(): String {
        return "MainConfig(debug=$debug, clears=$clears)"
    }
}