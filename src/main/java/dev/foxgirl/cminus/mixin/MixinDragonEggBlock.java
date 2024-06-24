package dev.foxgirl.cminus.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DragonEggBlock.class)
public class MixinDragonEggBlock {

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void inject$teleport(BlockState state, World world, BlockPos pos, CallbackInfo info) {
        info.cancel();
    }

}
