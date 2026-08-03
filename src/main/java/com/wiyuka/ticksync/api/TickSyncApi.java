package com.wiyuka.ticksync.api;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.history.StateHistory;
import com.wiyuka.ticksync.net.PhaseBroadcaster;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import com.wiyuka.ticksync.phase.PlayerSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class TickSyncApi {

    private TickSyncApi() {
    }

    public static boolean available() {
        return TickSync.get() != null;
    }

    public static Optional<PlayerTiming> timing(ServerPlayer player) {
        return timing(player.getUUID());
    }

    public static Optional<PlayerTiming> timing(UUID playerId) {
        TickSync mod = TickSync.get();
        if (mod == null) {
            return Optional.empty();
        }
        PlayerSession session = mod.registry().session(playerId);
        if (session == null) {
            return Optional.empty();
        }
        PhaseTracker phase = session.phase();
        double mean = phase.meanMs();
        double rtt = PhaseBroadcaster.rttMs(playerId);
        if (Double.isNaN(mean) || rtt < 0) {
            return Optional.of(new PlayerTiming(
                playerId, Double.NaN, Double.NaN, Double.NaN, rtt, phase.windowCount(),
                Confidence.NONE));
        }
        return Optional.of(new PlayerTiming(playerId,
                mean,
                phase.livePhaseMs(),
                phase.spreadMs(),
                rtt,
                phase.windowCount(),
                confidenceOf(session)));
    }

    public static OptionalInt rewindTicks(ServerPlayer player) {
        return timing(player)
            .filter(t -> t.confidence().atLeast(Confidence.LOW))
            .stream()
            .mapToInt(PlayerTiming::stalenessTicks)
            .findFirst();
    }

    public static OptionalInt rewindTicksForThisTick(ServerPlayer player) {
        TickSync mod = TickSync.get();
        if (mod == null) {
            return OptionalInt.empty();
        }
        PlayerSession session = mod.registry().session(player.getUUID());
        if (session == null) {
            return OptionalInt.empty();
        }
        double wait = session.phase().lastSampleMs();
        double rtt = PhaseBroadcaster.rttMs(player.getUUID());
        if (Double.isNaN(wait) || rtt < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(PhaseMath.stalenessTicks(wait, rtt));
    }

    public static Optional<StateHistory> history() {
        TickSync mod = TickSync.get();
        return mod == null ? Optional.empty() : Optional.of(mod.history());
    }

    public static Optional<TrackedState> asSeenBy(ServerPlayer actor, ServerPlayer target) {
        OptionalInt live = rewindTicksForThisTick(actor);
        OptionalInt rewind = live.isPresent() ? live : rewindTicks(actor);
        if (rewind.isEmpty()) {
            return Optional.empty();
        }
        int ticks = rewind.getAsInt();
        return history().flatMap(h -> h.ticksAgo(target.getUUID(), ticks));
    }

    private static Confidence confidenceOf(PlayerSession session) {
        PhaseTracker phase = session.phase();
        if (Double.isNaN(phase.meanMs()) || !session.rtt().ready()) {
            return Confidence.NONE;
        }
        boolean settled = phase.windowCount() >= 600
            && session.rtt().fill() >= 0.5
            && phase.sigmaMs() < PhaseMath.PERIOD_MS / 5.0;
        return settled ? Confidence.GOOD : Confidence.LOW;
    }
}
