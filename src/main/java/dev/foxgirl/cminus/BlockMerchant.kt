package dev.foxgirl.cminus

import net.minecraft.block.Block
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.village.SimpleMerchant
import net.minecraft.village.TradeOffer
import net.minecraft.village.TradedItem

class BlockMerchant(player: PlayerEntity) : SimpleMerchant(player) {

    fun updateAndSendOffers() {
        listBlocksForPlayer(customer!!) { player, blocks ->
            for (block in blocks) {
                val buyItem = TradedItem(Items.EMERALD, 16)
                val sellItem = ItemStack(block.asItem(), 64)
                offers.add(TradeOffer(buyItem, sellItem, Int.MAX_VALUE, 0, 0.05F))
            }
            sendOffers(player, Text.empty().append(player.name).append("'s Stand"), 0)
        }
    }

    override fun getYesSound(): SoundEvent {
        return SoundEvents.ENTITY_FOX_AMBIENT
    }

}

private fun listBlocksForPlayer(player: PlayerEntity, callback: (PlayerEntity, List<Block>) -> Unit) {
    DB
        .acquire { conn, actions -> actions.listBlocks(player.uuid).toList() }
        .thenAcceptAsync({ records -> callback(player, records.map { getBlock(Identifier(it.block)) }.sortedBy { it.name.string }) }, server)
}
