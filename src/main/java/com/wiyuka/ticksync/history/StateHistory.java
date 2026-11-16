package com.wiyuka.ticksync.history;

import com.wiyuka.ticksync.api.TrackedState;
import com.wiyuka.ticksync.mixin.ChunkMapAccessor;
import com.wiyuka.ticksync.mixin.TrackedEntityAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StateHistory {

    public static final int DEPTH_TICKS = 40;

    private final Map<UUID, PlayerHistory> histories = new ConcurrentHashMap<>();
    private volatile long requestedUntil;

    public void requestSamples() {
        requestedUntil = System.nanoTime() + 5_000_000_000L;
    }

    public void tick(MinecraftServer server, int tick, boolean recording) {
        long until = requestedUntil;
        if (recording || (until != 0 && System.nanoTime() - until < 0)) tick(server, tick);
        else if (!histories.isEmpty()) histories.clear();
    }

    public void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            histories
                .computeIfAbsent(player.getUUID(), id -> new PlayerHistory(DEPTH_TICKS))
                .record(tick, player, broadcastPositionOf(player));
        }
    }

    private static Vec3 broadcastPositionOf(ServerPlayer player) {
        ServerEntity serverEntity = serverEntityOf(player);
        return serverEntity == null ? player.position() : serverEntity.getPositionBase();
    }

    private static ServerEntity serverEntityOf(ServerPlayer player) {
        ServerLevel level = player.level();
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap)
            .ticksync$entityMap().get(player.getId());
        return tracked instanceof TrackedEntityAccessor accessor
            ? accessor.ticksync$serverEntity()
            : null;
    }

    public void detach(UUID playerId) {
        histories.remove(playerId);
    }

    public void clear() {
        histories.clear();
        requestedUntil = 0;
    }

    public Optional<TrackedState> at(UUID playerId, int tick) {
        requestSamples();
        PlayerHistory history = histories.get(playerId);
        return history == null ? Optional.empty() : history.at(tick);
    }

    public Optional<TrackedState> ticksAgo(UUID playerId, int ticks) {
        requestSamples();
        PlayerHistory history = histories.get(playerId);
        if (history == null || ticks < 0) {
            return Optional.empty();
        }
        return history.at(history.newestTick() - ticks);
    }

    public Optional<Vec3> broadcastPositionAt(UUID playerId, double ticksAgo) {
        if (Double.isNaN(ticksAgo) || ticksAgo < 0.0) {
            return Optional.empty();
        }
        int floor = (int) Math.floor(ticksAgo);
        double t = ticksAgo - floor;
        Optional<TrackedState> near = ticksAgo(playerId, floor);
        if (near.isEmpty()) {
            return Optional.empty();
        }
        Vec3 from = near.get().broadcastPosition();
        if (t < 1.0e-9) {
            return Optional.of(from);
        }
        Optional<TrackedState> far = ticksAgo(playerId, floor + 1);
        if (far.isEmpty()) {
            return Optional.of(from);
        }
        Vec3 to = far.get().broadcastPosition();
        return Optional.of(new Vec3(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t));
    }
}
