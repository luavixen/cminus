package dev.foxgirl.cminus.mixin.fixes.data_tracker_fix;

import dev.foxgirl.cminus.StandEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.util.collection.Class2IntMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DataTracker.class)
public abstract class MixinDataTracker {

    @Shadow @Final
    private static Class2IntMap CLASS_TO_LAST_ID;

    static {
        // Give StandEntity 30 data tracker entries, the missing entries will
        // be filled with FakeDataTrackerEntry instances by
        // MixinDataTracker$Builder
        for (int i = 0; i < 30; i++) CLASS_TO_LAST_ID.put(StandEntity.class);
    }

}
