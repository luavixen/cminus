package dev.foxgirl.cminus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.foxgirl.cminus.SpecialItemsKt;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RangedWeaponItem.class)
public abstract class MixinRangedWeaponItem {

    @ModifyVariable(
        method = "load(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/LivingEntity;)Ljava/util/List;",
        at = @At("STORE"), ordinal = 1
    )
    private static int injected$load$j(int j, @Local(ordinal = 0) int i) {
        return i == 0 ? 1 : i;
    }

    @Shadow
    private void shoot(LivingEntity shooter, ProjectileEntity projectile, int index, float speed, float divergence, float yaw, @Nullable LivingEntity target) {
        throw new AssertionError();
    }

    @Redirect(
        method = "shootAll(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/entity/LivingEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/RangedWeaponItem;shoot(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/projectile/ProjectileEntity;IFFFLnet/minecraft/entity/LivingEntity;)V"
        )
    )
    private void injected$shootAll$shoot(
        RangedWeaponItem rangedWeaponItem,
        LivingEntity shooter, ProjectileEntity projectile, int index, float speed, float divergence, float yaw, @Nullable LivingEntity target,
        @Local(ordinal = 0, argsOnly = true) ItemStack stack
    ) {
        if ("supershot_crossbow".equals(SpecialItemsKt.getSpecialItemID(stack))) {
            shoot(shooter, projectile, index, speed, divergence, yaw * 10.0F, target);
        } else {
            shoot(shooter, projectile, index, speed, divergence, yaw, target);
        }
    }

}
