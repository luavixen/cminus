package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity {

    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void injected$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (!CMinusKt.handlePlayerDamage((ServerPlayerEntity) (Object) this, amount, source)) info.cancel();
    }

}
