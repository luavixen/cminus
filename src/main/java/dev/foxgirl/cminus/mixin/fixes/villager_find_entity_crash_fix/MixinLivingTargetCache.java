package dev.foxgirl.cminus.mixin.fixes.villager_find_entity_crash_fix;

import dev.foxgirl.cminus.StandEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingTargetCache.class)
@SuppressWarnings("rawtypes")
public abstract class MixinLivingTargetCache {

    @ModifyVariable(
        method = "Lnet/minecraft/entity/ai/brain/LivingTargetCache;<init>(Lnet/minecraft/entity/LivingEntity;Ljava/util/List;)V",
        at = @At("HEAD"), ordinal = 0
    )
    private static List injected$__init__(List entities) {
        var list = new ArrayList<LivingEntity>(entities.size());
        for (var entity : entities) {
            if (entity instanceof LivingEntity && entity.getClass() != StandEntity.class) {
                list.add((LivingEntity) entity);
            }
        }
        return list;
    }

}
