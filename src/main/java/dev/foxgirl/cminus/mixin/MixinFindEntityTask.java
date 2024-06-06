package dev.foxgirl.cminus.mixin;

import dev.foxgirl.cminus.StandEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryQueryResult;
import net.minecraft.entity.ai.brain.task.FindEntityTask;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(value = FindEntityTask.class, priority = 999_999_999)
@SuppressWarnings("rawtypes")
public abstract class MixinFindEntityTask {

    @Shadow
    private static boolean method_46960(
        TaskTriggerer.TaskContext context,
        MemoryQueryResult memoryQueryResult1,
        Predicate predicate1,
        Predicate predicate2,
        int value1,
        MemoryQueryResult memoryQueryResult2,
        MemoryQueryResult memoryQueryResult3,
        MemoryQueryResult memoryQueryResult4,
        float value2,
        int value3,
        ServerWorld serverWorld,
        LivingEntity livingEntity,
        long time
    ) {
        throw new AssertionError();
    }

    @Unique
    private static boolean currentlyExecutingEvilMethod = false;

    @Inject(
        method = "method_46960(Lnet/minecraft/entity/ai/brain/task/TaskTriggerer$TaskContext;Lnet/minecraft/entity/ai/brain/MemoryQueryResult;Ljava/util/function/Predicate;Ljava/util/function/Predicate;ILnet/minecraft/entity/ai/brain/MemoryQueryResult;Lnet/minecraft/entity/ai/brain/MemoryQueryResult;Lnet/minecraft/entity/ai/brain/MemoryQueryResult;FILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;J)Z",
        at = @At("HEAD"), cancellable = true
    )
    private static void injected$method_46960(
        TaskTriggerer.TaskContext context,
        MemoryQueryResult memoryQueryResult1,
        Predicate predicate1,
        Predicate predicate2,
        int value1,
        MemoryQueryResult memoryQueryResult2,
        MemoryQueryResult memoryQueryResult3,
        MemoryQueryResult memoryQueryResult4,
        float value2,
        int value3,
        ServerWorld serverWorld,
        LivingEntity livingEntity,
        long time,
        CallbackInfoReturnable<Boolean> info
    ) {
        if (!currentlyExecutingEvilMethod) {
            boolean result;
            try {
                currentlyExecutingEvilMethod = true;
                if (livingEntity == null || livingEntity.getClass() == StandEntity.class) {
                    result = false;
                } else {
                    result = method_46960(
                        context,
                        memoryQueryResult1,
                        predicate1,
                        predicate2,
                        value1,
                        memoryQueryResult2,
                        memoryQueryResult3,
                        memoryQueryResult4,
                        value2,
                        value3,
                        serverWorld,
                        livingEntity,
                        time
                    );
                }
            } catch (ClassCastException ignored) {
                result = false;
            } finally {
                currentlyExecutingEvilMethod = false;
            }
            info.setReturnValue(result);
        }
    }

}
