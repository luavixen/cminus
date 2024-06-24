package dev.foxgirl.cminus.util;

import dev.foxgirl.cminus.CMinusKt;
import kotlin.Unit;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class Editor {

    private Editor() {
    }

    @FunctionalInterface
    public interface Action {
        @Nullable BlockState apply(@NotNull BlockState state, int x, int y, int z);
    }

    @FunctionalInterface
    public interface Predicate {
        boolean test(@NotNull BlockState state);
    }

    public record Result(@NotNull BlockPos pos, @NotNull BlockState state) {
    }

    public static final class Queue {
        private final ArrayList<Operation> list = new ArrayList<>();
        private boolean consumed = false;

        private Queue() {
        }

        private void add(Operation operation) {
            synchronized (LOCK) {
                if (consumed) {
                    throw new IllegalStateException("Queue has been consumed, cannot enqueue new operations");
                }
                list.add(operation);
            }
        }

        private Operation[] consume() {
            consumed = true;
            return list.toArray(new Operation[0]);
        }

        /**
         * Enqueues an edit operation into this queue.
         * @param action Action to perform.
         * @return {@link Promise} that is resolved when the operation completes.
         * @throws IllegalStateException If the queue has already been consumed.
         * @throws NullPointerException If {@code action} is null.
         */
        public @NotNull Promise<@NotNull Unit> edit(@NotNull Action action) {
            Objects.requireNonNull(action, "Argument 'action'");
            var future = new CompletableFuture<Unit>();
            var operation = new EditOperation(future, action);
            add(operation);
            return new Promise<>(future);
        }

        /**
         * Enqueues a search operation into this queue for specific blocks, filtered by the predicate.
         * @param predicate Predicate to filter blocks by.
         * @return {@link Promise} that is resolved with a list of search results.
         * @throws IllegalStateException If the queue has already been consumed.
         * @throws NullPointerException If {@code predicate} is null.
         */
        public @NotNull Promise<@NotNull List<@NotNull Result>> search(@NotNull Predicate predicate) {
            Objects.requireNonNull(predicate, "Argument 'predicate'");
            var future = new CompletableFuture<List<Result>>();
            var operation = new SearchOperation(future, predicate);
            add(operation);
            return new Promise<>(future);
        }
    }

    private static final class Target {
        private final ServerWorld world;
        private final Region region;

        private Target(ServerWorld world, Region region) {
            this.world = world;
            this.region = region;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Target otherTarget
                && world.equals(otherTarget.world)
                && region.equals(otherTarget.region);
        }

        @Override
        public int hashCode() {
            return 31 * world.hashCode() + region.hashCode();
        }
    }

    private interface Operation {
        Action action();
        void complete();
        void completeExceptionally(Throwable cause);
    }

    private static final class EditOperation implements Operation {
        private final CompletableFuture<Unit> future;
        private final Action action;

        private EditOperation(CompletableFuture<Unit> future, Action action) {
            this.future = future;
            this.action = action;
        }

        @Override
        public Action action() {
            return action;
        }

        @Override
        public void complete() {
            future.complete(Unit.INSTANCE);
        }

        @Override
        public void completeExceptionally(Throwable cause) {
            future.completeExceptionally(cause);
        }
    }

    private static final class SearchOperation implements Operation, Action {
        private final CompletableFuture<List<Result>> promise;
        private final Predicate predicate;

        private final ArrayList<Result> results = new ArrayList<>(64);

        private SearchOperation(CompletableFuture<List<Result>> promise, Predicate predicate) {
            this.promise = promise;
            this.predicate = predicate;
        }

        @Override
        public @Nullable BlockState apply(@NotNull BlockState state, int x, int y, int z) {
            if (predicate.test(state)) {
                results.add(new Result(new BlockPos(x, y, z), state));
            }
            return null;
        }

        @Override
        public Action action() {
            return this;
        }

        @Override
        public void complete() {
            promise.complete(results);
        }

        @Override
        public void completeExceptionally(Throwable cause) {
            promise.completeExceptionally(cause);
        }
    }

    private static final class Task {
        private final Target target;
        private final Operation[] operations;

        public Task(Target target, Operation[] operations) {
            this.target = target;
            this.operations = operations;
        }

        private boolean performChunk(WorldChunk chunk, Region region, ServerWorld world, ServerLightingProvider lighting, Action[] actions) {
            var cPos = chunk.getPos();
            int offsetX = cPos.x << 4;
            int offsetZ = cPos.z << 4;

            var posMin = region.getStart();
            var posMax = region.getEnd();
            int posMinY = posMin.getY(), posMaxY = posMax.getY();
            int posMinX = posMin.getX(), posMaxX = posMax.getX();
            int posMinZ = posMin.getZ(), posMaxZ = posMax.getZ();

            int bottomY = world.getBottomY();

            var sections = chunk.getSectionArray();
            int sectionsCount = sections.length;

            var mutated = false;
            // var mutatedSections = new boolean[sectionsCount];

            for (int i = 0; i < sectionsCount; i++) {
                boolean mutatedSection = false;
                var section = sections[i];
                int offsetY = (i << 4) + bottomY;
                for (int y = 0; y < 16; y++) {
                    int posY = y + offsetY;
                    if (posMinY > posY || posMaxY < posY) continue;
                    for (int z = 0; z < 16; z++) {
                        int posZ = z + offsetZ;
                        if (posMinZ > posZ || posMaxZ < posZ) continue;
                        for (int x = 0; x < 16; x++) {
                            int posX = x + offsetX;
                            if (posMinX > posX || posMaxX < posX) continue;
                            var stateOld = section.getBlockState(x, y, z);
                            for (var action : actions) {
                                var stateNew = action.apply(stateOld, posX, posY, posZ);
                                if (stateNew != null && stateNew != stateOld) {
                                    section.setBlockState(x, y, z, stateNew);
                                    stateOld = stateNew;
                                    mutatedSection = true;
                                }
                            }
                        }
                    }
                }
                if (mutatedSection) {
                    mutated = true;
                    // mutatedSections[i] = true;
                }
            }

            /*
            for (int i = 0; i < sectionsCount; i++) {
                if (mutatedSections[i]) {
                    lighting.setSectionStatus(ChunkSectionPos.from(chunk.getPos(), i), true);
                }
            }
            if (mutated) {
                lighting.propagateLight(chunk.getPos());
            }
            */

            return mutated;
        }

        private List<WorldChunk> performTask() {
            Target target = this.target;
            Operation[] operations = this.operations;

            Region region = target.region;
            ServerWorld world = target.world;

            ServerChunkManager manager = world.getChunkManager();
            ServerLightingProvider lighting = manager.getLightingProvider();

            int chunksCount = (int) region.getChunkCount();

            var chunks = new ArrayList<WorldChunk>(chunksCount);
            var chunksMutated = new ArrayList<WorldChunk>(chunksCount);

            var cPosMin = region.getChunkStart();
            var cPosMax = region.getChunkEnd();
            int cPosMinX = cPosMin.x, cPosMaxX = cPosMax.x;
            int cPosMinZ = cPosMin.z, cPosMaxZ = cPosMax.z;

            for (int x = cPosMinX; x <= cPosMaxX; x++) {
                for (int z = cPosMinZ; z <= cPosMaxZ; z++) {
                    world.setChunkForced(x, z, true);
                    var chunk = manager.getChunk(x, z, ChunkStatus.FULL, true);
                    if (chunk != null) chunks.add((WorldChunk) chunk);
                }
            }

            var actions = new Action[operations.length];
            for (int i = 0, length = operations.length; i < length; i++) {
                actions[i] = operations[i].action();
            }

            for (WorldChunk chunk : chunks) {
                var mutated = performChunk(chunk, region, world, lighting, actions);
                if (mutated) chunksMutated.add(chunk);
            }

            for (Operation operation : operations) {
                operation.complete();
            }

            return chunksMutated;
        }

        private List<WorldChunk> perform() {
            var start = System.nanoTime();
            var success = true;
            try {
                return performTask();
            } catch (Throwable cause) {
                for (Operation operation : operations) {
                    try {
                        operation.completeExceptionally(cause);
                    } catch (Throwable ignored) {
                    }
                }
                success = false;
            } finally {
                var ms = (double) (System.nanoTime() - start) * 1e-6D;
                var message = new StringBuilder(64);
                message.append("Editor performed task (");
                message.append(success ? "success" : "failure");
                message.append(") for ");
                message.append(operations.length);
                message.append(" operation(s) in ");
                message.append(new DecimalFormat("#.##").format(ms));
                message.append("ms");
                if (success) {
                    if (ms >= 2.0) CMinusKt.getLogger().info(message);
                    else CMinusKt.getLogger().debug(message);
                } else {
                    CMinusKt.getLogger().warn(message);
                }
            }
            return List.of();
        }
    }

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Target, Queue> QUEUES = new LinkedHashMap<>();

    /**
     * Creates a queue of operations for the given world and region.
     * Note that queues should be used immediately and discarded, as they become invalid after the next tick.
     * @param world World to perform operations in.
     * @param region Region to perform operations in.
     * @return Operation queue instance.
     * @throws NullPointerException If either of the provided arguments are null.
     */
    public static @NotNull Queue queue(@NotNull ServerWorld world, @NotNull Region region) {
        Objects.requireNonNull(world, "Argument 'world'");
        Objects.requireNonNull(region, "Argument 'region'");
        synchronized (LOCK) {
            return QUEUES.computeIfAbsent(new Target(world, region), (key) -> new Queue());
        }
    }

    /**
     * Creates a queue of operations for the given world and region, calling the provided function with the new operation queue.
     * The provided function will be executed while synchronized on the global editor lock, ensuring that the queue instance remains valid and no other operations are enqueued concurrently.
     * @param world World to perform operations in.
     * @param region Region to perform operations in.
     * @param function Function called with the new operation queue.
     * @return Return value of the provided function.
     * @throws NullPointerException If either of the provided arguments are null.
     */
    public static <T> T queue(@NotNull ServerWorld world, @NotNull Region region, @NotNull Function<@NotNull Queue, T> function) {
        Objects.requireNonNull(world, "Argument 'world'");
        Objects.requireNonNull(region, "Argument 'region'");
        Objects.requireNonNull(function, "Argument 'function'");
        synchronized (LOCK) {
            return function.apply(queue(world, region));
        }
    }

    /**
     * Executes all enqueued operations, must be run on the main server thread.
     * @throws IllegalStateException If invoked from the wrong thread.
     */
    public static void update() {
        var server = CMinusKt.getServer();

        if (!server.isOnThread()) {
            throw new IllegalStateException("Editor execution started from wrong thread");
        }

        ArrayList<Task> tasks;

        synchronized (LOCK) {
            if (QUEUES.isEmpty()) {
                return;
            }

            tasks = new ArrayList<>(QUEUES.size());

            for (var entry : QUEUES.entrySet()) {
                var target = entry.getKey();
                var queue = entry.getValue();
                tasks.add(new Task(target, queue.consume()));
            }

            QUEUES.clear();
        }

        tasks.sort(Comparator.comparingLong((task) -> task.target.region.getBlockCount()));

        var chunkLists = new ArrayList<List<WorldChunk>>(tasks.size());
        var chunkCount = 0;

        for (Task task : tasks) {
            var chunks = task.perform();
            if (chunks.isEmpty()) continue;
            chunkLists.add(chunks);
            chunkCount = Math.max(chunkCount, (int) task.target.region.getChunkCount());
        }

        if (chunkLists.isEmpty()) return;

        var chunks = new LinkedHashSet<WorldChunk>(chunkCount * 2);
        chunkLists.forEach(chunks::addAll);

        var distance = server.getPlayerManager().getViewDistance() + 4;

        for (WorldChunk chunk : chunks) {
            var world = (ServerWorld) chunk.getWorld();
            var packet = new ChunkDataS2CPacket(chunk, world.getChunkManager().getLightingProvider(), null, null);
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.getChunkPos().getChebyshevDistance(chunk.getPos()) > distance) continue;
                player.networkHandler.sendPacket(packet);
            }
        }
    }

}

