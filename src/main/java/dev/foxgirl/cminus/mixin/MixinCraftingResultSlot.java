package dev.foxgirl.cminus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CraftingResultSlot.class)
public abstract class MixinCraftingResultSlot {

    @Redirect(
        method = "onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/RecipeInputInventory;removeStack(II)Lnet/minecraft/item/ItemStack;"
        )
    )
    private ItemStack injected$onTakeItem$removeStack(
        RecipeInputInventory recipeInputInventory, int slot, int amount,
        @Local(ordinal = 0, argsOnly = true) ItemStack stack
    ) {
        if (stack.isOf(Items.END_PORTAL_FRAME)) {
            var inputStack = recipeInputInventory.getStack(slot);
            if (inputStack.isOf(Items.EMERALD_BLOCK)) {
                if (inputStack.getCount() > 32) {
                    inputStack.setCount(inputStack.getCount() - 32);
                    recipeInputInventory.setStack(slot, inputStack.copy());
                } else {
                    inputStack.setCount(0);
                    recipeInputInventory.setStack(slot, ItemStack.EMPTY);
                }
                recipeInputInventory.markDirty();
                return new ItemStack(Items.END_PORTAL_FRAME, 1);
            }
        }
        return recipeInputInventory.removeStack(slot, amount);
    }

}
