package com.wiyuka.ticksync.viz;

import com.wiyuka.ticksync.history.StateHistory;
import com.wiyuka.ticksync.net.GhostBoxPayload;
import com.wiyuka.ticksync.phase.PhaseMath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostViz {

    public static final double DEFAULT_RANGE_BLOCKS = 32.0;

    private static final Map<UUID, Double> watchers = new ConcurrentHashMap<>();

    private GhostViz() {
    }

    public static boolean supported(ServerPlayer observer) {
        return ServerPlayNetworking.canSend(observer, GhostBoxPayload.TYPE);
    }

    public static void enable(ServerPlayer observer, double rangeBlocks) {
        watchers.put(observer.getUUID(), rangeBlocks);
    }

    public static boolean disable(UUID observerId) {
        return watchers.remove(observerId) != null;
    }

    public static void clear() {
        watchers.clear();
    }

    public static boolean active() {
        return !watchers.isEmpty();
    }

    public static void tick(MinecraftServer server, StateHistory history) {
        if (watchers.isEmpty()) {
            return;
        }
        for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
            Double range = watchers.get(observer.getUUID());
            if (range != null && ServerPlayNetworking.canSend(observer, GhostBoxPayload.TYPE)) {
                ServerPlayNetworking.send(observer, collect(history, observer, range));
            }
        }
    }

    private static GhostBoxPayload collect(
        StateHistory history, ServerPlayer observer, double rangeBlocks) {

        double rangeSqr = rangeBlocks * rangeBlocks;
        List<GhostBoxPayload.Entry> entries = new ArrayList<>();
        for (ServerPlayer target : observer.level().players()) {
            if (target == observer || target.distanceToSqr(observer) > rangeSqr) {
                continue;
            }
            Optional<Vec3> seen = history.broadcastPositionAt(
                target.getUUID(), PhaseMath.INTERPOLATION_TICKS_EXACT);
            if (seen.isEmpty()) {
                continue;
            }
            entries.add(new GhostBoxPayload.Entry(target.getId(), seen.get(), target.position()));
            if (entries.size() >= GhostBoxPayload.MAX_ENTRIES) {
                break;
            }
        }
        return entries.isEmpty() ? GhostBoxPayload.EMPTY : new GhostBoxPayload(List.copyOf(entries));
    }
}
