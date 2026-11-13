package com.f4xizzz.serverbuddy.crates.data.rewards.options.int

class IntValue(
    val int: Int
): IntOption {
    override fun getValue(): Int {
        return int
    }
}