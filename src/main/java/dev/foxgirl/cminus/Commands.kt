package dev.foxgirl.cminus

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

fun onCommandRegistration(dispatcher: CommandDispatcher<ServerCommandSource>, registry: CommandRegistryAccess, environment: CommandManager.RegistrationEnvironment) {

    dispatcher.register(literal("buy").executes { ctx ->
        val player = ctx.source.entity as? ServerPlayerEntity
        if (player != null) {
            BlockMerchant(player).updateAndSendOffers()
        }
        1
    })

}
