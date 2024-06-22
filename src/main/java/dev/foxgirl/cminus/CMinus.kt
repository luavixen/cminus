package dev.foxgirl.cminus

import com.google.common.collect.ImmutableSet
import dev.foxgirl.cminus.util.*
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
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.Packet
import net.minecraft.particle.ParticleTypes
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
import net.minecraft.village.TradeOffer
import net.minecraft.village.TradedItem
import net.minecraft.world.GameMode
import net.minecraft.world.dimension.DimensionTypes
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.ThreadLocalRandom

val logger: Logger = LogManager.getLogger("CMinus")

val random: ThreadLocalRandom
    get() = ThreadLocalRandom.current()

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
    // Invalid or illegal blocks
    Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR,
    Blocks.FIRE, Blocks.BARRIER, Blocks.STRUCTURE_VOID,
    Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
    Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW,
    Blocks.NETHER_PORTAL, Blocks.END_PORTAL,
    Blocks.BEDROCK, Blocks.END_PORTAL_FRAME,
    // Emeralds
    Blocks.EMERALD_BLOCK, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
    // Other ores and minerals
    Blocks.COAL_BLOCK, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
    Blocks.IRON_BLOCK, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
    Blocks.GOLD_BLOCK, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
    Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
    Blocks.LAPIS_BLOCK, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
    Blocks.DIAMOND_BLOCK, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
    Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK,
    Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK,
    // Other "infinite loops"
    Blocks.COBWEB,
    Blocks.HAY_BLOCK,
    Blocks.PUMPKIN, Blocks.MELON,
    // THE ONE PIECE !! (just the egg)
    Blocks.DRAGON_EGG,
)

val exclusiveBlocks: Set<Block> = ImmutableSet.of(
    // THE ONE PIECE !!
    Blocks.DRAGON_HEAD, Blocks.DRAGON_WALL_HEAD,
    Blocks.DRAGON_EGG,
)

fun canTradeBlock(block: Block): Boolean {
    return block !in bannedBlocks || block in exclusiveBlocks
}

class Trade(val block: Block) {
    val item: Item = block.asItem()

    val amount: Int
    val cost: Int
    val offer: TradeOffer

    init {
        if (block in exclusiveBlocks) {
            amount = 1
            cost = 16
        } else {
            amount = item.maxCount
            cost = (item.maxCount / 4).coerceAtLeast(1)
        }
        offer = TradeOffer(TradedItem(Items.EMERALD, cost), stackOf(item, amount), Int.MAX_VALUE, 1, 0.05F)
    }
}

fun getTrade(block: Block) = Trade(block)

val bossEntityTypes: Set<EntityType<*>> = ImmutableSet.of(
    EntityType.PLAYER,
    EntityType.ENDER_DRAGON,
    EntityType.WITHER,
    EntityType.WITHER_SKULL,
    EntityType.WARDEN,
    EntityType.ELDER_GUARDIAN,
    EntityType.GUARDIAN,
    EntityType.PHANTOM,
)

val isEggCreativeModeEnabled: Boolean = false

lateinit var scoreboard: Scoreboard
lateinit var scoreboardLevelObjective: ScoreboardObjective

class DelayedTask(var ticks: Int, val runnable: Runnable)
val delayedTasks = mutableListOf<DelayedTask>()

fun delay(ticks: Int, runnable: Runnable) {
    if (!server.isOnThread) {
        throw IllegalStateException("Attempted to create delayed task on wrong thread")
    }
    delayedTasks.add(DelayedTask(ticks, runnable))
}

fun isInSpecificGameMode(player: PlayerEntity?, mode: GameMode): Boolean {
    return player != null && (player as ServerPlayerEntity).interactionManager.gameMode === mode
}

fun isInGameMode(player: PlayerEntity?): Boolean {
    return isInSpecificGameMode(player, GameMode.SURVIVAL)
}

fun isInEndDimension(entity: Entity?): Boolean {
    return entity != null && entity.world.dimensionEntry.matchesKey(DimensionTypes.THE_END)
}

fun shouldHaveEggCreativeMode(player: PlayerEntity?): Boolean {
    if (!isEggCreativeModeEnabled) return false
    if (player != null && (isInGameMode(player) || isInSpecificGameMode(player, GameMode.CREATIVE))) {
        return player.inventory.containsAny { it.item === Items.DRAGON_EGG }
            || player.currentScreenHandler?.cursorStack?.item === Items.DRAGON_EGG
    }
    return false
}

fun isInGameMode(player: PlayerEntity?, shouldIncludeEnd: Boolean = false, shouldIncludeEggCreativeMode: Boolean = false): Boolean {
    if (shouldIncludeEggCreativeMode && shouldHaveEggCreativeMode(player)) {
        return true
    }
    if (isInGameMode(player)) {
        if (shouldIncludeEnd) return true
        if (isInEndDimension(player)) return false
        return true
    }
    return false
}
fun isInGameMode(player: PlayerEntity?, shouldIncludeEnd: Boolean): Boolean {
    return isInGameMode(player, shouldIncludeEnd = shouldIncludeEnd, shouldIncludeEggCreativeMode = false)
}

fun isFlying(player: PlayerEntity?): Boolean {
    return player != null && player.abilities.flying
}

fun isInstantMiningActive(player: PlayerEntity): Boolean {
    return player.extraFields.isInstantMiningActive
}

fun getLevel(player: PlayerEntity): Int {
    return (player.extraFields.knownLevel / 16).coerceAtLeast(1) + 10
}
fun getLevelMultiplier(player: PlayerEntity): Float {
    return getLevel(player) * 0.1F
}

fun getDamageDivisor(player: PlayerEntity): Float {
    val divisor = 1.0F + getLevelMultiplier(player)
    return if (isInEndDimension(player)) {
        divisor.coerceAtMost(10.0F)
    } else {
        divisor
    }
}

fun <T> tryBlock(stack: ItemStack, consumer: (ItemStack, Identifier, Block) -> T?): T? {
    val id = getItemID(stack.item)
    val block = getBlock(id)
    return if (block !== Blocks.AIR && block !in bannedBlocks) consumer(stack, id, block) else null
}

fun setupPlayer(player: ServerPlayerEntity) {

    logger.debug("Player {} is being set up", player.nameForScoreboard)

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
            player.extraFields.knownStand = record.stand
            player.extraFields.knownLevel = record.level
        }
    }

    val uuid = player.uuid
    val blocks = player.inventory.asList().mapNotNull {
        tryBlock(it) { _, id, block ->
            if (player.extraFields.knownOwnedBlocks.add(block)) id.toString() else null
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
        val properties = player.extraFields
        if (properties.isKnownStandOrLevelUnset()) {
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

    setupEndTweaks()

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

    var isInGameMode = isInGameMode(player)
    var isInCreativeMode = isInSpecificGameMode(player, GameMode.CREATIVE)

    val shouldHaveEggCreativeMode = shouldHaveEggCreativeMode(player)

    fun switchGameMode(mode: GameMode) {
        player.changeGameMode(mode)
        isInGameMode = mode === GameMode.SURVIVAL
        isInCreativeMode = mode === GameMode.CREATIVE
    }

    if (isInCreativeMode && !shouldHaveEggCreativeMode && !player.hasPermissionLevel(2)) {
        switchGameMode(GameMode.SURVIVAL)
    } else if (!isInCreativeMode && shouldHaveEggCreativeMode) {
        switchGameMode(GameMode.CREATIVE)
    }

    if (!isInGameMode && !(isInCreativeMode && shouldHaveEggCreativeMode)) return

    if (isInGameMode) {
        player.abilities.apply {
            if (player.hungerManager.foodLevel <= 0 || isInEndDimension(player)) {
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
    }

    if (player.air < 0) player.air = 0

    if (player.isAlive) {

        if (player.scoreboardTeam?.name != "cminus") {
            val team = scoreboard.getTeam("cminus")
            if (team != null) scoreboard.addScoreHolderToTeam(player.nameForScoreboard, team)
        }

        if (!player.extraFields.isKnownLevelUnset()) {
            scoreboard.getOrCreateScore(player, scoreboardLevelObjective).score = getLevel(player)
        }

        if (!player.extraFields.isKnownStandUnset()) {
            val standKind = try {
                StandKind.valueOf(player.extraFields.knownStand)
            } catch (ignored: IllegalArgumentException) {
                StandKind.VILLAGER
            }

            val oldStandEntity = player.extraFields.standEntity
            if (oldStandEntity == null || oldStandEntity.isRemoved || oldStandEntity.kind !== standKind) {
                oldStandEntity?.remove(Entity.RemovalReason.KILLED)
                val newStandEntity = StandEntity(player, standKind, player.world)
                player.world.spawnEntity(newStandEntity)
                player.extraFields.standEntity = newStandEntity
            }
        }

    }

}

fun handlePacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*>? {

    return packet

}

fun handleEntityLoad(entity: Entity, world: ServerWorld) {

    if (entity is ServerPlayerEntity) {
        if (isInGameMode(entity, shouldIncludeEggCreativeMode = true)) {
            setupPlayer(entity)
        }
    } else {
        if (entity !is StandEntity && entity.scoreboardTeam?.name == "cminus" && !entity.hasCustomName()) {
            entity.remove(Entity.RemovalReason.KILLED)
        }
    }

}

fun handleServerJoin(handler: ServerPlayNetworkHandler, sender: PacketSender, server: MinecraftServer) {

    val player = handler.player
    player.sendMessage(Text.literal("Welcome to CREATIVE-, ").append(player.displayName).append("!"))
    player.sendMessage(Text.literal("You can use ").append(Text.literal("/spectre").formatted(Formatting.GREEN)).append(" to choose your spectre"))
    player.sendMessage(Text.literal("Join the Discord: ").append(Text.literal("https://discord.gg/55zJX4PP6v").styled {
        it.withColor(Formatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/55zJX4PP6v"))
    }))
    player.sendMessage(Text.literal("View the Dynmap: ").append(Text.literal("https://cminus.foxgirl.dev/").styled {
        it.withColor(Formatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, "https://cminus.foxgirl.dev/"))
    }))

}

fun handlePlayerDamage(player: ServerPlayerEntity, source: DamageSource, amount: Float): Boolean {

    if (!isInGameMode(player, shouldIncludeEnd = false)) return true

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

    if (
        source.isOf(DamageTypes.DRAGON_BREATH) ||
        source.isOf(DamageTypes.WITHER_SKULL)
    ) {
        return true
    }

    if (source.source?.type in bossEntityTypes || source.attacker?.type in bossEntityTypes) {
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

    tryBlock(player.getStackInHand(hand)) { stack, id, block ->
        if (player.extraFields.lastUsedBlock !== block) {
            player.extraFields.lastUsedBlock = block
            DB.perform { conn, actions -> actions.useBlock(player.uuid, id.toString()) }
        }
        if (stack.count == 1) {
            val list = player.inventory.asList()
            val i1 = list.indexOf(stack)
            val i2 = list.indexOfFirst { it !== stack && it.item === stack.item }
            if (i1 >= 0 && i2 >= 0) {
                delay(1) {
                    if (list[i1].isEmpty && !list[i2].isEmpty) {
                        list[i1] = list[i2]
                        list[i2] = stackOf()
                        player.inventory.markDirty()
                    }
                }
            }
        }
    }

    return ActionResult.PASS

}

fun handlePlayerAttackBlock(player: ServerPlayerEntity, world: ServerWorld, hand: Hand, pos: BlockPos, direction: Direction): ActionResult {

    return ActionResult.PASS

}

fun handlePlayerAttackAndDamageEntity(player: ServerPlayerEntity, entity: Entity, source: DamageSource, amount: Float) {

    if (!isInGameMode(player, shouldIncludeEggCreativeMode = true)) return

    delay(12) {

        if (!entity.isAlive) return@delay

        val standEntity = player.extraFields.standEntity
        if (standEntity == null) {
            logger.debug("Player {} stand entity is missing", player.nameForScoreboard)
            return@delay
        }
        if (standEntity.world !== entity.world || standEntity.squaredDistanceTo(entity) > (7.0 * 7.0)) {
            logger.debug("Player {} stand entity is too far from {}", player.nameForScoreboard, entity.displayName?.string)
            return@delay
        }

        val success = entity.damage(source, amount * getLevelMultiplier(player))
        if (success) {
            standEntity.play(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, pitch = 0.8 + 0.4 * standEntity.random.nextDouble())
            standEntity.particles(ParticleTypes.CRIT, speed = 0.5, count = 10)
            logger.debug("Player {} stand entity attacked {}", player.nameForScoreboard, entity.displayName?.string)
        } else {
            logger.debug("Player {} stand entity failed to attack {}", player.nameForScoreboard, entity.displayName?.string)
        }

    }

}

fun handlePlayerInventoryAdd(player: ServerPlayerEntity, stack: ItemStack) {

    if (!isInGameMode(player)) return

    tryBlock(stack) { _, id, block ->
        if (player.extraFields.knownOwnedBlocks.add(block)) {
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
