package dev.foxgirl.cminus

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity

class PlayerExtraFields {

    val knownOwnedBlocks: MutableSet<Block> = ObjectOpenHashSet()

    var knownStand: String = ""
    var knownLevel: Int = 0

    fun isKnownStandUnset() = knownStand.isEmpty()
    fun isKnownLevelUnset() = knownLevel == 0
    fun isKnownStandOrLevelUnset() = isKnownStandUnset() || isKnownLevelUnset()

    var lastUsedBlock: Block = Blocks.AIR

    var standEntity: StandEntity? = null

    var isInstantMiningActive: Boolean = true

}

interface PlayerExtraFieldsAccess {
    fun getCminusExtraFields(): PlayerExtraFields
}

val PlayerEntity.extraFields get() = (this as PlayerExtraFieldsAccess).getCminusExtraFields()
