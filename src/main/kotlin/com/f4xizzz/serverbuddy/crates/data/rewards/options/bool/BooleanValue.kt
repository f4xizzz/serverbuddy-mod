package com.f4xizzz.serverbuddy.crates.data.rewards.options.bool

class BooleanValue(val bool: Boolean) : BooleanOption {
    override fun getValue(): Boolean {
        return bool
    }
}