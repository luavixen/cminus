package dev.foxgirl.cminus.util

import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack

fun Inventory.asList(): MutableList<ItemStack> = InventoryList(this)
