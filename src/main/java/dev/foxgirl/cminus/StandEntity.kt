package dev.foxgirl.cminus

import dev.foxgirl.cminus.util.*
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

    private var customer: PlayerEntity? = null

    override fun getCustomer(): PlayerEntity? {
        return customer
    }
    override fun setCustomer(customer: PlayerEntity?) {
        this.customer = customer
    }

    fun resetCustomer() {
        setCustomer(null)
    }

    private val offers = TradeOfferList()

    override fun getOffers(): TradeOfferList {
        return offers
    }

    private var offersUpdatePromise: Promise<Unit>? = null
    private var offersLastUpdated: Int = -1000

    private fun updateOffersActual(): Promise<Unit> {
        return DB
            .perform { conn, actions ->
                actions.listBlocksByPlayer(owner.uuid)
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
        return updateOffers().then {
            setCustomer(player)
            sendOffers(player, Text.empty().append(owner.displayName).append("'s Spectre - Lv ${getPlayerLevel(owner)}"), 0)
        }
    }

    fun prepareOffersFor(player: PlayerEntity): Promise<Unit> {
        setCustomer(player)
        return updateOffers()
    }

    private val interactionsInProgress = HashSet<UUID>()

    private fun calculatePosition(): Vec3d {
        return owner.pos
            .offset(Direction.UP, 0.75)
            .add(Vec3d(-0.75, 0.0, -0.75).rotateY((360.0F - owner.yaw) * MathHelper.RADIANS_PER_DEGREE))
    }

    init {
        setInvulnerable(true)
        setSilent(true)

        setNoGravity(true)
        setNoDrag(true)
        noClip = true

        val pos = calculatePosition()
        refreshPositionAndAngles(pos.x, pos.y, pos.z, owner.yaw, owner.pitch)

        addStatusEffect(StatusEffectInstance(StatusEffects.INVISIBILITY, Int.MAX_VALUE, 0, false, false, false))

        val team = scoreboard.getTeam("cminus")
        if (team != null) {
            scoreboard.addScoreHolderToTeam(nameForScoreboard, team)
        } else {
            logger.warn("Failed to get team for stand entity")
        }

        kind.entityInitializer(this)

        logger.debug("Initialized new stand entity for {} of kind {}", owner.nameForScoreboard, kind)
    }

    override fun tick() {
        super.tick()

        if (owner.world !== world || !owner.isAlive || !isInGameMode(owner) || owner.properties.standEntity !== this) {
            if (owner.properties.standEntity === this) {
                owner.properties.standEntity = null
            }
            remove(RemovalReason.KILLED)
            logger.debug("Removed stand entity for {}", owner.nameForScoreboard)
            return
        }

        if (!owner.isSneaking && owner !== customer && owner.uuid !in interactionsInProgress) {
            setPosition(calculatePosition())
            setRotation(owner.yaw, 0.0F)
            setVelocity(0.0, 0.0, 0.0)
        }

        if (!hasStatusEffect(StatusEffects.INVISIBILITY)) {
            addStatusEffect(StatusEffectInstance(StatusEffects.INVISIBILITY, Int.MAX_VALUE, 0, false, false, false))
        }
    }

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

    override fun trade(offer: TradeOffer) {
        offer.use()

        play(getYesSound(), pitch = Random.nextDouble(0.8, 1.2))

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

        val stack = offer.sellItem
        val block = getBlock(stack)
        val blockID = getBlockID(block)
        val blockText = block.name.copy().formatted(Formatting.GREEN)

        val customer = customer
        if (customer == null) {
            logger.warn("Player traded with {}'s stand entity for {}, but no customer?", owner.nameForScoreboard, blockID)
        } else {
            logger.info("Player {} traded with {}'s stand entity for {}", customer.nameForScoreboard, owner.nameForScoreboard, blockID)

            if (owner !== customer) {
                owner.sendMessage(Text.empty().append(customer.displayName).append(" bought ").append(blockText).append(" from your spectre"))
                customer.sendMessage(Text.empty().append("You bought ").append(blockText).append(" from ").append(owner.displayName).append("'s spectre"))
            } else {
                owner.sendMessage(Text.empty().append("You bought ").append(blockText).append(" from your spectre"))
                DB.perform { conn, actions -> actions.useBlock(owner.uuid, blockID.toString()) }
            }
        }

        DB.perform { conn, actions ->
            val didIncrementLevel = actions.incrementPlayerLevel(owner.uuid)
            if (!didIncrementLevel) {
                logger.warn("Player {} failed to increment level?", owner.nameForScoreboard)
            }
            val level = actions.getPlayerLevel(owner.uuid)
            if (level == null) {
                logger.warn("Player {} failed to get level?", owner.nameForScoreboard)
            }
            level
        }.then { level ->
            if (level != null) {
                owner.properties.knownLevel = level
                logger.info("Player {} incremented level to {}", owner.nameForScoreboard, level)
            }
        }
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

    override fun onSellingItem(stack: ItemStack) {
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
