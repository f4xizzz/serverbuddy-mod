package com.f4xizzz.serverbuddy.crates.data.particles.effects

import com.f4xizzz.serverbuddy.crates.data.particles.effects.types.BeamEffect
import com.f4xizzz.serverbuddy.crates.data.particles.effects.types.CircleEffect
import com.f4xizzz.serverbuddy.crates.data.particles.effects.types.PulseEffect
import com.f4xizzz.serverbuddy.crates.data.particles.effects.types.SpiralEffect

enum class EffectType(val identifier: String, val clazz: Class<*>) {
    SPIRAL("spiral", SpiralEffect::class.java),
    CIRCLE("circle", CircleEffect::class.java),
    BEAM("beam", BeamEffect::class.java),
    PULSE("pulse", PulseEffect::class.java),
    // Future effects
//    CYLINDER("cylinder", ::class.java),
//    VORTEX("vortex", ::class.java),
//    FUNNEL("funnel", ::class.java),
//    HEART("heart", ::class.java)
    ;

    companion object {
        fun valueOfAnyCase(name: String): EffectType? {
            for (type in entries) {
                if (name.equals(type.identifier, true)) return type
            }
            return null
        }
    }
}