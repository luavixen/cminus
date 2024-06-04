package dev.foxgirl.cminus

import com.google.common.collect.ImmutableSet
import dev.foxgirl.cminus.util.asList
import dev.foxgirl.cminus.util.particles
import dev.foxgirl.cminus.util.play
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.Packet
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.scoreboard.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.text.ClickEvent
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
import kotlin.random.Random

val logger: Logger = LogManager.getLogger("CMinus")

lateinit var server: MinecraftServer

fun init() {

    ServerLifecycleEvents.SERVER_STARTING.register { server = it }
    ServerLifecycleEvents.SERVER_STARTED.register { onStart() }
    ServerTickEvents.START_SERVER_TICK.register { onTick() }
    CommandRegistrationCallback.EVENT.register(::onCommandRegistration)

    ServerEntityEvents.ENTITY_LOAD.register(::handleEntityLoad)

    ServerPlayConnectionEvents.JOIN.register(::handleServerJoin)

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

lateinit var scoreboard: Scoreboard
lateinit var scoreboardLevelObjective: ScoreboardObjective

class DelayedTask(var ticks: Int, val runnable: Runnable)
val delayedTasks = mutableListOf<DelayedTask>()

fun delay(ticks: Int, runnable: Runnable) {
    delayedTasks.add(DelayedTask(ticks, runnable))
}

fun isInGameMode(player: PlayerEntity?): Boolean {
    return player != null && (player as ServerPlayerEntity).interactionManager.gameMode === GameMode.SURVIVAL
}
fun isFlying(player: PlayerEntity?): Boolean {
    return player != null && player.abilities.flying
}

fun getPlayerLevel(player: PlayerEntity): Int {
    return player.properties.knownLevel.coerceAtLeast(1) + 10
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

fun setupPlayer(player: ServerPlayerEntity) {

    DB.perform { conn, actions ->
        val isNewPlayer = actions.addPlayer(player.uuid, player.nameForScoreboard)
        if (isNewPlayer) {
            logger.info("Player {} joined the game for the first time", player.nameForScoreboard)
        }
        var record = actions.getPlayer(player.uuid)
        if (record == null) {
            logger.warn("Player {} was not found in the database after being added?", player.nameForScoreboard)
        } else {
            var wasUpdated = false
            if (record.name != player.nameForScoreboard) {
                record = record.copy(name = player.nameForScoreboard)
                wasUpdated = true
            }
            if (record.stand.isEmpty()) {
                record = record.copy(stand = StandKind.VILLAGER.name)
                wasUpdated = true
            }
            if (record.level <= 0) {
                record = record.copy(level = 1)
                wasUpdated = true
            }
            if (wasUpdated) {
                actions.setPlayer(record)
            }
        }
        record
    }.then { record ->
        if (record != null) {
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
        DB.perform { conn, actions ->
            val newDiscoveryCount = actions.addBlocks(uuid, blocks)
            if (newDiscoveryCount > 0) {
                logger.info("Player {} discovered {} new blocks during setup", player.nameForScoreboard, newDiscoveryCount)
            }
        }
    }

}

fun setupPlayers() {

    delay(20, ::setupPlayers)

    for (player in server.playerManager.playerList) {
        val properties = player.properties
        if (properties.knownStand.isEmpty() || properties.knownLevel <= 0) {
            setupPlayer(player)
        }
    }

}

fun onStart() {

    scoreboard = server.scoreboard

    val team = scoreboard.getTeam("cminus") ?: scoreboard.addTeam("cminus")
    team.displayName = Text.of("CMinus")
    team.collisionRule = AbstractTeam.CollisionRule.NEVER
    team.setFriendlyFireAllowed(true)
    team.setShowFriendlyInvisibles(true)

    val objective = scoreboard.getNullableObjective("cminus_level")
    if (objective != null) {
        scoreboard.removeObjective(objective)
    }
    scoreboardLevelObjective = scoreboard.addObjective(
        "cminus_level",
        ScoreboardCriterion.DUMMY,
        Text.of("Lv"),
        ScoreboardCriterion.RenderType.INTEGER,
        true, null
    )
    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.BELOW_NAME, scoreboardLevelObjective)

    setupPlayers()

}

fun onTick() {

    for (player in server.playerManager.playerList) onTickPlayer(player)

    for (task in delayedTasks.toTypedArray()) {
        if (task.ticks <= 0) {
            task.runnable.run()
            delayedTasks.remove(task)
        }
    }
    for (task in delayedTasks) task.ticks--

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

    if (player.isAlive && player.properties.knownStand.isNotEmpty() && player.properties.knownLevel > 0) {

        val standKind = try {
            StandKind.valueOf(player.properties.knownStand)
        } catch (ignored: IllegalArgumentException) {
            StandKind.VILLAGER
        }

        val oldStandEntity = player.properties.standEntity
        if (oldStandEntity == null || oldStandEntity.isRemoved || oldStandEntity.kind !== standKind) {
            oldStandEntity?.remove(Entity.RemovalReason.KILLED)
            val newStandEntity = StandEntity(player, standKind, player.world)
            player.world.spawnEntity(newStandEntity)
            player.properties.standEntity = newStandEntity
        }

        if (player.scoreboardTeam?.name != "cminus") {
            val team = scoreboard.getTeam("cminus")
            if (team != null) scoreboard.addScoreHolderToTeam(player.nameForScoreboard, team)
        }

        scoreboard.getOrCreateScore(player, scoreboardLevelObjective).score = getPlayerLevel(player)

    }

}

fun handlePacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*>? {

    return packet

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

fun handleServerJoin(handler: ServerPlayNetworkHandler, sender: PacketSender, server: MinecraftServer) {

    val player = handler.player
    player.sendMessage(Text.literal("Welcome to the Creative- server, ").append(player.displayName).append("!"))
    player.sendMessage(Text.literal("You can use ").append(Text.literal("/spectre").formatted(Formatting.GREEN)).append(" to choose your spectre"))
    player.sendMessage(Text.literal("Join the Discord: ").append(Text.literal("https://discord.gg/55zJX4PP6v").styled {
        it.withColor(Formatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/55zJX4PP6v"))
    }))

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

}

fun handlePlayerUseBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, hit: BlockHitResult): ActionResult {

    if (!isInGameMode(player)) return ActionResult.PASS

    tryBlock(player.getStackInHand(hand)) { id, block ->
        if (player.properties.lastUsedBlock !== block) {
            player.properties.lastUsedBlock = block
            DB.perform { conn, actions -> actions.useBlock(player.uuid, id.toString()) }
        }
    }

    return ActionResult.PASS

}

fun handlePlayerAttackBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, pos: BlockPos, direction: Direction): ActionResult {

    return ActionResult.PASS

}

fun handlePlayerAttackAndDamageEntity(player: ServerPlayerEntity, entity: Entity, source: DamageSource, amount: Float) {

    if (!isInGameMode(player)) return

    delay(12) {
        if (entity.isAlive) {
            val success = entity.damage(source, amount * (getPlayerLevel(player).toFloat() / 10.0F))
            if (success) {
                val standEntity = player.properties.standEntity
                if (standEntity != null) {
                    standEntity.play(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, pitch = Random.nextDouble(0.8, 1.2))
                    standEntity.particles(ParticleTypes.CRIT, speed = 0.5, count = 10)
                }
            }
        }
    }

}

fun handlePlayerInventoryAdd(player: ServerPlayerEntity, stack: ItemStack) {

    if (!isInGameMode(player)) return

    tryBlock(stack) { id, block ->
        if (player.properties.knownOwnedBlocks.add(block)) {
            DB.perform { conn, actions ->
                actions.addBlock(player.uuid, id.toString())
            }.then { isNewDiscovery ->
                if (isNewDiscovery) {
                    logger.info("Player {} discovered {}", player.nameForScoreboard, id)
                    player.sendMessage(Text.literal("You discovered ").append(block.name.copy().formatted(Formatting.GREEN)).append("!"))
                }
            }
        }
    }

}
