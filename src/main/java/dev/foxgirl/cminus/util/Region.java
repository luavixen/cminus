package dev.foxgirl.cminus.util;


import kotlin.jvm.internal.ArrayIteratorKt;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Region implements Selection {

    public static final @NotNull Region EMPTY = new Region(
        new BlockPos(0, -64, 0),
        new BlockPos(0, -64, 0)
    );

    private final BlockPos start;
    private final BlockPos end;
    private final ChunkPos chunkStart;
    private final ChunkPos chunkEnd;
    private final Box box;

    public Region(@NotNull Vec3i a, @NotNull Vec3i b) {
        Objects.requireNonNull(a, "Argument 'a'");
        Objects.requireNonNull(b, "Argument 'b'");
        start = new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
        end = new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );
        chunkStart = new ChunkPos(start);
        chunkEnd = new ChunkPos(end);
        box = Box.enclosing(start, end.add(1, 1, 1));
    }

    public @NotNull BlockPos component1() {
        return start;
    }
    public @NotNull BlockPos component2() {
        return end;
    }

    public @NotNull BlockPos getStart() {
        return start;
    }
    public @NotNull BlockPos getEnd() {
        return end;
    }
    public @NotNull ChunkPos getChunkStart() {
        return chunkStart;
    }
    public @NotNull ChunkPos getChunkEnd() {
        return chunkEnd;
    }
    public @NotNull Box getBox() {
        return box;
    }

    public @NotNull Vec3d getCenter() {
        return new Vec3d(
            (double) ((long) start.getX() + (long) end.getX()) / 2.0 + 0.5,
            (double) ((long) start.getY() + (long) end.getY()) / 2.0 + 0.5,
            (double) ((long) start.getZ() + (long) end.getZ()) / 2.0 + 0.5
        );
    }

    public long getBlockCount() {
        return
            (long) (end.getX() - start.getX() + 1) *
                (long) (end.getY() - start.getY() + 1) *
                (long) (end.getZ() - start.getZ() + 1);
    }
    public long getChunkCount() {
        return
            (long) (chunkEnd.x - chunkStart.x + 1) *
                (long) (chunkEnd.z - chunkStart.z + 1);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return start.getX() <= x && end.getX() >= x
            && start.getY() <= y && end.getY() >= y
            && start.getZ() <= z && end.getZ() >= z;
    }

    public @NotNull Region offset(int x, int y, int z) {
        return new Region(start.add(x, y, z), end.add(x, y, z));
    }
    public @NotNull Region offset(@NotNull Vec3i pos) {
        return offset(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean equals(@Nullable Object other) {
        if (other == this) return true;
        return other instanceof Region otherRegion
            && start.equals(otherRegion.start)
            && end.equals(otherRegion.end);
    }

    public int hashCode() {
        return 31 * start.hashCode() + end.hashCode();
    }
    public @NotNull String toString() {
        return "Region{start=" + start + ", end=" + end + "}";
    }

    public static final class Set extends AbstractSet<Region> implements Selection {

        private final Region[] regions;

        public Set(@NotNull Region... elements) {
            this(Arrays.asList(elements));
        }
        public Set(@NotNull Collection<Region> collection) {
            regions = new LinkedHashSet<>(collection).toArray(new Region[0]);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            for (var region : regions) if (region.contains(x, y, z)) return true;
            return false;
        }

        @Override
        public int size() {
            return regions.length;
        }
        @Override
        public @NotNull Iterator<Region> iterator() {
            return ArrayIteratorKt.iterator(regions);
        }

    }

}
