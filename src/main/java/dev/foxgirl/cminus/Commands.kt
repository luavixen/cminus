package dev.foxgirl.cminus

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.foxgirl.cminus.util.asList
import dev.foxgirl.cminus.util.getBlockID
import dev.foxgirl.cminus.util.give
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockStateArgumentType
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.item.Items
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.*

fun onCommandRegistration(dispatcher: CommandDispatcher<ServerCommandSource>, registry: CommandRegistryAccess, environment: CommandManager.RegistrationEnvironment) {

    dispatcher.register(literal("buy").also {
        it.executes { ctx ->
            val player = ctx.source.entity as? ServerPlayerEntity
            if (player == null) {
                ctx.source.sendError(Text.of("You must be a player to use this command!"))
                return@executes 0
            } else {
                player.extraFields.standEntity?.startTradingWith(player)
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

            val standEntity = player.extraFields.standEntity
            if (standEntity != null) {
                standEntity.prepareOffersFor(player).then {
                    val offerItem = block.asItem()
                    val offer = standEntity.offers.find { it.sellItem.item === offerItem }
                    if (offer == null) {
                        ctx.source.sendError(Text.of("You don't have this block!"))
                        return@then
                    }

                    val emeraldStacks = player.inventory.asList().filter { it.item === Items.EMERALD }

                    var emeraldCountRemaining: Int = offer.firstBuyItem.count
                    val emeraldCount = emeraldStacks.sumOf { it.count }
                    if (emeraldCount < emeraldCountRemaining) {
                        ctx.source.sendError(Text.of("You don't have enough emeralds!"))
                        return@then
                    }

                    for (stack in emeraldStacks) {
                        if (emeraldCountRemaining <= 0) break
                        stack.decrement(stack.count.coerceAtMost(emeraldCountRemaining).also { emeraldCountRemaining -= it })
                    }
                    player.inventory.markDirty()

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
                        player.extraFields.knownStand = kind.name

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

    dispatcher.register(literal("freeze").executes { ctx ->
        val player = ctx.source.entity as? ServerPlayerEntity
        if (player == null) {
            ctx.source.sendError(Text.of("You must be a player to use this command!"))
            return@executes 0
        }

        val standEntity = player.extraFields.standEntity
        if (standEntity == null) {
            ctx.source.sendError(Text.of("You don't have a spectre right now!"))
            return@executes 0
        }

        standEntity.isFixed = !standEntity.isFixed
        player.sendMessage(Text.literal("Your spectre is now ").append(if (standEntity.isFixed) "frozen" else "unfrozen"))
        return@executes 1
    })

    dispatcher.register(literal("cminus").also {

        it.then(literal("level").also {

            it.then(literal("list").executes { ctx ->
                if (!ctx.source.hasPermissionLevel(2)) {
                    ctx.source.sendError(Text.of("You don't have permission to use this command!"))
                    return@executes 0
                }

                DB.perform { conn, actions ->
                    actions.listPlayers()
                }.then { players ->
                    ctx.source.sendMessage(Text.of("Players: " + players.joinToString(", ") { "${it.name} (${it.level / 16 + 10})" }))
                }

                return@executes 1
            })

            it.then(literal("set").then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("level", IntegerArgumentType.integer(11)).executes { ctx ->
                if (!ctx.source.hasPermissionLevel(2)) {
                    ctx.source.sendError(Text.of("You don't have permission to use this command!"))
                    return@executes 0
                }

                val players = GameProfileArgumentType.getProfileArgument(ctx, "player").map { profile -> profile.id }
                val level = IntegerArgumentType.getInteger(ctx, "level") * 16 - 160

                DB.perform { conn, actions ->
                    var updateCount = 0
                    for (player in players) {
                        if (actions.setPlayerLevel(player, level)) updateCount++
                    }
                    updateCount
                }.then { updateCount ->
                    ctx.source.sendMessage(Text.of("Updated level of $updateCount player(s)"))
                }

                return@executes 1
            })))

            it.then(literal("increment").then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("amount", IntegerArgumentType.integer()).executes { ctx ->
                if (!ctx.source.hasPermissionLevel(2)) {
                    ctx.source.sendError(Text.of("You don't have permission to use this command!"))
                    return@executes 0
                }

                val players = GameProfileArgumentType.getProfileArgument(ctx, "player").map { profile -> profile.id }
                val amount = IntegerArgumentType.getInteger(ctx, "amount") * 16

                DB.perform { conn, actions ->
                    var updateCount = 0
                    for (player in players) {
                        if (actions.incrementPlayerLevel(player, amount)) updateCount++
                    }
                    updateCount
                }.then { updateCount ->
                    ctx.source.sendMessage(Text.of("Updated level of $updateCount player(s)"))
                }

                return@executes 1
            })))

        })

        it.then(literal("trade").also {

            fun tradeUpdateCommand(name: String, message: String, action: (DB.Actions, UUID, String) -> Boolean) {
                it.then(literal(name).then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("block", BlockStateArgumentType.blockState(registry)).executes { ctx ->
                    if (!ctx.source.hasPermissionLevel(2)) {
                        ctx.source.sendError(Text.of("You don't have permission to use this command!"))
                        return@executes 0
                    }

                    val players = GameProfileArgumentType.getProfileArgument(ctx, "player").map { it.id }
                    val block = getBlockID(BlockStateArgumentType.getBlockState(ctx, "block").blockState.block).toString()

                    DB.perform { conn, actions ->
                        var updateCount = 0
                        for (player in players) {
                            if (action(actions, player, block)) updateCount++
                        }
                        updateCount
                    }.then { updateCount ->
                        ctx.source.sendMessage(Text.of("$message for $block on $updateCount player(s)"))
                    }

                    return@executes 1
                })))
            }

            tradeUpdateCommand("add", "Added trade") { actions, player, block ->
                actions.addBlock(player, block)
            }

            tradeUpdateCommand("remove", "Removed trade") { actions, player, block ->
                actions.removeBlock(player, block)
            }

            it.then(literal("clear").also {

                it.then(argument("players", GameProfileArgumentType.gameProfile()).executes { ctx ->
                    if (!ctx.source.hasPermissionLevel(2)) {
                        ctx.source.sendError(Text.of("You don't have permission to use this command!"))
                        return@executes 0
                    }

                    val players = GameProfileArgumentType.getProfileArgument(ctx, "player").map { it.id }

                    DB.perform { conn, actions ->
                        var updateCount = 0
                        for (player in players) {
                            if (actions.clearBlocks(player) > 0) updateCount++
                        }
                        updateCount
                    }.then { updateCount ->
                        ctx.source.sendMessage(Text.of("Cleared trades for $updateCount player(s)"))
                    }

                    return@executes 1
                })

            })

        })

    })

}
