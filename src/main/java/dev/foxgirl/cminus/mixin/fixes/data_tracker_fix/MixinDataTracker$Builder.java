package dev.foxgirl.cminus.mixin.fixes.data_tracker_fix;

import dev.foxgirl.cminus.StandEntity;
import dev.foxgirl.cminus.util.FakeDataTrackerEntry;
import net.minecraft.entity.data.DataTracked;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataTracker.Builder.class)
public abstract class MixinDataTracker$Builder {

    @Shadow @Final private DataTracked entity;
    @Shadow @Final private DataTracker.Entry<?>[] entries;

    @Inject(method = "build", at = @At("HEAD"))
    private void injected$build(CallbackInfoReturnable<DataTracker> info) {
        // Fill missing entries with FakeDataTrackerEntry instances for our
        // custom StandEntity class
        if (entity.getClass() == StandEntity.class) {
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] == null) {
                    entries[i] = new FakeDataTrackerEntry(i);
                }
            }
        }
    }

}
