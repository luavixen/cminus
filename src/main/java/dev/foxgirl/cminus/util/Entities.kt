package dev.foxgirl.cminus.util

import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.Vec3d
import kotlin.math.min

fun PlayerEntity.give(stack: ItemStack) = this.give(stack, true)
fun PlayerEntity.give(stack: ItemStack, drop: Boolean): Boolean {
    if (stack.isEmpty) return true
    if (giveItemStack(stack)) return true
    if (drop) {
        val entity = dropItem(stack, false)
        if (entity != null) {
            entity.resetPickupDelay()
            entity.setOwner(uuid)
            return true
        }
    }
    return false
}

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

fun Entity.applyVelocity(velocity: Vec3d) {
    addVelocity(velocity)
    velocityDirty = true
    if (this is ServerPlayerEntity) {
        velocityModified = true
    }
}
fun Entity.applyKnockback(strength: Double, x: Double, z: Double) = applyKnockback(strength, x, z, false)
fun Entity.applyKnockback(strength: Double, x: Double, z: Double, force: Boolean) {
    if (this is LivingEntity && !force) {
        takeKnockback(strength, x, z)
    } else {
        applyKnockbackImpl(this, strength, x, z)
    }
    if (velocityDirty && this is ServerPlayerEntity) {
        velocityModified = true
    }
}
private fun applyKnockbackImpl(entity: Entity, strength: Double, x: Double, z: Double) {
    entity.velocityDirty = true
    entity.velocity = entity.velocity.let {
        val delta = Vec3d(x, 0.0, z).normalize().multiply(strength)
        val x = it.x / 2.0 - delta.x
        val z = it.z / 2.0 - delta.z
        val y = if (entity.isOnGround) min(0.4, it.y / 2.0 + strength) else it.y
        Vec3d(x, y, z)
    }
}
