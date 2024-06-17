package dev.foxgirl.cminus.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingScreenHandler.class)
public abstract class MixinCraftingScreenHandler extends ScreenHandler {

    private MixinCraftingScreenHandler() {
        super(null, 0);
    }

    @Inject(
        method = "quickMove(Lnet/minecraft/entity/player/PlayerEntity;I)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"), cancellable = true
    )
    private void injected$quickMove(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> info) {
        var slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack() && slot.getStack().isOf(Items.END_PORTAL_FRAME)) {
            info.setReturnValue(ItemStack.EMPTY);
        }
    }

}
