package dev.foxgirl.cminus

import dev.foxgirl.cminus.util.UUIDEncoding
import dev.foxgirl.cminus.util.asList
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.Packet
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.GameMode
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

val logger: Logger = LogManager.getLogger("CMinus")

lateinit var server: MinecraftServer

fun init() {
    ServerLifecycleEvents.SERVER_STARTING.register { server = it }
    ServerLifecycleEvents.SERVER_STARTING.register { onStart() }
    ServerTickEvents.START_SERVER_TICK.register { onTick() }
    ServerPlayerEvents.ALLOW_DEATH.register(::handlePlayerDeath)
    ServerPlayerEvents.AFTER_RESPAWN.register(::handlePlayerRespawn)
    UseBlockCallback.EVENT.register { player, world, hand, hit ->
        handlePlayerUseBlock(player as ServerPlayerEntity, world as ServerWorld, hand, hit)
    }
    logger.info("Hello, world! :3c")
    DB.connect().get()
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

    if (!isInGameMode(player)) return

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

fun handlePacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*>? {

    return packet

}

fun handlePlayerDamage(player: ServerPlayerEntity, source: DamageSource, amount: Float): Boolean {

    if (!isInGameMode(player)) return true

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

fun handlePlayerDeath(player: ServerPlayerEntity, source: DamageSource, amount: Float): Boolean {

    DB
        .acquire { conn, actions ->
            val blocks = mutableListOf<String>()
            conn.prepareStatement("SELECT block FROM blocks WHERE player = ?").use { stmt ->
                stmt.setBytes(1, UUIDEncoding.toByteArray(player.uuid))
                stmt.executeQuery().use { results ->
                    while (results.next()) {
                        blocks.add(results.getString("block"))
                    }
                }
            }
            blocks
        }
        .thenAcceptAsync({ blocks -> blocks.forEach { player.sendMessage(Text.literal(" - ").append(Registries.BLOCK.get(Identifier(it)).name)) } }, server)

    return true

}

fun handlePlayerRespawn(oldPlayer: ServerPlayerEntity, newPlayer: ServerPlayerEntity, isOldPlayerAlive: Boolean) {

    if (!isInGameMode(newPlayer)) return

    val uuid = newPlayer.uuid
    val blocks = mutableListOf<String>()

    for (stack in newPlayer.inventory.asList()) {
        if (stack.isEmpty) continue
        val id = Registries.ITEM.getId(stack.item)
        val block = Registries.BLOCK.get(id)
        if (block !== Blocks.AIR && newPlayer.knownOwnedBlocks.add(block)) {
            blocks.add(id.toString())
        }
    }

    if (blocks.isNotEmpty()) {
        DB.acquire { conn, actions -> actions.addBlocks(uuid, blocks) }
    }

}

fun handlePlayerUseBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, hit: BlockHitResult): ActionResult {

    if (isInGameMode(player)) return ActionResult.PASS

    val stack = player.getStackInHand(hand)
    if (stack.isEmpty) return ActionResult.PASS

    val uuid = player.uuid

    val id = Registries.ITEM.getId(stack.item)
    val block = Registries.BLOCK.get(id)
    if (block !== Blocks.AIR) {
        DB.acquire { conn, actions -> actions.useBlock(uuid, id.toString()) }
    }

    return ActionResult.PASS

}

fun handlePlayerInventoryAdd(player: ServerPlayerEntity, stack: ItemStack) {

    if (stack.isEmpty || !isInGameMode(player)) return

    val uuid = player.uuid

    val id = Registries.ITEM.getId(stack.item)
    val block = Registries.BLOCK.get(id)
    if (block !== Blocks.AIR && player.knownOwnedBlocks.add(block)) {
        DB
            .acquire { conn, actions -> actions.addBlock(uuid, id.toString()) }
            .thenAcceptAsync({ if (it) player.sendMessage(Text.literal("You discovered ").append(block.name).append("!")) }, server)
    }

}
