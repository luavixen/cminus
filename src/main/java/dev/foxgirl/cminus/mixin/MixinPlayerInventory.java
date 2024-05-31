package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class MixinPlayerInventory {

    @Shadow @Final
    private PlayerEntity player;

    @Unique
    private ItemStack currentlyInsertingStack = null;

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void injected$insertStack$0(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> info) {
        currentlyInsertingStack = stack.copy();
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"))
    private void injected$insertStack$1(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ()) {
            CMinusKt.handlePlayerInventoryAdd((ServerPlayerEntity) player, currentlyInsertingStack);
        }
        currentlyInsertingStack = null;
    }

    @Inject(method = "setStack(ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void injected$setStack(int slot, ItemStack stack, CallbackInfo info) {
        CMinusKt.handlePlayerInventoryAdd((ServerPlayerEntity) player, stack);
    }

}
