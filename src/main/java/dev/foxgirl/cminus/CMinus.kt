package dev.foxgirl.cminus

import com.google.common.collect.ImmutableSet
import dev.foxgirl.cminus.util.Promise
import dev.foxgirl.cminus.util.asList
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.Packet
import net.minecraft.registry.Registries
import net.minecraft.scoreboard.AbstractTeam
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.GameMode
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

val logger: Logger = LogManager.getLogger("CMinus")

lateinit var server: MinecraftServer

fun init() {

    ServerLifecycleEvents.SERVER_STARTING.register { server = it }
    ServerLifecycleEvents.SERVER_STARTED.register { onStart() }
    ServerTickEvents.START_SERVER_TICK.register { onTick() }
    CommandRegistrationCallback.EVENT.register(::onCommandRegistration)

    ServerEntityEvents.ENTITY_LOAD.register(::handleEntityLoad)

    ServerPlayerEvents.ALLOW_DEATH.register(::handlePlayerDeath)
    ServerPlayerEvents.AFTER_RESPAWN.register(::handlePlayerRespawn)
    UseBlockCallback.EVENT.register { player, world, hand, hit ->
        handlePlayerUseBlock(player as ServerPlayerEntity, world as ServerWorld, hand, hit)
    }
    AttackBlockCallback.EVENT.register { player, world, hand, pos, direction ->
        handlePlayerAttackBlock(player as ServerPlayerEntity, world as ServerWorld, hand, pos, direction)
    }

    logger.info("Hello, world! :3c")

    DB.connect().get()

}

val bannedBlocks: Set<Block> = ImmutableSet.of(
    Blocks.AIR,
    Blocks.CAVE_AIR,
    Blocks.VOID_AIR,
    Blocks.STRUCTURE_VOID,
    Blocks.FIRE,
    Blocks.BEDROCK,
    Blocks.BARRIER,
    Blocks.COMMAND_BLOCK,
    Blocks.CHAIN_COMMAND_BLOCK,
    Blocks.REPEATING_COMMAND_BLOCK,
    Blocks.STRUCTURE_BLOCK,
    Blocks.JIGSAW,
    Blocks.EMERALD_BLOCK,
    Blocks.EMERALD_ORE,
    Blocks.DEEPSLATE_EMERALD_ORE,
)

fun isInGameMode(player: PlayerEntity?): Boolean {
    return player != null && (player as ServerPlayerEntity).interactionManager.gameMode === GameMode.SURVIVAL
}
fun isFlying(player: PlayerEntity?): Boolean {
    return player != null && player.abilities.flying
}

fun getItemID(item: Item): Identifier = Registries.ITEM.getId(item)
fun getBlockID(block: Block): Identifier = Registries.BLOCK.getId(block)

fun getBlock(stack: ItemStack): Block = getBlock(stack.item)
fun getBlock(item: Item): Block = getBlock(getItemID(item))
fun getBlock(id: Identifier): Block = Registries.BLOCK.get(id)

fun <T> tryBlock(stack: ItemStack, consumer: (Identifier, Block) -> T?): T? = tryBlock(stack.item, consumer)
fun <T> tryBlock(item: Item, consumer: (Identifier, Block) -> T?): T? {
    val id = getItemID(item)
    val block = getBlock(id)
    return if (block !== Blocks.AIR && block !in bannedBlocks) consumer(id, block) else null
}

fun onStart() {

    val scoreboard = server.scoreboard

    val team = scoreboard.addTeam("cminus")
    team.displayName = Text.of("CMinus")
    team.collisionRule = AbstractTeam.CollisionRule.NEVER
    team.setFriendlyFireAllowed(true)
    team.setShowFriendlyInvisibles(true)

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

        if (player.scoreboardTeam?.name != "cminus") {
            val team = server.scoreboard.getTeam("cminus")
            if (team != null) {
                server.scoreboard.addScoreHolderToTeam(player.nameForScoreboard, team)
            }
        }

        val standKind = try {
            StandKind.valueOf(player.properties.knownStand)
        } catch (cause: IllegalArgumentException) {
            StandKind.VILLAGER
        }

        val oldStandEntity = player.properties.standEntity
        if (oldStandEntity == null || oldStandEntity.isRemoved || oldStandEntity.kind != standKind) {
            oldStandEntity?.remove(Entity.RemovalReason.KILLED)
            val newStandEntity = StandEntity(player, standKind, player.world)
            player.world.spawnEntity(newStandEntity)
            player.properties.standEntity = newStandEntity
        }

    } else {

        val standEntity = player.properties.standEntity
        if (standEntity != null) {
            standEntity.remove(Entity.RemovalReason.KILLED)
            player.properties.standEntity = null
        }

    }

}

fun handlePacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*>? {

    return packet

}

fun setupPlayer(player: ServerPlayerEntity) {

    Promise(Unit)
        .thenCompose(executor = null) { DB.perform { conn, actions -> actions.addPlayer(player.uuid, player.nameForScoreboard) } }
        .then { isNewPlayer ->
            if (isNewPlayer) {
                logger.info("Player {} joined the game for the first time", player.nameForScoreboard)
                player.sendMessage(Text.literal("Welcome to the Creative- server, ").append(player.displayName).append("!"))
            }
        }
        .thenCompose(executor = null) { DB.perform { conn, action -> action.getPlayer(player.uuid) } }
        .then { record ->
            if (record == null) {
                logger.warn("Player {} was not found in the database after being added?", player.nameForScoreboard)
            } else {
                player.properties.knownStand = record.stand
                player.properties.knownLevel = record.level
            }
        }

    val uuid = player.uuid
    val blocks = player.inventory.asList().mapNotNull {
        tryBlock(it) { id, block ->
            if (player.properties.knownOwnedBlocks.add(block)) id.toString() else null
        }
    }
    if (blocks.isNotEmpty()) {
        DB
            .perform { conn, actions -> actions.addBlocks(uuid, blocks) }
            .then { newDiscoveryCount ->
                if (newDiscoveryCount > 0) {
                    logger.info("Player {} discovered {} new blocks during setup", player.nameForScoreboard, newDiscoveryCount)
                }
            }
    }

}

fun handleEntityLoad(entity: Entity, world: ServerWorld) {

    if (entity is ServerPlayerEntity) {
        if (isInGameMode(entity)) {
            setupPlayer(entity)
        }
    } else {
        if (entity !is StandEntity && entity.scoreboardTeam?.name == "cminus") {
            entity.remove(Entity.RemovalReason.KILLED)
        }
    }

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

    return true

}

fun handlePlayerRespawn(oldPlayer: ServerPlayerEntity, newPlayer: ServerPlayerEntity, isOldPlayerAlive: Boolean) {

    if (!isInGameMode(newPlayer)) return

    setupPlayer(newPlayer)

}

fun handlePlayerUseBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, hit: BlockHitResult): ActionResult {

    if (!isInGameMode(player)) return ActionResult.PASS

    tryBlock(player.getStackInHand(hand)) { id, block ->
        DB.perform { conn, actions -> actions.useBlock(player.uuid, id.toString()) }
    }

    return ActionResult.PASS

}

fun handlePlayerAttackBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, pos: BlockPos, direction: Direction): ActionResult {

    return ActionResult.PASS

}

fun handlePlayerInventoryAdd(player: ServerPlayerEntity, stack: ItemStack) {

    if (!isInGameMode(player)) return

    tryBlock(stack) { id, block ->
        if (player.properties.knownOwnedBlocks.add(block)) {
            DB
                .perform { conn, actions -> actions.addBlock(player.uuid, id.toString()) }
                .then { isNewDiscovery ->
                    if (isNewDiscovery) {
                        logger.info("Player {} discovered {}", player.nameForScoreboard, id)
                        player.sendMessage(Text.literal("You discovered ").append(block.name.copy().formatted(Formatting.GREEN)).append("!"))
                    }
                }
        }
    }

}
