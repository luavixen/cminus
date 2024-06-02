package dev.foxgirl.cminus

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.block.Block
import net.minecraft.entity.player.PlayerEntity

class PlayerProperties {

    val knownOwnedBlocks: MutableSet<Block> = ObjectOpenHashSet()

    var knownStand: String = ""
    var knownLevel: Int = 0

    var standEntity: StandEntity? = null

}

interface PlayerPropertiesAccess {
    fun getCMinusPlayerProperties(): PlayerProperties
}

val PlayerEntity.properties get() = (this as PlayerPropertiesAccess).getCMinusPlayerProperties()
