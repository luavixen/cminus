package dev.foxgirl.cminus.mixin.fixes.golem_find_entity_crash_fix;

import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionedEntityCache.class)
public abstract class MixinSectionedEntityCache {

    @Shadow
    private void forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<?>> consumer) {
        throw new AssertionError();
    }

    @Unique
    private static boolean currentlyExecutingEvilMethod = false;

    @Inject(
        method = "forEachInBox(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void injected$forEachInBox(Box box, LazyIterationConsumer<EntityTrackingSection<?>> consumer, CallbackInfo info) {
        if (!currentlyExecutingEvilMethod) {
            try {
                currentlyExecutingEvilMethod = true;
                forEachInBox(box, consumer);
            } catch (ClassCastException ignored) {
            } finally {
                currentlyExecutingEvilMethod = false;
                info.cancel();
            }
        }
    }


}
