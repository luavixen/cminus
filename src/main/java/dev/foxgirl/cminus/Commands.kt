package dev.foxgirl.cminus

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.foxgirl.cminus.util.*
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockStateArgumentType
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.entity.boss.dragon.EnderDragonEntity
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import java.util.*

fun onCommandRegistration(dispatcher: CommandDispatcher<ServerCommandSource>, registry: CommandRegistryAccess, environment: CommandManager.RegistrationEnvironment) {

    dispatcher.register(literal("buy").also {
        it.executesHandler(requiresPlayer = true) { ctx, source, player ->
            player!!.extraFields.standEntity?.startTradingWith(player)
            true
        }
        it.then(argument("block", BlockStateArgumentType.blockState(registry)).executesHandler(requiresPlayer = true) { ctx, source, player ->
            require(player != null)

            val block = BlockStateArgumentType.getBlockState(ctx, "block").blockState.block
            if (!canTradeBlock(block)) {
                source.sendError(Text.of("You can't buy this block!"))
                return@executesHandler false
            }

            val standEntity = player.extraFields.standEntity
            if (standEntity != null) {
                standEntity.prepareOffersFor(player).then {
                    val offerItem = block.asItem()
                    val offer = standEntity.offers.find { it.sellItem.item === offerItem }
                    if (offer == null) {
                        source.sendError(Text.of("You don't have this block!"))
                        return@then
                    }

                    val emeraldStacks = player.inventory.asList().filter { it.item === standCurrencyItem }

                    var emeraldCountRemaining: Int = offer.firstBuyItem.count
                    val emeraldCount = emeraldStacks.sumOf { it.count }
                    if (emeraldCount < emeraldCountRemaining) {
                        source.sendError(Text.of("You don't have enough emeralds!"))
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

            true
        })
    })

    dispatcher.register(literal("spectre").also {
        it.executesHandler { ctx, source, player ->
            source.sendMessage(Text.literal("Use this command to change your spectre, like ").append(Text.literal("/spectre <mob>").formatted(Formatting.GREEN)))
            true
        }
        for (kind in StandKind.entries) {
            it.then(literal(kind.name.lowercase()).executesHandler(requiresPlayer = true) { ctx, source, player ->
                require(player != null)

                DB.perform { conn, actions ->
                    actions.setPlayerStand(player.uuid, kind.name)
                }.then {
                    player.extraFields.knownStand = kind.name

                    logger.info("Player {} has set their stand kind to {}", player.nameForScoreboard, kind.name)
                    player.sendMessage(Text.literal("You have set your spectre to ").append(kind.entityType.name.copy().formatted(Formatting.GREEN)))
                }

                true
            })
        }
    })

    dispatcher.register(literal("freeze").executesHandler(requiresPlayer = true) { ctx, source, player ->
        require(player != null)

        val standEntity = player.extraFields.standEntity
        if (standEntity == null) {
            ctx.source.sendError(Text.of("You don't have a spectre right now!"))
            return@executesHandler false
        }

        standEntity.isFixed = !standEntity.isFixed

        logger.debug("Player {} has set their spectre frozen state to {}", player.nameForScoreboard, standEntity.isFixed)
        player.sendMessage(Text.literal("Your spectre is now ").append(if (standEntity.isFixed) "frozen" else "unfrozen"))

        true
    })

    dispatcher.register(literal("instamine").executesHandler(requiresPlayer = true) { ctx, source, player ->
        require(player != null)

        player.extraFields.isInstantMiningActive = !player.extraFields.isInstantMiningActive

        logger.debug("Player {} has set their instant mining state to {}", player.nameForScoreboard, player.extraFields.isInstantMiningActive)
        player.sendMessage(Text.literal("Instant mining is now ").append(if (player.extraFields.isInstantMiningActive) "enabled" else "disabled"))

        true
    })

    dispatcher.register(literal("cminus").also {

        it.then(literal("level").also {

            it.then(literal("list").executesHandler(requiresOperator = true) { ctx, source, _ ->
                DB.perform { conn, actions ->
                    actions.listPlayers()
                }.then { players ->
                    source.sendMessage(Text.of("Players: " + players.joinToString(", ") { "${it.name} (${it.level / 16 + 10})" }))
                }
                true
            })

            it.then(literal("set").then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("level", IntegerArgumentType.integer(11)).executesHandler(requiresOperator = true) { ctx, source, _ ->
                val players = GameProfileArgumentType.getProfileArgument(ctx, "players")
                val level = IntegerArgumentType.getInteger(ctx, "level") * 16 - 160

                DB.perform { conn, actions ->
                    var updateCount = 0
                    for (player in players) {
                        if (actions.setPlayerLevel(player.id, level)) updateCount++
                    }
                    updateCount
                }.then { updateCount ->
                    source.sendMessage(Text.of("Updated level of $updateCount player(s)"))
                    logger.info("Set level to {} for updatedCount: {}, totalCount: {}, players: {}", level, updateCount, players.size, players.joinToString(", ") { it.name }.truncate(60))
                }

                true
            })))

            it.then(literal("increment").then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("amount", IntegerArgumentType.integer()).executesHandler(requiresOperator = true) { ctx, source, _ ->
                val players = GameProfileArgumentType.getProfileArgument(ctx, "players")
                val amount = IntegerArgumentType.getInteger(ctx, "amount") * 16

                DB.perform { conn, actions ->
                    var updateCount = 0
                    for (player in players) {
                        if (actions.incrementPlayerLevel(player.id, amount)) updateCount++
                    }
                    updateCount
                }.then { updateCount ->
                    source.sendMessage(Text.of("Updated level of $updateCount player(s)"))
                    logger.info("Incremented level by {} for updatedCount: {}, totalCount: {}, players: {}", amount, updateCount, players.size, players.joinToString(", ") { it.name }.truncate(60))
                }

                true
            })))

        })

        it.then(literal("trade").also {

            fun tradeUpdateCommand(name: String, message: String, action: (DB.Actions, UUID, String) -> Boolean) {
                it.then(literal(name).then(argument("players", GameProfileArgumentType.gameProfile()).then(argument("block", BlockStateArgumentType.blockState(registry)).executesHandler(requiresOperator = true) { ctx, source, _ ->
                    val players = GameProfileArgumentType.getProfileArgument(ctx, "players")
                    val block = getBlockID(BlockStateArgumentType.getBlockState(ctx, "block").blockState.block).toString()

                    DB.perform { conn, actions ->
                        var updateCount = 0
                        for (player in players) {
                            if (action(actions, player.id, block)) updateCount++
                        }
                        updateCount
                    }.then { updateCount ->
                        source.sendMessage(Text.of("$message for $block on $updateCount player(s)"))
                        logger.info("$message for $block on updatedCount: ${updateCount}, totalCount: ${players.size}, players: ${players.joinToString(", ") { it.name }.truncate(60)}")
                    }

                    true
                })))
            }

            tradeUpdateCommand("add", "Added trade") { actions, player, block ->
                actions.addBlock(player, block)
            }

            tradeUpdateCommand("remove", "Removed trade") { actions, player, block ->
                actions.removeBlock(player, block)
            }

            it.then(literal("clear").also {

                it.then(argument("players", GameProfileArgumentType.gameProfile()).executesHandler(requiresOperator = true) { ctx, source, _ ->
                    val players = GameProfileArgumentType.getProfileArgument(ctx, "players")

                    DB.perform { conn, actions ->
                        var updateCount = 0
                        for (player in players) {
                            if (actions.clearBlocks(player.id) > 0) updateCount++
                        }
                        updateCount
                    }.then { updateCount ->
                        source.sendMessage(Text.of("Cleared trades for $updateCount player(s)"))
                        logger.info("Cleared trades for updatedCount: ${updateCount}, totalCount: ${players.size}, players: ${players.joinToString(", ") { it.name }.truncate(60)}")
                    }

                    true
                })

            })

        })

        it.then(literal("play_egg_sequence").executesHandler { ctx, source, player ->
            Async.go {
                val world = Async.poll { server.getWorld(World.END) }
                val dragonFight = Async.poll { world.enderDragonFight }
                val dragonEntity = Async.poll { world.getEntity(dragonFight.dragonUuid) as? EnderDragonEntity }
                playEggSequence(world, dragonFight, dragonEntity)
            }
            true
        })
        it.then(literal("play_final_fight_sequence").executesHandler { ctx, source, player ->
            playFinalFightSequence()
            true
        })
        it.then(literal("flood_the_world").executesHandler { ctx, source, player ->
            floodTheWorld()
            true
        })

        it.then(literal("give_special_item").also {
            for ((id, stack) in specialItems) {
                it.then(literal(id).executesHandler(requiresPlayer = true) { ctx, source, player ->
                    require(player != null)

                    player.give(stack.copy())
                    player.sendMessage(Text.of("Gave you a special item: $id"))

                    true
                })
            }
        })

    })

}

fun <S : ServerCommandSource, T : ArgumentBuilder<S, T>> ArgumentBuilder<S, T>.executesHandler(
    requiresPlayer: Boolean = false,
    requiresOperator: Boolean = false,
    handler: (CommandContext<S>, S, ServerPlayerEntity?) -> Boolean,
): T {
    return executes { ctx ->
        val source = ctx.source
        val player = ctx.source.entity as? ServerPlayerEntity
        try {
            if (requiresPlayer && player == null) {
                source.sendError(Text.of("You must be a player to use this command!"))
                return@executes 0
            }
            if (requiresOperator && !source.hasPermissionLevel(2)) {
                source.sendError(Text.of("You don't have permission to use this command!"))
                return@executes 0
            }
            return@executes if (handler(ctx, source, player)) 1 else 0
        } catch (cause: Exception) {
            source.sendError(Text.of("Unexpected exception while executing command: ${cause.message ?: cause.javaClass.name}"))
            logger.error("Unexpected exception while executing command \"${ctx.input.truncate(40)}\"", cause)
            return@executes 0
        }
    }
}
