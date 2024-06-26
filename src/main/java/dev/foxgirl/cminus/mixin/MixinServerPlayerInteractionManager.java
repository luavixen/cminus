package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import dev.foxgirl.cminus.SpecialItemsKt;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.minecraft.block.Block.getRawIdFromState;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class MixinServerPlayerInteractionManager {

    @Shadow @Final private ServerPlayerEntity player;
    @Shadow private ServerWorld world;

    @Shadow private boolean mining;
    @Shadow private BlockPos miningPos;

    @Shadow private int tickCounter;
    @Shadow private int startMiningTime;

    @Shadow
    private boolean tryBreakBlock(BlockPos pos) {
        throw new AssertionError();
    }

    @Unique
    private int lastFastBreakingTime;

    @Unique
    private boolean canPlayerInstantMine() {
        return CMinusKt.isInGameMode(player, false) && CMinusKt.isInstantMiningActive(player);
    }

    @Unique
    private boolean usingCorrectTool(BlockState state) {
        var block = state.getBlock();
        if (block == Blocks.BARRIER || block == Blocks.BEDROCK || block == Blocks.END_PORTAL_FRAME || block == Blocks.DRAGON_EGG) {
            if ("barrier_breaker".equals(SpecialItemsKt.getSpecialItemID(player.getStackInHand(player.getActiveHand())))) {
                return true;
            }
        }
        return player.getInventory().getBlockBreakingSpeed(state) >= 2.0F;
    }
    @Unique
    private boolean shouldBreakNow() {
        if (tickCounter - startMiningTime >= 2) return true;
        if (tickCounter - lastFastBreakingTime > 8) return true;
        return false;
    }

    @Unique
    private void breakBlock(BlockPos pos, BlockState state) {
        if (tryBreakBlock(pos)) {
            player.networkHandler.sendPacket(new WorldEventS2CPacket(WorldEvents.BLOCK_BROKEN, pos, getRawIdFromState(state), false));
        }
    }

    @Inject(method = "update()V", at = @At("TAIL"))
    private void injected$update(CallbackInfo info) {
        if (mining && canPlayerInstantMine()) {
            var state = world.getBlockState(miningPos);
            if (usingCorrectTool(state) && shouldBreakNow()) {
                lastFastBreakingTime = tickCounter;
                breakBlock(miningPos, state);
            }
        }
    }

}
