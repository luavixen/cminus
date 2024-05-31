package dev.foxgirl.cminus.util;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;

public final class InventoryList extends AbstractList<ItemStack> implements RandomAccess {

    private final Inventory inventory;

    public InventoryList(@NotNull Inventory inventory) {
        Objects.requireNonNull(inventory, "Argument 'inventory'");
        this.inventory = inventory;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public @NotNull ItemStack get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds");
        }
        ItemStack stack = inventory.getStack(index);
        return stack != null ? stack : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack set(int index, @Nullable ItemStack newStack) {
        ItemStack oldStack = get(index);
        inventory.setStack(index, newStack != null ? newStack : ItemStack.EMPTY);
        return oldStack;
    }

}
