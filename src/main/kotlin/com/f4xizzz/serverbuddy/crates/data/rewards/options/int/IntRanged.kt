package com.f4xizzz.serverbuddy.crates.data.rewards.options.int

class IntRanged(
    val min: Int,
    val max: Int,
): IntOption {
    override fun getValue(): Int {
        return (min..max).random()
    }
}