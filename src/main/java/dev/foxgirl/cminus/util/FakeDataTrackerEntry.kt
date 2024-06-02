package dev.foxgirl.cminus.util

import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry

class FakeDataTrackerEntry(id: Int)
    : DataTracker.Entry<Boolean>(TrackedData(id, TrackedDataHandlerRegistry.BOOLEAN), false)
{

    override fun set(value: Boolean?) {
    }

    override fun setDirty(dirty: Boolean) {
    }

    override fun isDirty(): Boolean {
        return false
    }
    override fun isUnchanged(): Boolean {
        return true
    }

}
