package dev.foxgirl.cminus

import net.minecraft.block.Block
import net.minecraft.entity.player.PlayerEntity

interface BlockOwner {
    val knownOwnedBlocks: MutableSet<Block>
}

val PlayerEntity.knownOwnedBlocks: MutableSet<Block> get() = (this as BlockOwner).knownOwnedBlocks
