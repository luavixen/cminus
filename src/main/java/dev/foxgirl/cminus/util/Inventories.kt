package dev.foxgirl.cminus.util

import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack

interface MutableInventoryList : MutableList<ItemStack> {
    val inventory: Inventory
}

private class MutableInventoryListImpl(override val inventory: Inventory) : AbstractMutableList<ItemStack>(), MutableInventoryList {

    override val size get() = inventory.size()

    override fun get(index: Int): ItemStack {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        }
        return inventory.getStack(index) ?: ItemStack.EMPTY
    }

    override fun set(index: Int, element: ItemStack): ItemStack {
        return get(index).also { inventory.setStack(index, element) }
    }

    override fun add(index: Int, element: ItemStack) {
        throw UnsupportedOperationException("Cannot change size of inventory")
    }
    override fun removeAt(index: Int): ItemStack {
        throw UnsupportedOperationException("Cannot change size of inventory")
    }

}

fun Inventory.asList(): MutableInventoryList = MutableInventoryListImpl(this)
