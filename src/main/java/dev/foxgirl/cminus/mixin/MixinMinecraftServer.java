package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.DB;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "shutdown", at = @At("TAIL"))
    private void injected$shutdown(CallbackInfo info) {
        DB.INSTANCE.close();
    }

}
