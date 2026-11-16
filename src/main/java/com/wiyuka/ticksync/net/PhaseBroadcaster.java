package com.wiyuka.ticksync.net;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.AlignMode;
import com.wiyuka.ticksync.control.PhaseController;
import com.wiyuka.ticksync.phase.PhaseTracker;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhaseBroadcaster {

    private static final Map<UUID, UUID> hudWatch = new ConcurrentHashMap<>();
    private static final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    public static void subscribe(UUID viewer, boolean visible) {
        if (visible) viewers.add(viewer);
        else viewers.remove(viewer);
    }

    public static Set<UUID> subjects() {
        if (viewers.isEmpty() || hudWatch.isEmpty()) return viewers;
        Set<UUID> subjects = new HashSet<>();
        for (UUID viewer : viewers) subjects.add(hudSubject(viewer));
        return subjects;
    }

    private PhaseBroadcaster() {
    }

    public static void setHudWatch(UUID viewerId, UUID subjectId) {
        if (subjectId == null || subjectId.equals(viewerId)) {
            hudWatch.remove(viewerId);
        } else {
            hudWatch.put(viewerId, subjectId);
        }
    }

    public static UUID hudSubject(UUID viewerId) {
        return hudWatch.getOrDefault(viewerId, viewerId);
    }

    public static void discardHud(UUID playerId) {
        viewers.remove(playerId);
        hudWatch.remove(playerId);
        hudWatch.values().removeIf(subjectId -> subjectId.equals(playerId));
    }

    public static void clearHudWatches() {
        viewers.clear();
        hudWatch.clear();
    }

    public static double rttMs(ServerPlayer player) {
        return rttMs(player.getUUID(), player.connection.latency());
    }

    public static double rttMs(UUID playerId) {
        return rttMs(playerId, -1);
    }

    private static double rttMs(UUID playerId, int fallback) {
        TickSync mod = TickSync.get();
        double measured = mod == null ? -1.0 : mod.registry().rttMs(playerId);
        return measured >= 0 ? measured : fallback;
    }

    public static void broadcast(MinecraftServer server, PhaseController controller) {
        if (viewers.isEmpty()) {
            return;
        }
        TickSync mod = TickSync.get();
        if (mod == null) {
            return;
        }
        for (UUID viewerId : viewers) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null || !ServerPlayNetworking.canSend(viewer, PhaseSnapshotPayload.TYPE)) {
                continue;
            }
            UUID subjectId = hudSubject(viewerId);
            PhaseTracker tracker = mod.registry().get(subjectId);
            if (tracker == null) {
                continue;
            }
            ServerPlayer subject = server.getPlayerList().getPlayer(subjectId);
            double rtt = subject != null ? rttMs(subject) : rttMs(subjectId);
            AlignMode mode = controller.mode();
            PhaseController.Band band = controller.bandFor(tracker, rtt);
            float target = band == null ? Float.NaN : (float) band.targetFor(mode);
            ServerPlayNetworking.send(viewer, new PhaseSnapshotPayload(
                tracker.playerName(),
                (float) tracker.meanMs(),
                (float) tracker.livePhaseMs(),
                (float) tracker.sigmaMs(),
                (float) tracker.lowTailMs(),
                target,
                mode,
                (int) Math.round(rtt),
                tracker.windowCount(),
                controller.isCorrecting(subjectId)));
        }
    }
}
