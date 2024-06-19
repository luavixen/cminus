package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EnderDragonEntity.class)
public abstract class MixinEnderDragonEntity extends MobEntity {

    private MixinEnderDragonEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyVariable(
        method = "damagePart(Lnet/minecraft/entity/boss/dragon/EnderDragonPart;Lnet/minecraft/entity/damage/DamageSource;F)Z",
        at = @At("HEAD"), ordinal = 0
    )
    private float injected$damagePart(float oldAmount) {
        float newAmount = Math.min(oldAmount, 30.0F);
        CMinusKt.getLogger().debug(
            "EnderDragonEntity#damagePart oldAmount: {}, newAmount: {}",
            oldAmount, newAmount
        );
        return newAmount;
    }

}
