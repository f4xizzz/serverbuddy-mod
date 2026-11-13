package com.f4xizzz.serverbuddy.crates.data.particles

import com.f4xizzz.serverbuddy.crates.data.CrateInstance
import com.f4xizzz.serverbuddy.crates.data.particles.effects.ParticleEffect

class ParticleAnimationOptions(
    val mode: AnimationMode,
    val distance: Double,
    val effects: List<ParticleEffect>
) {
    fun generateAnimation(instance: CrateInstance): ParticleAnimation {
        val animation = ParticleAnimation()

        animation.setMode(mode)
        animation.setDistance(distance)

        for (effect in effects) {
            animation.addTimeline(effect.generateTimeline(instance))
        }

        return animation
    }
}