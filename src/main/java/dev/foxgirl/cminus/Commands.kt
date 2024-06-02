package dev.foxgirl.cminus

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

fun onCommandRegistration(dispatcher: CommandDispatcher<ServerCommandSource>, registry: CommandRegistryAccess, environment: CommandManager.RegistrationEnvironment) {

    dispatcher.register(literal("buy").executes { ctx ->
        val player = ctx.source.entity as? ServerPlayerEntity
        if (player != null) {
            player.properties.standEntity?.startTradingWith(player)
            return@executes 1
        } else {
            return@executes 0
        }
    })

    dispatcher.register(literal("stand").also {
        for (kind in StandKind.entries) {
            it.then(literal(kind.name.lowercase()).executes { ctx ->
                val player = ctx.source.entity as? ServerPlayerEntity
                if (player != null) {
                    DB
                        .perform { conn, actions ->
                            actions.setPlayerStand(player.uuid, kind.name)
                        }
                        .then {
                            player.properties.knownStand = kind.name

                            logger.info("Player {} has set their stand kind to {}", player.nameForScoreboard, kind.name)
                            player.sendMessage(Text.literal("You have set your stand kind to ").append(kind.entityType.name.copy().formatted(Formatting.GREEN)))
                        }
                    return@executes 1
                } else {
                    return@executes 0
                }
            })
        }
    })

}
