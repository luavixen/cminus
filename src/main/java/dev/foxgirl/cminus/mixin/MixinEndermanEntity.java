package dev.foxgirl.cminus.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanEntity.class)
public abstract class MixinEndermanEntity extends HostileEntity {

    private MixinEndermanEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void injected$dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops, CallbackInfo info) {
        dropStack(new ItemStack(Items.EMERALD_BLOCK, 1));
    }

}
