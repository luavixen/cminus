package dev.foxgirl.cminus

import com.mojang.brigadier.CommandDispatcher
import dev.foxgirl.cminus.util.asList
import dev.foxgirl.cminus.util.give
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockStateArgumentType
import net.minecraft.item.Items
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.minecraft.util.Formatting

fun onCommandRegistration(dispatcher: CommandDispatcher<ServerCommandSource>, registry: CommandRegistryAccess, environment: CommandManager.RegistrationEnvironment) {

    dispatcher.register(literal("buy").also {
        it.executes { ctx ->
            val player = ctx.source.entity as? ServerPlayerEntity
            if (player == null) {
                ctx.source.sendError(Text.of("You must be a player to use this command!"))
                return@executes 0
            } else {
                player.properties.standEntity?.startTradingWith(player)
                return@executes 1
            }
        }
        it.then(argument("block", BlockStateArgumentType.blockState(registry)).executes { ctx ->
            val player = ctx.source.entity as? ServerPlayerEntity
            if (player == null) {
                ctx.source.sendError(Text.of("You must be a player to use this command!"))
                return@executes 0
            }

            val block = BlockStateArgumentType.getBlockState(ctx, "block").blockState.block
            if (block in bannedBlocks) {
                ctx.source.sendError(Text.of("You can't buy this block!"))
                return@executes 0
            }

            val standEntity = player.properties.standEntity
            if (standEntity != null) {
                standEntity.prepareOffersFor(player).then {
                    val offerItem = block.asItem()
                    val offer = standEntity.offers.find { it.sellItem.item === offerItem }
                    if (offer == null) {
                        ctx.source.sendError(Text.of("You don't have this block!"))
                        return@then
                    }

                    val emeraldStacks = player.inventory.asList().filter { it.item === Items.EMERALD }

                    var emeraldCountRemaining = 16
                    val emeraldCount = emeraldStacks.sumOf { it.count }
                    if (emeraldCount < emeraldCountRemaining) {
                        ctx.source.sendError(Text.of("You don't have enough emeralds!"))
                        return@then
                    }

                    for (stack in emeraldStacks) {
                        if (emeraldCountRemaining <= 0) break
                        stack.decrement(stack.count.coerceAtMost(emeraldCountRemaining).also { emeraldCountRemaining -= it })
                    }

                    standEntity.trade(offer)
                    player.incrementStat(Stats.TRADED_WITH_VILLAGER)
                    player.give(offer.sellItem.copy())
                }.finally { _, _ ->
                    standEntity.resetCustomer()
                }
            }

            return@executes 1
        })
    })

    dispatcher.register(literal("spectre").also {
        it.executes { ctx ->
            val player = ctx.source.entity as? ServerPlayerEntity
            if (player != null) {
                player.sendMessage(Text.literal("Use this command to change your spectre, like ").append(Text.literal("/spectre <mob>").formatted(Formatting.GREEN)))
                return@executes 1
            } else {
                return@executes 0
            }
        }
        for (kind in StandKind.entries) {
            it.then(literal(kind.name.lowercase()).executes { ctx ->
                val player = ctx.source.entity as? ServerPlayerEntity
                if (player != null) {
                    DB.perform { conn, actions ->
                        actions.setPlayerStand(player.uuid, kind.name)
                    }.then {
                        player.properties.knownStand = kind.name

                        logger.info("Player {} has set their stand kind to {}", player.nameForScoreboard, kind.name)
                        player.sendMessage(Text.literal("You have set your spectre to ").append(kind.entityType.name.copy().formatted(Formatting.GREEN)))
                    }
                    return@executes 1
                } else {
                    return@executes 0
                }
            })
        }
    })

}
