package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class MixinServerCommonNetworkHandler {

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
        at = @At("HEAD"), ordinal = 0
    )
    private Packet<?> injected$send$0(Packet<?> packet) {
        if ((Object) this instanceof ServerPlayNetworkHandler networkHandler) {
            return CMinusKt.handlePacket(networkHandler.getPlayer(), packet);
        }
        return packet;
    }

    @Inject(
        method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void injected$send$1(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo info) {
        if (packet == null) info.cancel();
    }

}
