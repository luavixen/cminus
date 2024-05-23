package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
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
        if (CMinusKt.isInGameMode(self) && !CMinusKt.isFlying(self)) info.cancel();
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

}
