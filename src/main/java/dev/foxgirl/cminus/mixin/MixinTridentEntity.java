package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.SpecialItemsKt;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TridentEntity.class)
public abstract class MixinTridentEntity extends PersistentProjectileEntity {

    private MixinTridentEntity(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Redirect(
        method = "onEntityHit(Lnet/minecraft/util/hit/EntityHitResult;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;isThundering()Z"
        )
    )
    private boolean injected$onEntityHit$isThundering(World world) {
        if ("lightning_trident".equals(SpecialItemsKt.getSpecialItemID(getItemStack()))) {
            return true;
        }
        return world.isThundering();
    }

}
