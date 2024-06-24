package dev.foxgirl.cminus

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.fabric.FabricAdapter
import com.sk89q.worldedit.function.biome.BiomeReplace
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.function.visitor.RegionVisitor
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.world.biome.BiomeTypes
import dev.foxgirl.cminus.util.*
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.boss.dragon.EnderDragonEntity
import net.minecraft.entity.boss.dragon.EnderDragonFight
import net.minecraft.entity.decoration.Brightness
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.WardenEntity
import net.minecraft.inventory.RecipeInputInventory
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.particle.ParticleTypes
import net.minecraft.recipe.*
import net.minecraft.recipe.book.CraftingRecipeCategory
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.AffineTransformation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.joml.Vector3f
import java.util.*

val endPortalFrameRecipeEntry: RecipeEntry<ShapedRecipe> = RecipeEntry(
    Identifier.of("cminus", "emerald_block_end_portal_frame"),
    object : ShapedRecipe(
        /* group: */ "", /* category: */ CraftingRecipeCategory.MISC,
        /* raw: */ RawShapedRecipe.create(
            mapOf(
                '#' to Ingredient.ofItems(Items.EMERALD_BLOCK),
                'o' to Ingredient.ofItems(Items.ENDER_EYE),
            ),
            "###",
            "#o#",
            "###",
        ),
        /* result: */ stackOf(Items.END_PORTAL_FRAME),
    ) {
        override fun matches(recipeInputInventory: RecipeInputInventory, world: World): Boolean {
            return super.matches(recipeInputInventory, world)
                && recipeInputInventory.asList().all { it.item !== Items.EMERALD_BLOCK || it.count >= 32 }
        }
    }
)

val dragonArmorModifier = EntityAttributeModifier(
    UUID.fromString("97ba2540-4221-445e-b6df-89415b3bbc27"),
    "cminus_dragon_armor", 12.0, EntityAttributeModifier.Operation.ADD_VALUE,
)
val dragonAttackDamageModifier = EntityAttributeModifier(
    UUID.fromString("8e833949-7902-4e28-bf11-fea4a58f71ca"),
    "cminus_dragon_attack_damage", 10.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
)
val dragonAttackKnockbackModifier = EntityAttributeModifier(
    UUID.fromString("f73af995-6ffb-4587-96f1-78a9c9b13d2f"),
    "cminus_dragon_attack_knockback", 2.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
)

suspend fun applyAttributeModifier(entity: LivingEntity, attribute: RegistryEntry<EntityAttribute>, modifier: EntityAttributeModifier) {
    val attributeInstance = Async.poll { entity.getAttributeInstance(attribute) }
    if (attributeInstance.hasModifier(modifier)) {
        attributeInstance.removeModifier(modifier.uuid)
    }
    attributeInstance.addPersistentModifier(modifier)
}

suspend fun applyDragonTweaks(dragonEntity: EnderDragonEntity) {
    dragonEntity.customName = Text.of("Ender D. Ragon")
    dragonEntity.isAiDisabled = true

    applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ARMOR, dragonArmorModifier)
    applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ATTACK_DAMAGE, dragonAttackDamageModifier)
    applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, dragonAttackKnockbackModifier)

    logger.info("(FightSequence) Ender Dragon entity name/state/attributes updated: {}", dragonEntity)
}

fun toMutableText(value: Any?): MutableText {
    if (value == null) return Text.empty()
    if (value is MutableText) return value.copy()
    return Text.literal(value.toString())
}

fun sendMessageAs(actor: Any?, message: Any?, actorColor: Formatting = Formatting.GREEN, messageColor: Formatting = Formatting.RESET) {
    val actorText = toMutableText(actor).formatted(actorColor)
    val messageText = toMutableText(message).formatted(messageColor)
    val text = Text.empty().append("<").append(actorText).append("> ").append(messageText)
    logger.info("(FightSequence) Sending chat message: ${text.string}")
    Broadcast.send(GameMessageS2CPacket(text, false))
}

fun displayTitle(titleMessage: Any? = null, subtitleMessage: Any? = null, titleColor: Formatting = Formatting.WHITE, subtitleColor: Formatting = Formatting.WHITE) {
    val titleText = toMutableText(titleMessage).formatted(titleColor)
    val subtitleText = toMutableText(subtitleMessage).formatted(subtitleColor)
    logger.info("(FightSequence) Displaying title message: ${titleText.string}, ${subtitleText.string}")
    Broadcast.send(TitleFadeS2CPacket(10, 70, 20))
    Broadcast.send(TitleS2CPacket(titleText))
    Broadcast.send(SubtitleS2CPacket(subtitleText))
}

fun playEggSequence(world: ServerWorld, dragonFight: EnderDragonFight, dragonEntity: EnderDragonEntity) {
    Async.go {
        val eggEntity: BlockDisplayEntity = EntityType.BLOCK_DISPLAY.create(world)!!

        eggEntity.setPosition(dragonEntity.pos)
        eggEntity.setBlockState(Blocks.DRAGON_EGG.defaultState)
        eggEntity.setBrightness(Brightness.FULL)
        eggEntity.setGlowing(true)
        eggEntity.setGlowColorOverride(0xFFAA00AA.toInt())
        eggEntity.setViewRange(100.0F)
        eggEntity.setTransformation(AffineTransformation(
            Vector3f(-1.5F, -1.5F, -1.5F),
            AffineTransformation.identity().leftRotation,
            Vector3f(3.0F, 3.0F, 3.0F),
            AffineTransformation.identity().rightRotation,
        ))

        world.spawnEntityAndPassengers(eggEntity)

        Async.go {
            while (eggEntity.isAlive) {
                eggEntity.particles(ParticleTypes.FLASH) { it }
                eggEntity.play(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 0.2, random.nextDouble(0.5, 1.5))
                Async.delay(2)
            }
        }

        Async.delay(140)

        val startPos = eggEntity.pos
        val endPos = dragonFight.exitPortalLocation!!.toCenterPos()

        Async.go {
            for (i in 0 until 200) {
                eggEntity.setPosition(startPos.lerp(endPos, i / 200.0))
                Async.delay()
            }
            for (i in 0 until 6) {
                Async.delay()
                eggEntity.particles(ParticleTypes.EXPLOSION, 10.0, 3) { it }
            }
            eggEntity.play(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 1.4, 1.0)
            eggEntity.kill()
        }
    }
}

fun setupEndFightSequence() {

    val recipeManager = server.recipeManager

    recipeManager.recipesByType = ImmutableMultimap
        .builder<RecipeType<*>, RecipeEntry<*>>()
        .putAll(recipeManager.recipesByType)
        .put(RecipeType.CRAFTING, endPortalFrameRecipeEntry)
        .build()
    recipeManager.recipesById = ImmutableMap
        .builder<Identifier, RecipeEntry<*>>()
        .putAll(recipeManager.recipesById)
        .put(endPortalFrameRecipeEntry.id, endPortalFrameRecipeEntry)
        .build()

    Async.go {

        if (!isCustomDragonFightEnabled) {
            logger.warn("(FightSequence/End) Custom dragon fight is disabled, skipping tweaks!")
            return@go
        }

        logger.info("(FightSequence/End) Setting up...")

        val world = Async.poll { server.getWorld(World.END) }
        logger.info("(FightSequence/End) Server loaded End dimension world")
        val dragonFight = Async.poll { world.enderDragonFight }
        logger.info("(FightSequence/End) Ender Dragon fight ready: {}", dragonFight)

        if (dragonFight.dragonKilled) {
            logger.warn("(FightSequence/End) Ender Dragon is dead, oh no! Skipping tweaks :<")
            return@go
        } else {
            logger.info("(FightSequence/End) Ender Dragon is alive!")
        }

        fun getDragonEntity(): EnderDragonEntity? {
            return world.getEntity(dragonFight.dragonUuid) as? EnderDragonEntity
        }

        Async.go {
            val dragonEntity = Async.poll { getDragonEntity() }
            logger.info("(FightSequence/End) Ender Dragon entity ready: {}", dragonEntity)
            applyDragonTweaks(dragonEntity)
        }

        fun getActivePlayersInEnd(): List<ServerPlayerEntity> {
            return world.players.filter {
                it.isAlive && it.world === world && !it.hasPermissionLevel(2)
            }
        }
        fun areActivePlayersInEnd(): Boolean {
            return getActivePlayersInEnd().isNotEmpty()
        }

        Async.go {
            Async.until { areActivePlayersInEnd() }
            Async.poll { getDragonEntity() }.isAiDisabled = false
            logger.info("(FightSequence/End) Ender Dragon entity AI enabled, active player in end")
        }

        fun displayDragonTitle(message: String) {
            logger.info("(FightSequence/End) Displaying dragon title message: {}", message)
            displayTitle(message, titleColor = Formatting.LIGHT_PURPLE)
            Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        }

        Async.go {
            val narratorDialog = listOf<Text>(
                Text.of("Wealth."),
                Text.of("Fame."),
                Text.of("Power."),
                Text.of("One hint, when your eyes are truly opened"),
                Text.of("One place, foreign to air"),
                Text.empty()
                    .append(Text.literal("Ender Dragon").styled { it.withColor(Formatting.GREEN).withUnderline(true) })
                    .append(", the last Ruler of the End..."),
                Text.of("obtained this, and everything else this dimension had to offer"),
                Text.of("and as she died..."),
                Text.of("her parting words"),
                Text.of("would drive countless gamers to the craft:"),
            )

            val crystalEntities = mutableMapOf<UUID, EndCrystalEntity>()

            val crystalIDs = mutableSetOf<UUID>()
            val crystalIDsConsumed = mutableSetOf<UUID>()

            while (true) {
                for (crystalEntity in world.getEntitiesByType(EntityType.END_CRYSTAL, { true })) {
                    crystalEntities[crystalEntity.uuid] = crystalEntity as EndCrystalEntity
                    crystalIDs.add(crystalEntity.uuid)
                }

                val crystalIDToConsume = crystalIDs.find { id ->
                    id !in crystalIDsConsumed && crystalEntities[id].let { it != null && it.removalReason === Entity.RemovalReason.KILLED }
                }
                if (crystalIDToConsume != null) {
                    sendMessageAs("Narrator", narratorDialog.getOrNull(crystalIDsConsumed.size) ?: Text.of("..."))
                    crystalIDsConsumed.add(crystalIDToConsume)
                }

                Async.delay()
            }
        }

        Async.go {
            fun isDragonDead(): Boolean {
                if (!areActivePlayersInEnd()) return false
                if (dragonFight.dragonKilled) return true
                val dragonEntity = getDragonEntity()
                if (dragonEntity == null) return false
                if (dragonEntity.health > 0.0F) return false
                return dragonEntity.removalReason == Entity.RemovalReason.KILLED
                    || dragonEntity.removalReason == null
            }
            Async.until { isDragonDead() }

            logger.info("(FightSequence/End) Ender Dragon defeated!")

            playEggSequence(world, dragonFight, Async.poll { getDragonEntity() })

            Async.delay(20)
            displayDragonTitle("YOU WANT MY EGG?")
            Async.delay(70)
            displayDragonTitle("YOU CAN HAVE IT.")
            Async.delay(70)
            displayDragonTitle("I LEFT IT IN ONE PLACE")
            Async.delay(70)
            displayDragonTitle("NOW YOU JUST HAVE TO FIND IT!")

            Async.delay(100)

            while (true) {
                val x = random.nextInt(-800, 800)
                val z = random.nextInt(-800, 800)
                if (x * x + z * z < 200 * 200) continue
                val y = random.nextInt(-32, 128)

                val pos = BlockPos(x, y, z)

                val overworld = server.overworld
                if (!(
                    overworld.getBlockState(pos.up()   ).isSolid &&
                    overworld.getBlockState(pos.down() ).isSolid &&
                    overworld.getBlockState(pos.north()).isSolid &&
                    overworld.getBlockState(pos.east() ).isSolid &&
                    overworld.getBlockState(pos.south()).isSolid &&
                    overworld.getBlockState(pos.west() ).isSolid
                )) continue

                val weWorld = FabricAdapter.adapt(overworld)
                val weSession = WorldEdit.getInstance().newEditSession(weWorld)
                try {
                    val weRegion = CuboidRegion(
                        BlockVector3(pos.x - 3, -64, pos.z - 3),
                        BlockVector3(pos.x + 3, 300, pos.z + 3),
                    )

                    val weRegionFunction = BiomeReplace(weSession, BiomeTypes.END_BARRENS)
                    val weRegionVisitor = RegionVisitor(weRegion, weRegionFunction)

                    try {
                        Operations.complete(weRegionVisitor)
                        logger.info("(FightSequence/End) Updated biome at x: ${pos.x}, z: ${pos.z} affecting ${weRegionVisitor.affected} blocks")
                    } catch (cause: WorldEditException) {
                        logger.error("(FightSequence/End) Failed to update biome at x: ${pos.x}, z: ${pos.z}", cause)
                    }
                } finally {
                    weSession.close()
                }

                overworld.setBlockState(pos, Blocks.DRAGON_EGG.defaultState)

                logger.info("(FightSequence/End) Dragon egg placed at x: {}, y: {}, z: {}", pos.x, pos.y, pos.z)
                logger.info("(FightSequence/End) Dragon egg position biome is: {}", overworld.getBiome(pos))

                break
            }
        }

    }

}

suspend fun playerScreenShake(seconds: Double, playerPredicate: (ServerPlayerEntity) -> Boolean = { true }) {
    logger.info("(FightSequence) Starting player screen shake for {} seconds", seconds)
    var running = true; delay((seconds * 20.0).toInt()) { running = false }
    while (running) {
        for (player in server.playerManager.playerList) {
            if (!playerPredicate(player)) continue
            player.yaw += random.nextFloat(-1.0F, 1.0F)
            player.pitch += random.nextFloat(-1.0F, 1.0F)
            player.networkHandler.requestTeleport(player.x, player.y, player.z, player.yaw, player.pitch)
        }
        Async.delay()
    }
}

fun playFinalFightSequence() {
    Async.go {

        logger.info("(FightSequence/Final) Playing final fight sequence...")

        suspend fun delayRelative(ticks: Int) = Async.delay((2.5 * ticks.toDouble()).toInt())

        val world = server.overworld
        val players = server.playerManager

        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1.0F, 1.0F)
        delayRelative(3 * 20)

        Async.go {
            var running = true; Async.go { playerScreenShake(15.0) }.then { running = false }
            while (running) {
                Broadcast.sendSound(SoundEvents.BLOCK_STONE_BREAK, SoundCategory.MASTER, 0.1F, random.nextFloat(0.8F, 1.2F))
                Async.delay()
            }
        }

        sendMessageAs("????? ?????", "of course.")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        sendMessageAs("????? ?????", "i sense that one of you has attained godhood")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("????? ?????", "how does it feel?")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "was it worth it?")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "countless lives lost to my hand, to your kinds recklessness")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "but at least now, one... no, two of you can now taste the power i was born with")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "your disrespect has been noted")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "despite being aware of my power, you mistreat me")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(40)
        sendMessageAs("????? ?????", "and then you take up the visage of my enemy")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("????? ?????", "funny.")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        Async.go {
            var running = true; Async.go { playerScreenShake(8.0) }.then { running = false }
            while (running) {
                Broadcast.sendSound(SoundEvents.BLOCK_STONE_BREAK, SoundCategory.MASTER, 0.1F, random.nextFloat(0.8F, 1.2F))
                Async.delay()
            }
        }

        for (player in players.playerList) {
            player.teleport(world, -37.5, 223.0, 73.5, player.yaw, player.pitch)
        }
        logger.info("(FightSequence/Final) Teleported players to final fight location")

        world.setTimeOfDay(18000)
        logger.info("(FightSequence/Final) Set time of day to midnight")

        delayRelative(3 * 20)

        sendMessageAs("Yumbo Zauce", "well...")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(1 * 20)

        for (playerName in players.whitelistedNames.asList().shuffled()) {
            sendMessageAs(playerName, "THE ELDRICH BASEMENT GOD JUMBO JOSH????", actorColor = Formatting.WHITE)
            Broadcast.sendSound(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.MASTER, 0.3F, 1.0F)
            Async.delay()
        }

        delayRelative(3 * 20)

        val joshEntities = mutableListOf<WardenEntity>()

        for (player in players.playerList) {
            val joshEntity = EntityType.WARDEN.create(world)!!
            joshEntity.refreshPositionAndAngles(0.0, 150.0, 0.0, 0.0F, 0.0F)
            joshEntity.setAiDisabled(true)
            joshEntity.setInvulnerable(true)
            joshEntity.setPersistent()
            joshEntity.setCustomName(Text.of(player.gameProfile.name))
            joshEntity.setCustomNameVisible(false)

            world.spawnEntity(joshEntity)
            joshEntities.add(joshEntity)

            scoreboard.addScoreHolderToTeam(joshEntity.nameForScoreboard, scoreboard.getTeam("cminus")!!)

            Async.go {
                while (joshEntity.isAlive) {
                    if (!joshEntity.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                        joshEntity.addStatusEffect(StatusEffectInstance(StatusEffects.INVISIBILITY, Int.MAX_VALUE, 0, false, false, false))
                    }

                    val currentPlayer = players.getPlayer(joshEntity.customName!!.string) ?: continue
                    val currentPos = currentPlayer.pos.add(Vec3d(0.0, 0.0, 10.0).rotateY((360.0F - currentPlayer.yaw) * MathHelper.RADIANS_PER_DEGREE))
                    joshEntity.teleport(currentPlayer.serverWorld, currentPos.x, currentPos.y, currentPos.z, PositionFlag.VALUES, 0.0F, 0.0F)

                    Async.delay()
                }
            }
        }

        sendMessageAs("Yumbo Zauce", "so you do remember my name.")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "now that youre limited only by your creativity")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "i guess this shouldnt be any issue")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(3 * 20)

        displayTitle("YEA THAT FLOODS HAPPENING NOW", titleColor = Formatting.AQUA)
        Async.go {
            for (i in 0 until 3) {
                Broadcast.sendSound(SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.MASTER, 1.0F, 1.0F)
                Async.delay(20)
            }
        }

        floodTheWorld()

        delayRelative(2 * 20)

        sendMessageAs("Yumbo Zauce", "FROM THE FOUR CORNERS OF YOUR EARTH")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "THIS WORLD IS MINE")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "THE WATERS SHALL RAISE ONCE AGAIN")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "AND YOU THINK THESE WALLS CAN STOP ME??")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))

        val wardenEntities: List<WardenEntity> = Async.await(Async.go {
            logger.info("(FightSequence/Final) Summoning Warden entities...")

            val results = Async.await(
                Editor
                    .queue(world, Region(BlockPos(208, 162, -130), BlockPos(-193, 162, 293)))
                    .search { it.block === Blocks.CRIMSON_HYPHAE && random.nextInt(0, 3) == 0 }
            )

            logger.info("(FightSequence/Final) Selected ${results.size} positions to summon Warden entities")

            val entities = ArrayList<WardenEntity>(results.size)
            var i = 0

            for (result in results) {
                val wardenEntity = EntityType.WARDEN.create(world)!!
                wardenEntity.setPosition(
                    result.pos.x + 0.5, result.pos.y + 1.0, result.pos.z + 0.5,
                )
                wardenEntity.yaw = wardenEntity.random.nextFloat() * 360.0F - 180.0F
                wardenEntity.headYaw = wardenEntity.yaw
                wardenEntity.bodyYaw = wardenEntity.yaw
                wardenEntity.setAiDisabled(true)
                wardenEntity.setPersistent()
                world.spawnEntity(wardenEntity)
                entities.add(wardenEntity)
                if (i++ >= 20) {
                    i = 0
                    Async.delay()
                }
            }

            logger.info("(FightSequence/Final) Summoned ${entities.size} total Warden entities")

            entities
        })

        delayRelative(50)

        sendMessageAs("Yumbo Zauce", "THERES MORE THAN ONE WAY TO WIPE THE SLATE CLEAN.")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "NOTHING BUT CHICKENS SHALL REMAIN")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))
        delayRelative(30)
        sendMessageAs("Yumbo Zauce", "NOTH-")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.7F, random.nextFloat(0.8F, 1.2F))

        val dragonEntity = EntityType.ENDER_DRAGON.create(world)!!
        dragonEntity.refreshPositionAndAngles(-14.5, 230.0, 67.5, 0.0F, 0.0F)
        dragonEntity.setInvulnerable(true)
        dragonEntity.setAiDisabled(false)
        Async.go { applyDragonTweaks(dragonEntity) }
        Async.go {
            while (dragonEntity.isAlive) {
                dragonEntity.setPosition(-14.5, 230.0, 67.5)
                dragonEntity.setAiDisabled(false)
                Async.delay()
            }
        }
        world.spawnEntity(dragonEntity)
        logger.info("(FightSequence/Final) Summoned Ender Dragon entity")

        val lightningEntity1 = EntityType.LIGHTNING_BOLT.create(world)!!
        lightningEntity1.refreshPositionAndAngles(dragonEntity.x, dragonEntity.y - 50, dragonEntity.z, 0.0F, 0.0F)
        world.spawnEntity(lightningEntity1)

        delayRelative(20)
        displayTitle("MORTALS", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        displayTitle("YOUVE FOUND MY LAST TREASURE", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        displayTitle("ACCEPT ITS POWER", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        displayTitle("YOUVE EARNED IT.", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        displayTitle("AS FOR YOU...", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(20)
        sendMessageAs("Yumbo Zauce", "...")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "...me?")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(3 * 20)
        displayTitle("BEGONE.", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        Async.go {
            val wardenEntityIterator = wardenEntities.shuffled().iterator()
            for (i in 0 until 750) {
                if (wardenEntityIterator.hasNext()) {
                    wardenEntityIterator.next().kill()
                }
                dragonEntity.yaw += 10.0F
                for (player in players.playerList) {
                    player.networkHandler.sendPacket(EntityPositionS2CPacket(dragonEntity))
                }
                for (j in 0 until 2) {
                    dragonEntity.particles(ParticleTypes.EXPLOSION) {
                        it.add(
                            random.nextDouble(-16.0, 16.0),
                            random.nextDouble(-12.0, 12.0),
                            random.nextDouble(-16.0, 16.0),
                        )
                    }
                }
                Broadcast.sendSound(SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.MASTER, 0.2F, random.nextFloat(0.8F, 1.2F))
                Async.delay()
            }
            while (wardenEntityIterator.hasNext()) {
                wardenEntityIterator.next().kill()
            }
        }

        delayRelative(3 * 20)
        sendMessageAs("Yumbo Zauce", "ENDER D. RAGON YOU BITCH")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(3 * 20)
        displayTitle("FUCK YOU !!!", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(3 * 20)
        sendMessageAs("Yumbo Zauce", "DAMNIT")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(50)
        sendMessageAs("Yumbo Zauce", "MY CHICKENS, SAVE ME AND MY BRETHEREN!! I BEG OF YUO!!")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(70)
        sendMessageAs("Invincible Chicken 1", "nah, imma do my own thing", actorColor = Formatting.WHITE)
        Broadcast.sendSound(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))

        delayRelative(3 * 20)

        Async.go {
            for (i in 0 until 150) {
                dragonEntity.particles(ParticleTypes.EXPLOSION, 5.0, 3) {
                    it.add(
                        random.nextDouble(-1.0 + (i.toDouble() * -2.0), 1 + (i.toDouble() * 2.0)),
                        random.nextDouble(-1.0 + (i.toDouble() * -2.0), 1 + (i.toDouble() * 2.0)),
                        random.nextDouble(-1.0 + (i.toDouble() * -2.0), 1 + (i.toDouble() * 2.0)),
                    )
                }

                val lightningEntity2 = EntityType.LIGHTNING_BOLT.create(world)!!
                lightningEntity2.refreshPositionAndAngles(dragonEntity.x, dragonEntity.y - 50, dragonEntity.z, 0.0F, 0.0F)
                world.spawnEntity(lightningEntity2)

                Async.delay()
            }
        }

        displayTitle("SUPER ULTRA BANISHMENT BEAM!!!!", titleColor = Formatting.LIGHT_PURPLE)
        Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0F, random.nextFloat(0.8F, 1.2F))
        delayRelative(10)
        sendMessageAs("Invincible Chicken 1", "SUPER ULTRA BANISHMENT BEAM!!!!", actorColor = Formatting.WHITE)
        Broadcast.sendSound(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundCategory.MASTER, 0.75F, random.nextFloat(0.8F, 1.2F))
        delayRelative(10)
        sendMessageAs("Invincible Chicken 2", "SUPER ULTRA BANISHMENT BEAM!!!!", actorColor = Formatting.WHITE)
        Broadcast.sendSound(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundCategory.MASTER, 0.75F, random.nextFloat(0.8F, 1.2F))
        delayRelative(10)
        sendMessageAs("Invincible Chicken 3", "SUPER ULTRA BANISHMENT BEAM!!!!", actorColor = Formatting.WHITE)
        Broadcast.sendSound(SoundEvents.ENTITY_CHICKEN_AMBIENT, SoundCategory.MASTER, 0.75F, random.nextFloat(0.8F, 1.2F))
        delayRelative(10)

        joshEntities.forEach { it.kill() }

        sendMessageAs("Yumbo Zauce", "SHIIIIIIIIIIIIIIIIIIIIIIIIIIT")
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1.5F, 1.0F)
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.3F, random.nextFloat(0.8F, 1.2F))
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.3F, random.nextFloat(0.8F, 1.2F))
        Broadcast.sendSound(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 0.3F, random.nextFloat(0.8F, 1.2F))

        delayRelative(10 * 20)

        for (i in 0 until 5) {
            dragonEntity.particles(ParticleTypes.EXPLOSION, 2.0, 5) {
                it.add(
                    random.nextDouble(-8.0, 8.0),
                    random.nextDouble(-8.0, 8.0),
                    random.nextDouble(-8.0, 8.0),
                )
            }
        }
        Async.delay()

        dragonEntity.remove(Entity.RemovalReason.CHANGED_DIMENSION)
        Broadcast.sendSound(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.7F, 1.0F)

        delayRelative(5 * 20)

        if (players.playerList.isNotEmpty()) {
            sendMessageAs(players.playerList.random().displayName, "well that just happened", actorColor = Formatting.WHITE)
        }

        logger.info("(FightSequence/Final) Final fight sequence completed")

    }
}

object FloodTheWorldAction : Editor.Action {
    private val waterState: BlockState = Blocks.WATER.defaultState
    override fun apply(state: BlockState, x: Int, y: Int, z: Int): BlockState? {
        val distanceX = x - 14
        val distanceZ = z - 81
        val distanceTotalSquared = distanceX * distanceX + distanceZ * distanceZ
        if (distanceTotalSquared < 35344 /* 188 * 188 */) return null
        return if (state.isAir) waterState else state
    }
}

fun floodTheWorld() {
    Async.go {
        logger.info("(FightSequence/Final) Flooding the world, prepare for lag...")

        val world = server.overworld
        val region = Region(BlockPos(14 - 400, 0, 81 - 400), BlockPos(14 + 400, 5, 81 + 400))

        var i = 72
        while (i < 160) {
            Async.await(Editor.queue(world, region.offset(0, i, 0)).edit(FloodTheWorldAction))
            Async.delay(5)
            i += 5
        }

        logger.info("(FightSequence/Final) Flooding completed, starting dropped item removal loop for 60 seconds")

        for (j in 0 until 60) {
            world.getEntitiesByType(EntityType.ITEM, { true }).forEach { it.kill() }
            Async.delay(20)
        }

        logger.info("(FightSequence/Final) Stopped dropped item removal loop")
    }
}

fun setupFinalFightSequence() {
    Async.go {
        val world = server.overworld
        val region = Region(BlockPos(-26, -44, 32), BlockPos(-42, -58, 48))

        suspend fun isEggInDevRoom(): Boolean {
            val eggBlocksInDevRoom = Async.await(Editor.queue(world, region).search { it.block === Blocks.DRAGON_EGG })
            val eggItemsInDevRoom = world.getEntitiesByType(EntityType.ITEM, region.box) { it.stack.item === Items.DRAGON_EGG }
            return !(eggBlocksInDevRoom.isEmpty() && eggItemsInDevRoom.isEmpty())
        }

        if (!isEggInDevRoom()) {
            logger.warn("(FightSequence/Final) Dragon egg not found in dev room, skipping sequence!")
            return@go
        }

        while (true) {
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            if (isEggInDevRoom()) continue
            Async.delay(10)
            break
        }

        logger.info("(FightSequence/Final) Dragon egg was broken! Starting sequence...")

        playFinalFightSequence()
    }
}
