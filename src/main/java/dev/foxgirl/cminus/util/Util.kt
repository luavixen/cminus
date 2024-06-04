package dev.foxgirl.cminus.util

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

fun getItemID(item: Item): Identifier = Registries.ITEM.getId(item)
fun getBlockID(block: Block): Identifier = Registries.BLOCK.getId(block)

fun getBlock(stack: ItemStack): Block = getBlock(stack.item)
fun getBlock(item: Item): Block = getBlock(getItemID(item))
fun getBlock(id: Identifier): Block = Registries.BLOCK.get(id)

inline fun lazyToString(crossinline block: () -> String): Any {
    return object {
        private var string: String? = null
        override fun toString(): String {
            synchronized(this) {
                if (string == null) {
                    string = block()
                }
                return string!!
            }
        }
    }
}
