package dev.foxgirl.cminus

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.Packet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.GameMode
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

val logger: Logger = LogManager.getLogger("CMinus")

lateinit var server: MinecraftServer

fun init() {
    ServerLifecycleEvents.SERVER_STARTING.register { server = it }
    ServerLifecycleEvents.SERVER_STARTING.register { onStart() }
    ServerTickEvents.START_SERVER_TICK.register { onTick() }
    logger.info("Hello, world! :3c")
}

fun isInGameMode(player: PlayerEntity?): Boolean {
    return player != null && (player as ServerPlayerEntity).interactionManager.gameMode === GameMode.SURVIVAL
}
fun isFlying(player: PlayerEntity?): Boolean {
    return player != null && player.abilities.flying
}

fun onStart() {

}

fun onTick() {

    for (player in server.playerManager.playerList) onTickPlayer(player)

}

fun onTickPlayer(player: ServerPlayerEntity) {

    if (isInGameMode(player)) {
        player.abilities.apply {
            if (player.hungerManager.foodLevel <= 0) {
                if (allowFlying || flying) {
                    allowFlying = false
                    flying = false
                    player.sendAbilitiesUpdate()
                }
            } else if (!allowFlying) {
                allowFlying = true
                player.sendAbilitiesUpdate()
            }
        }
        if (player.air < 0) player.air = 0
    }

}

fun handlePacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*>? {
    return packet
}

fun handlePlayerDamage(player: ServerPlayerEntity, amount: Float, source: DamageSource): Boolean {
    if (isInGameMode(player)) {
        if (
            source.isOf(DamageTypes.FALL) ||
            source.isOf(DamageTypes.IN_WALL) ||
            source.isOf(DamageTypes.FLY_INTO_WALL) ||
            source.isOf(DamageTypes.OUT_OF_WORLD) ||
            source.isOf(DamageTypes.OUTSIDE_BORDER) ||
            source.isOf(DamageTypes.BAD_RESPAWN_POINT)
        ) {
            return true
        }
        if (source.source is PlayerEntity || source.attacker is PlayerEntity) {
            return true
        }
        return false
    }
    return true

}
