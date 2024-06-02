package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import dev.foxgirl.cminus.PlayerProperties;
import dev.foxgirl.cminus.PlayerPropertiesAccess;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity implements PlayerPropertiesAccess {

    @Unique
    private final PlayerProperties cminusPlayerProperties = new PlayerProperties();

    public PlayerProperties getCMinusPlayerProperties() {
        return cminusPlayerProperties;
    }

    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void injected$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (!CMinusKt.handlePlayerDamage((ServerPlayerEntity) (Object) this, source, amount)) info.cancel();
    }

    @Redirect(method = "increaseTravelMotionStats(DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isOnGround()Z"))
    private boolean injected$increaseTravelMotionStats$isOnGround(ServerPlayerEntity player) {
        if (CMinusKt.isInGameMode(player) && CMinusKt.isFlying(player)) {
            return true;
        } else {
            return player.isOnGround();
        }
    }

}
