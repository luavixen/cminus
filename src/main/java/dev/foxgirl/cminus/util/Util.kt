package dev.foxgirl.cminus.util

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemConvertible
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

fun getItemID(item: Item): Identifier = Registries.ITEM.getId(item)
fun getBlockID(block: Block): Identifier = Registries.BLOCK.getId(block)

fun getBlock(stack: ItemStack): Block = getBlock(stack.item)
fun getBlock(item: Item): Block = getBlock(getItemID(item))
fun getBlock(id: Identifier): Block = Registries.BLOCK.get(id)

fun stackOf(): ItemStack = ItemStack.EMPTY
fun stackOf(item: ItemConvertible, count: Int = 1): ItemStack = ItemStack(item, count)

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

fun String.truncate(maxLength: Int, postfix: String = "\u2026"): String {
    return if (length > maxLength) substring(0, length - 1) + postfix else this
}

private class ProxyList<E>(private val lists: Array<out MutableList<E>>) : AbstractMutableList<E>() {

    override val size get() = lists.sumOf { it.size }

    override fun get(index: Int): E {
        var i = index
        for (list in lists) {
            if (i < list.size) {
                return list.get(i)
            }
            i -= list.size
        }
        throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
    }

    override fun set(index: Int, element: E): E {
        var i = index
        for (list in lists) {
            if (i < list.size) {
                return list.set(i, element)
            }
            i -= list.size
        }
        throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
    }

    override fun add(index: Int, element: E) {
        throw UnsupportedOperationException("Cannot change size of proxy list")
    }
    override fun removeAt(index: Int): E {
        throw UnsupportedOperationException("Cannot change size of proxy list")
    }

}

fun <E> proxyListOf(vararg lists: MutableList<E>): MutableList<E> = ProxyList(lists)
