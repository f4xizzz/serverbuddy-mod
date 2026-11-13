package com.f4xizzz.serverbuddy.kits.data

class UserData(
    val kits: MutableMap<String, KitData> = mutableMapOf()
) {
    override fun toString(): String {
        return "UserData(kits=$kits)"
    }
}