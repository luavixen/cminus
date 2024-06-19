package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity {

    @Inject(method = "addExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void injected$addExhaustion(float exhaustion, CallbackInfo info) {
        var self = (PlayerEntity) (Object) this;
        if (CMinusKt.isInGameMode(self, false) && !CMinusKt.isFlying(self)) info.cancel();
    }

    @Redirect(
        method = "handleFallDamage(FFLnet/minecraft/entity/damage/DamageSource;)Z", at = @At(
            target = "Lnet/minecraft/entity/player/PlayerAbilities;allowFlying:Z",
            value = "FIELD", opcode = Opcodes.GETFIELD
        )
    )
    private boolean injected$handleFallDamage$allowFlying(PlayerAbilities abilities) {
        return CMinusKt.isInGameMode((PlayerEntity) (Object) this) ? false : abilities.allowFlying;
    }

    @Redirect(
        method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At(
            target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F",
            value = "INVOKE"
        )
    )
    private float injected$applyDamage$modifyAppliedDamage(PlayerEntity player, DamageSource source, float amount) {
        var damage = player.modifyAppliedDamage(source, amount);
        if (CMinusKt.isInGameMode(player)) {
            damage = damage / CMinusKt.getDamageDivisor(player);
        }
        return damage;
    }

    @Redirect(
        method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At(
            target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
            value = "INVOKE"
        )
    )
    private boolean injected$attack$damage(Entity entity, DamageSource source, float amount) {
        if (entity.damage(source, amount)) {
            CMinusKt.handlePlayerAttackAndDamageEntity((ServerPlayerEntity) (Object) this, entity, source, amount);
            return true;
        } else {
            return false;
        }
    }


}
