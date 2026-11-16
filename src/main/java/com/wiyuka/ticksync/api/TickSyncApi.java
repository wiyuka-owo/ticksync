package com.wiyuka.ticksync.api;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.history.StateHistory;
import com.wiyuka.ticksync.net.PhaseBroadcaster;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import com.wiyuka.ticksync.phase.PlayerSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;
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
        mod.registry().requestSamples(playerId);
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
        OptionalDouble rtt = liveRttTicks(player);
        return rtt.isPresent()
            ? OptionalInt.of(PhaseMath.rewindTicks(rtt.getAsDouble() * PhaseMath.PERIOD_MS))
            : OptionalInt.empty();
    }

    public static OptionalDouble rttTicks(ServerPlayer player) {
        OptionalDouble live = liveRttTicks(player);
        if (live.isPresent()) {
            return live;
        }
        return timing(player)
            .filter(t -> t.confidence().atLeast(Confidence.LOW))
            .stream()
            .mapToDouble(PlayerTiming::rttTicks)
            .findFirst();
    }

    public static OptionalDouble rewindTicksExact(ServerPlayer player) {
        OptionalDouble network = rttTicks(player);
        return network.isPresent()
            ? OptionalDouble.of(network.getAsDouble() + PhaseMath.INTERPOLATION_TICKS_EXACT)
            : OptionalDouble.empty();
    }

    private static OptionalDouble liveRttTicks(ServerPlayer player) {
        TickSync mod = TickSync.get();
        if (mod == null) {
            return OptionalDouble.empty();
        }
        mod.registry().requestSamples(player.getUUID());
        double rtt = PhaseBroadcaster.rttMs(player.getUUID());
        return rtt < 0 ? OptionalDouble.empty() : OptionalDouble.of(PhaseMath.rttTicks(rtt));
    }

    public static Optional<Vec3> positionAsSeenBy(ServerPlayer actor, ServerPlayer target) {
        Optional<StateHistory> history = history();
        OptionalDouble rewind = rewindTicksExact(actor);
        if (rewind.isEmpty()) {
            return Optional.empty();
        }
        double ticks = Math.max(0.0, rewind.getAsDouble());
        return history.flatMap(h -> h.broadcastPositionAt(target.getUUID(), ticks));
    }

    /** Starts recording on demand. Past ticks become available as server ticks are recorded. */
    public static Optional<StateHistory> history() {
        TickSync mod = TickSync.get();
        if (mod == null) return Optional.empty();
        mod.history().requestSamples();
        return Optional.of(mod.history());
    }

    public static Optional<TrackedState> asSeenBy(ServerPlayer actor, ServerPlayer target) {
        Optional<StateHistory> history = history();
        OptionalInt live = rewindTicksForThisTick(actor);
        OptionalInt rewind = live.isPresent() ? live : rewindTicks(actor);
        if (rewind.isEmpty()) {
            return Optional.empty();
        }
        int ticks = rewind.getAsInt();
        return history.flatMap(h -> h.ticksAgo(target.getUUID(), ticks));
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
