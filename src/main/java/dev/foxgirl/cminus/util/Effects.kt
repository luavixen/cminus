package dev.foxgirl.cminus.util

import net.minecraft.entity.Entity
import net.minecraft.particle.ParticleEffect
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.Vec3d

fun Entity.play(
    sound: SoundEvent,
    category: SoundCategory = SoundCategory.PLAYERS,
    volume: Double = 1.0,
    pitch: Double = 1.0,
) {
    Broadcast.sendSound(sound, category, volume.toFloat(), pitch.toFloat(), world, pos)
}
fun Entity.particles(
    particle: ParticleEffect,
    speed: Double = 1.0,
    count: Int = 1,
    position: (Vec3d) -> Vec3d = { it.add(0.0, height / 2.0, 0.0) },
) {
    Broadcast.sendParticles(particle, speed.toFloat(), count, world, position(pos))
}
