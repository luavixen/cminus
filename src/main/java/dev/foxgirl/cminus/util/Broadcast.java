package dev.foxgirl.cminus.util;

import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class Broadcast {

    private Broadcast() {}

    public interface PacketSupplier {
        @Nullable
        Packet<?> get(@NotNull ServerPlayerEntity player);
    }

    public static void send(@NotNull PacketSupplier packetSupplier) {
        Objects.requireNonNull(packetSupplier, "Argument 'packetSupplier'");

        for (var player : CMinusKt.getServer().getPlayerManager().getPlayerList()) {
            var packet = packetSupplier.get(player);
            if (packet != null) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    public static void send(@Nullable Packet<?> packet) {
        send((player) -> packet);
    }

    @FunctionalInterface
    public interface PositionSupplier {
        @Nullable
        Position get(@NotNull ServerPlayerEntity player);
    }

    private static final class NearbyPositionSupplier implements PositionSupplier {
        private final World world;
        private final Position position;

        private final double maxSquaredDistance;

        private NearbyPositionSupplier(World world, Position position, double distance) {
            Objects.requireNonNull(world, "Argument 'world'");
            Objects.requireNonNull(position, "Argument 'position'");

            this.world = world;
            this.position = position;

            maxSquaredDistance = distance * distance;
        }

        @Override
        public @Nullable Position get(@NotNull ServerPlayerEntity player) {
            if (player.getWorld() != world) return null;
            if (player.squaredDistanceTo(position.getX(), position.getY(), position.getZ()) > maxSquaredDistance) return null;
            return position;
        }
    }

    public static void sendSound(@NotNull SoundEvent sound, @NotNull SoundCategory category, float volume, float pitch, @NotNull PositionSupplier positionSupplier) {
        Objects.requireNonNull(sound, "Argument 'sound'");
        Objects.requireNonNull(category, "Argument 'category'");
        Objects.requireNonNull(positionSupplier, "Argument 'positionSupplier'");

        var entry = Registries.SOUND_EVENT.getEntry(sound);
        var seed = ThreadLocalRandom.current().nextLong();

        send((player) -> {
            var pos = positionSupplier.get(player);
            if (pos == null) return null;

            return new PlaySoundS2CPacket(
                entry, category, pos.getX(), pos.getY(), pos.getZ(),
                volume, pitch, seed
            );
        });
    }

    public static void sendSound(@NotNull SoundEvent sound, @NotNull SoundCategory category, float volume, float pitch, @NotNull World world, @NotNull Position position) {
        sendSound(sound, category, volume, pitch, new NearbyPositionSupplier(world, position, 96.0));
    }

    public static void sendSound(@NotNull SoundEvent sound, @NotNull SoundCategory category, float volume, float pitch) {
        sendSound(sound, category, volume, pitch, (player) -> new Vec3d(player.getX(), player.getEyeY(), player.getZ()));
    }

    public static void sendParticles(@NotNull ParticleEffect particle, float speed, int count, @NotNull PositionSupplier positionSupplier) {
        Objects.requireNonNull(particle, "Argument 'particle'");
        Objects.requireNonNull(positionSupplier, "Argument 'positionSupplier'");

        send((player) -> {
            var pos = positionSupplier.get(player);
            if (pos == null) return null;

            return new ParticleS2CPacket(particle, false, pos.getX(), pos.getY(), pos.getZ(), 0.0F, 0.0F, 0.0F, speed, count);
        });
    }

    public static void sendParticles(@NotNull ParticleEffect particle, float speed, int count, @NotNull World world, @NotNull Position position) {
        sendParticles(particle, speed, count, new NearbyPositionSupplier(world, position, 48.0));
    }

}
