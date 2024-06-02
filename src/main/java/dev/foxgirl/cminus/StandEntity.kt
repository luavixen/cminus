package dev.foxgirl.cminus

import dev.foxgirl.cminus.util.Promise
import dev.foxgirl.cminus.util.particles
import dev.foxgirl.cminus.util.play
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.MovementType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.*
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.*
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.village.Merchant
import net.minecraft.village.TradeOffer
import net.minecraft.village.TradeOfferList
import net.minecraft.village.TradedItem
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent
import java.util.*
import kotlin.random.Random

class StandEntity(val owner: PlayerEntity, val kind: StandKind, world: World) : LivingEntity(kind.entityType, world), Merchant {

    init {
        setInvulnerable(true)
        setSilent(true)

        setNoGravity(true)
        setNoDrag(true)
        noClip = true

        refreshPositionAndAngles(owner.x, owner.y, owner.z, owner.yaw, owner.pitch)

        val team = scoreboard.getTeam("cminus")
        if (team != null) {
            scoreboard.addScoreHolderToTeam(nameForScoreboard, team)
        } else {
            logger.warn("Failed to get team for stand entity")
        }

        kind.entityInitializer(this)
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)

        if (type === EntityType.CAT) {
            builder.add(CatEntity.CAT_VARIANT, Registries.CAT_VARIANT.entryOf(CatVariant.BLACK))
        } else if (type === EntityType.WOLF) {
            builder.add(WolfEntity.VARIANT, registryManager.get(RegistryKeys.WOLF_VARIANT).entryOf(WolfVariants.PALE))
        } else if (type === EntityType.PARROT) {
            builder.add(ParrotEntity.VARIANT, 0)
        }
    }

    fun setVariantCat(variant: RegistryKey<CatVariant>) {
        dataTracker.set(CatEntity.CAT_VARIANT, Registries.CAT_VARIANT.entryOf(variant))
    }
    fun setVariantWolf(variant: RegistryKey<WolfVariant>) {
        dataTracker.set(WolfEntity.VARIANT, registryManager.get(RegistryKeys.WOLF_VARIANT).entryOf(variant))
    }
    fun setVariantParrot(variant: ParrotEntity.Variant) {
        dataTracker.set(ParrotEntity.VARIANT, variant.id)
    }

    override fun getYesSound(): SoundEvent {
        return kind.entitySound ?: SoundEvents.ENTITY_VILLAGER_YES
    }

    private var customer: PlayerEntity? = null

    override fun getCustomer(): PlayerEntity? {
        return customer
    }
    override fun setCustomer(customer: PlayerEntity?) {
        this.customer = customer
    }

    private val offers = TradeOfferList()

    private var offersUpdatePromise: Promise<Unit>? = null
    private var offersLastUpdated: Int = -1000

    private fun updateOffersActual(): Promise<Unit> {
        return DB
            .perform { conn, actions ->
                actions.listBlocks(owner.uuid).toList()
            }
            .then { records ->
                offers.clear()
                for (record in records) {
                    val block = getBlock(Identifier(record.block))
                    if (block !in bannedBlocks) {
                        val offer = TradeOffer(TradedItem(Items.EMERALD, 16), ItemStack(block.asItem(), 64), Int.MAX_VALUE, 1, 0.05F)
                        offers.add(offer)
                    }
                }
            }
            .finally { _, cause ->
                if (cause != null) {
                    logger.error("Failed to update trade offers for stand entity", cause)
                }
                offersUpdatePromise = null
                offersLastUpdated = age
            }
    }
    private fun updateOffers(): Promise<Unit> {
        if (age - offersLastUpdated < 60) {
            return Promise(Unit)
        } else {
            return offersUpdatePromise ?: updateOffersActual().also { offersUpdatePromise = it }
        }
    }

    private fun updateAndSendOffers(player: PlayerEntity): Promise<Unit> {
        return updateOffers().then(executor = null) {
            setCustomer(player)
            sendOffers(player, Text.empty().append(owner.displayName).append("'s Spectre - Lv ${getPlayerLevel(owner)}"), 0)
        }
    }

    fun prepareOffersFor(player: PlayerEntity): Promise<Unit> {
        setCustomer(player)
        return updateOffers()
    }

    override fun getOffers(): TradeOfferList {
        return offers
    }

    override fun trade(offer: TradeOffer) {
        offer.use()

        play(yesSound, pitch = Random.nextDouble(0.8, 1.2))

        for (i in 0 until 5) {
            particles(ParticleTypes.HAPPY_VILLAGER, count = 2) {
                val halfWidth = width.toDouble() / 2.0
                it.add(
                    Random.nextDouble(-halfWidth, halfWidth),
                    Random.nextDouble(0.0, height.toDouble()),
                    Random.nextDouble(-halfWidth, halfWidth)
                )
            }
        }

        val customer = customer
        if (customer == null) {
            logger.warn("Player traded with {}'s stand entity, but no customer?", owner.nameForScoreboard)
        } else {
            logger.info("Player {} traded with {}'s stand entity", customer.nameForScoreboard, owner.nameForScoreboard)

            val soldStack = offer.sellItem
            val soldBlock = getBlock(soldStack)
            val soldBlockText = soldBlock.name.copy().formatted(Formatting.GREEN)

            if (owner === customer) {
                owner.sendMessage(Text.empty().append("You bought ").append(soldBlockText).append(" from your spectre"))
            } else {
                owner.sendMessage(Text.empty().append(customer.displayName).append(" bought ").append(soldBlockText).append(" from your spectre"))
                customer.sendMessage(Text.empty().append("You bought ").append(soldBlockText).append(" from ").append(owner.displayName).append("'s spectre"))
            }
        }

        DB
            .perform { conn, actions ->
                if (!actions.incrementPlayerLevel(owner.uuid)) {
                    logger.warn("Player {} failed to increment level?", owner.nameForScoreboard)
                }
                actions.getPlayerLevel(owner.uuid)
            }
            .then { level ->
                if (level != null) {
                    owner.properties.knownLevel = level
                    logger.info("Player {} incremented level to {}", owner.nameForScoreboard, level)
                } else {
                    logger.warn("Player {} failed to get level?", owner.nameForScoreboard)
                }
            }
    }

    override fun onSellingItem(stack: ItemStack) {
    }

    fun startTradingWith(player: PlayerEntity): Promise<Unit> {
        logger.info("Player {} opening {}'s stand entity", player.nameForScoreboard, owner.nameForScoreboard)
        return run {
            setCustomer(player)
            updateAndSendOffers(player).then {
                play(SoundEvents.BLOCK_BARREL_OPEN, volume = 0.5, pitch = Random.nextDouble(0.8, 1.2))
            }
        }
    }

    private val interactionsInProgress = HashSet<UUID>()

    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (
            player.currentScreenHandler === player.playerScreenHandler &&
            interactionsInProgress.add(player.uuid)
        ) {
            startTradingWith(player).finally { _, _ ->
                interactionsInProgress.remove(player.uuid)
                emitGameEvent(GameEvent.ENTITY_INTERACT, player)
            }
            return ActionResult.SUCCESS
        }
        return ActionResult.PASS
    }

    override fun tick() {
        super.tick()

        if (owner.world !== world || !owner.isAlive) {
            remove(RemovalReason.KILLED)
        }

        if (!owner.isSneaking && owner !== customer && owner.uuid !in interactionsInProgress) {
            setPosition(
                owner.pos
                    .offset(Direction.UP, 0.75)
                    .add(Vec3d(-0.75, 0.0, -0.75).rotateY((360.0F - owner.yaw) * MathHelper.RADIANS_PER_DEGREE))
            )
            setRotation(owner.yaw, 0.0F)
            setVelocity(0.0, 0.0, 0.0)
        }

        if (!hasStatusEffect(StatusEffects.INVISIBILITY)) {
            addStatusEffect(StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, true, false))
        }
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        return false
    }
    override fun move(movementType: MovementType, movement: Vec3d) {
    }

    override fun getArmorItems(): Iterable<ItemStack> {
        return emptyList()
    }
    override fun getEquippedStack(slot: EquipmentSlot): ItemStack {
        return ItemStack.EMPTY
    }
    override fun equipStack(slot: EquipmentSlot, stack: ItemStack) {
    }
    override fun getMainArm(): Arm {
        return Arm.LEFT
    }

    override fun setOffersFromServer(offers: TradeOfferList) {
    }
    override fun setExperienceFromServer(experience: Int) {
    }

    override fun getExperience(): Int {
        return 0
    }

    override fun isClient() = world.isClient()
    override fun isLeveledMerchant(): Boolean = true

}
