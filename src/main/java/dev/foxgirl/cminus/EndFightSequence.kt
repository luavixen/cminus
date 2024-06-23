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
import net.minecraft.inventory.RecipeInputInventory
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.recipe.*
import net.minecraft.recipe.book.CraftingRecipeCategory
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.AffineTransformation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.joml.Vector3f
import java.util.*

fun setupEndFightSequence() {

    val endPortalFrameRecipeEntry = RecipeEntry(
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
            logger.warn("(EndFightSequence) Custom dragon fight is disabled, skipping tweaks!")
            return@go
        }

        logger.info("(EndFightSequence) Setting up...")

        val world = Async.poll { server.getWorld(World.END) }
        logger.info("(EndFightSequence) Server loaded End dimension world")
        val dragonFight = Async.poll { world.enderDragonFight }
        logger.info("(EndFightSequence) Ender Dragon fight ready: {}", dragonFight)

        if (dragonFight.dragonKilled) {
            logger.warn("(EndFightSequence) Ender Dragon is dead, oh no! Skipping tweaks :<")
            return@go
        } else {
            logger.info("(EndFightSequence) Ender Dragon is alive!")
        }

        fun getDragonEntity(): EnderDragonEntity? {
            return world.getEntity(dragonFight.dragonUuid) as? EnderDragonEntity
        }

        Async.go {
            val dragonEntity = Async.poll { getDragonEntity() }
            logger.info("(EndFightSequence) Ender Dragon entity ready: {}", dragonEntity)

            dragonEntity.customName = Text.of("Ender D. Ragon")
            dragonEntity.isAiDisabled = true

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

            applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ARMOR, dragonArmorModifier)
            applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ATTACK_DAMAGE, dragonAttackDamageModifier)
            applyAttributeModifier(dragonEntity, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, dragonAttackKnockbackModifier)

            logger.info("(EndFightSequence) Ender Dragon entity name/state/attributes updated")
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
            logger.info("(EndFightSequence) Ender Dragon entity AI enabled, active player in end")
        }

        fun sendMessageAs(actor: String, message: Text) {
            logger.info("(EndFightSequence) Sending chat message: <{}> {}", actor, message.string)
            val text = Text.empty().append("<").append(Text.literal(actor).formatted(Formatting.GREEN)).append("> ").append(message)
            Broadcast.send(GameMessageS2CPacket(text, false))
        }
        fun displayDragonTitle(message: String) {
            logger.info("(EndFightSequence) Displaying dragon title message: {}", message)
            val text = Text.literal(message).formatted(Formatting.LIGHT_PURPLE)
            Broadcast.send(TitleFadeS2CPacket(10, 70, 20))
            Broadcast.send(TitleS2CPacket(text))
            Broadcast.send(SubtitleS2CPacket(Text.empty()))
            Broadcast.sendSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.0F, random.nextFloat(0.8F, 1.2F))
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

            logger.info("(EndFightSequence) Ender Dragon defeated!")

            playEggAnimation(world, dragonFight, Async.poll { getDragonEntity() })

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
                        logger.info("(EndFightSequence) Updated biome at x: ${pos.x}, z: ${pos.z} affecting ${weRegionVisitor.affected} blocks")
                    } catch (cause: WorldEditException) {
                        logger.error("(EndFightSequence) Failed to update biome at x: ${pos.x}, z: ${pos.z}", cause)
                    }
                } finally {
                    weSession.close()
                }

                overworld.setBlockState(pos, Blocks.DRAGON_EGG.defaultState)

                logger.info("(EndFightSequence) Dragon egg placed at x: {}, y: {}, z: {}", pos.x, pos.y, pos.z)
                logger.info("(EndFightSequence) Dragon egg position biome is: {}", overworld.getBiome(pos))

                break
            }
        }

    }

}

fun playEggAnimation(world: ServerWorld, dragonFight: EnderDragonFight, dragonEntity: EnderDragonEntity) {
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
                eggEntity.play(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.2, random.nextDouble(0.5, 1.5))
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
