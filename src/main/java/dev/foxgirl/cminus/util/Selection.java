package dev.foxgirl.cminus.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;

public interface Selection {

    boolean contains(int x, int y, int z);

    default boolean contains(@NotNull Vec3i pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    default boolean contains(@NotNull Entity entity) {
        return contains(entity.getBlockPos());
    }

    default boolean excludes(int x, int y, int z) {
        return !contains(x, y, z);
    }

    default boolean excludes(@NotNull Vec3i pos) {
        return !contains(pos);
    }

    default boolean excludes(@NotNull Entity entity) {
        return !contains(entity);
    }

}
