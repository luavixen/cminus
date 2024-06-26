package dev.foxgirl.cminus

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.passive.CatVariant
import net.minecraft.entity.passive.ParrotEntity
import net.minecraft.entity.passive.WolfVariants
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents.*

enum class StandKind(
    val entityType: EntityType<out LivingEntity>,
    val entitySound: SoundEvent? = null,
    val entityInitializer: (StandEntity) -> Unit = {},
) {

    ALLAY(EntityType.ALLAY, ENTITY_ALLAY_ITEM_GIVEN),
    ARMADILLO(EntityType.ARMADILLO, ENTITY_ARMADILLO_AMBIENT),
    AXOLOTL(EntityType.AXOLOTL, ENTITY_AXOLOTL_IDLE_AIR),
    BAT(EntityType.BAT, ENTITY_BAT_AMBIENT),
    BEE(EntityType.BEE, ENTITY_BEE_LOOP),
    BLAZE(EntityType.BLAZE, ENTITY_BLAZE_AMBIENT),
    CAMEL(EntityType.CAMEL, ENTITY_CAMEL_AMBIENT),
    CAT(EntityType.CAT, ENTITY_CAT_PURREOW),
    CAVE_SPIDER(EntityType.CAVE_SPIDER, ENTITY_SPIDER_AMBIENT),
    CHICKEN(EntityType.CHICKEN, ENTITY_CHICKEN_AMBIENT),
    COD(EntityType.COD, ENTITY_COD_AMBIENT),
    COW(EntityType.COW, ENTITY_COW_AMBIENT),
    CREEPER(EntityType.CREEPER, ENTITY_CREEPER_PRIMED),
    DOLPHIN(EntityType.DOLPHIN, ENTITY_DOLPHIN_AMBIENT),
    DONKEY(EntityType.DONKEY, ENTITY_DONKEY_AMBIENT),
    DROWNED(EntityType.DROWNED, ENTITY_DROWNED_AMBIENT),
    ENDERMAN(EntityType.ENDERMAN, ENTITY_ENDERMAN_AMBIENT),
    ENDERMITE(EntityType.ENDERMITE, ENTITY_ENDERMITE_AMBIENT),
    EVOKER(EntityType.EVOKER, ENTITY_EVOKER_AMBIENT),
    FOX(EntityType.FOX, ENTITY_FOX_AMBIENT),
    FROG(EntityType.FROG, ENTITY_FROG_AMBIENT),
    GLOW_SQUID(EntityType.GLOW_SQUID, ENTITY_GLOW_SQUID_AMBIENT),
    GOAT(EntityType.GOAT, ENTITY_GOAT_AMBIENT),
    HORSE(EntityType.HORSE, ENTITY_HORSE_AMBIENT),
    HUSK(EntityType.HUSK, ENTITY_HUSK_AMBIENT),
    ILLUSIONER(EntityType.ILLUSIONER, ENTITY_ILLUSIONER_AMBIENT),
    LLAMA(EntityType.LLAMA, ENTITY_LLAMA_AMBIENT),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, ENTITY_MAGMA_CUBE_SQUISH_SMALL),
    MOOSHROOM(EntityType.MOOSHROOM, ENTITY_COW_AMBIENT),
    MUEL(EntityType.MULE, ENTITY_MULE_AMBIENT),
    OCELOT(EntityType.OCELOT, ENTITY_OCELOT_AMBIENT),
    PANDA(EntityType.PANDA, ENTITY_PANDA_AMBIENT),
    PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT),
    PHANTOM(EntityType.PHANTOM, ENTITY_PHANTOM_AMBIENT),
    PIG(EntityType.PIG, ENTITY_PIG_AMBIENT),
    PIGLIN(EntityType.PIGLIN, ENTITY_PIGLIN_AMBIENT),
    PIGLIN_BRUTE(EntityType.PIGLIN_BRUTE, ENTITY_PIGLIN_BRUTE_AMBIENT),
    PILLAGER(EntityType.PILLAGER, ENTITY_PILLAGER_AMBIENT),
    PUFFERFISH(EntityType.PUFFERFISH, ENTITY_PUFFER_FISH_AMBIENT),
    RABBIT(EntityType.RABBIT, ENTITY_RABBIT_AMBIENT),
    SALMON(EntityType.SALMON, ENTITY_SALMON_AMBIENT),
    SHEEP(EntityType.SHEEP, ENTITY_SHEEP_AMBIENT),
    SILVERFISH(EntityType.SILVERFISH, ENTITY_SILVERFISH_AMBIENT),
    SKELETON(EntityType.SKELETON, ENTITY_SKELETON_AMBIENT),
    SKELETON_HORSE(EntityType.SKELETON_HORSE, ENTITY_SKELETON_HORSE_AMBIENT),
    SLIME(EntityType.SLIME, ENTITY_SLIME_SQUISH_SMALL),
    SNOW_GOLEM(EntityType.SNOW_GOLEM, ENTITY_SNOW_GOLEM_AMBIENT),
    SPIDER(EntityType.SPIDER, ENTITY_SPIDER_AMBIENT),
    SQUID(EntityType.SQUID, ENTITY_SQUID_AMBIENT),
    STRAY(EntityType.STRAY, ENTITY_STRAY_AMBIENT),
    STRIDER(EntityType.STRIDER, ENTITY_STRIDER_AMBIENT),
    TRADER_LLAMA(EntityType.TRADER_LLAMA, ENTITY_LLAMA_AMBIENT),
    TURTLE(EntityType.TURTLE, ENTITY_TURTLE_SHAMBLE),
    VEX(EntityType.VEX, ENTITY_VEX_AMBIENT),
    VILLAGER(EntityType.VILLAGER, ENTITY_VILLAGER_YES),
    VINDICATOR(EntityType.VINDICATOR, ENTITY_VINDICATOR_AMBIENT),
    WANDERING_TRADER(EntityType.WANDERING_TRADER, ENTITY_WANDERING_TRADER_YES),
    WITCH(EntityType.WITCH, ENTITY_WITCH_AMBIENT),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, ENTITY_WITHER_AMBIENT),
    WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT),
    ZOMBIE(EntityType.ZOMBIE, ENTITY_ZOMBIE_AMBIENT),
    ZOMBIE_VILLAGER(EntityType.ZOMBIE_VILLAGER, ENTITY_ZOMBIE_VILLAGER_AMBIENT),
    ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, ENTITY_ZOMBIFIED_PIGLIN_AMBIENT),

    TABBY_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.TABBY) }),
    BLACK_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.BLACK) }),
    RED_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.RED) }),
    SIAMESE_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.SIAMESE) }),
    BRITISH_SHORTHAIR_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.BRITISH_SHORTHAIR) }),
    CALICO_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.CALICO) }),
    PERSIAN_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.PERSIAN) }),
    RAGDOLL_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.RAGDOLL) }),
    WHITE_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.WHITE) }),
    JELLIE_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.JELLIE) }),
    ALL_BLACK_CAT(EntityType.CAT, ENTITY_CAT_PURREOW, { it.setVariantCat(CatVariant.ALL_BLACK) }),

    PALE_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.PALE) }),
    SPOTTED_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.SPOTTED) }),
    SNOWY_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.SNOWY) }),
    BLACK_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.BLACK) }),
    ASHEN_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.ASHEN) }),
    RUSTY_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.RUSTY) }),
    WOODS_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.WOODS) }),
    CHESTNUT_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.CHESTNUT) }),
    STRIPED_WOLF(EntityType.WOLF, ENTITY_WOLF_AMBIENT, { it.setVariantWolf(WolfVariants.STRIPED) }),

    RED_BLUE_PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT, { it.setVariantParrot(ParrotEntity.Variant.RED_BLUE) }),
    BLUE_PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT, { it.setVariantParrot(ParrotEntity.Variant.BLUE) }),
    GREEN_PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT, { it.setVariantParrot(ParrotEntity.Variant.GREEN) }),
    YELLOW_BLUE_PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT, { it.setVariantParrot(ParrotEntity.Variant.YELLOW_BLUE) }),
    GRAY_PARROT(EntityType.PARROT, ENTITY_PARROT_AMBIENT, { it.setVariantParrot(ParrotEntity.Variant.GRAY) }),

}
