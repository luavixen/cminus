package dev.foxgirl.cminus.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderDragonFight.class)
public abstract class MixinEnderDragonFight {

    @Redirect(
        method = "dragonKilled(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"
        )
    )
    private boolean injected$dragonKilled$setBlockState(ServerWorld world, BlockPos pos, BlockState state) {
        return false;
    }

}
