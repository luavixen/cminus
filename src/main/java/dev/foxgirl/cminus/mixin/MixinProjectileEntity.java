package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.util.EntitiesKt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class MixinProjectileEntity extends Entity {

    private MixinProjectileEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onEntityHit(Lnet/minecraft/util/hit/EntityHitResult;)V", at = @At("RETURN"))
    private void injected$onEntityHit(EntityHitResult hitResult, CallbackInfo info) {
        if (getType() == EntityType.SNOWBALL) {
            applyKnockbackToHitEntity(hitResult, 4.0);
        }
        if (getType() == EntityType.EGG) {
            applyKnockbackToHitEntity(hitResult, -4.0);
        }
    }

    @Unique
    private void applyKnockbackToHitEntity(EntityHitResult hitResult, double strength) {
        var target = hitResult.getEntity();

        float pushYaw = getYaw() * 0.017453292F;
        float pushX = -MathHelper.sin(pushYaw);
        float pushZ = -MathHelper.cos(pushYaw);

        if (strength <= 0) {
            pushX = 0 - pushX;
            pushZ = 0 - pushZ;
            strength = Math.abs(strength);
        }

        EntitiesKt.applyKnockback(target, strength, pushX, pushZ, true);
    }

}
