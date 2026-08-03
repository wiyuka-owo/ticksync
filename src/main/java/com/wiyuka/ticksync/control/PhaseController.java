package com.wiyuka.ticksync.control;

import com.wiyuka.ticksync.net.PhaseBroadcaster;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PhaseController {

    public static final float CORRECTION_RATE = 19.5F;

    private static final int MIN_WINDOW_SAMPLES = 200;

    private static final double MARGIN_MS = 1.0;

    private static final double HEADROOM_MS = 4.0;

    private static final double MIN_GAIN_MS = 2.0;

    private static final double MIN_CORRECTION_MS = 2.0;

    private static final int AUTO_SCAN_INTERVAL_TICKS = 40;

    private static final int SETTLE_TICKS = 650;

    private final Map<UUID, Integer> active = new HashMap<>();
    private final Map<UUID, Integer> lastAlignTick = new HashMap<>();

    private AlignMode mode = AlignMode.C2S;
    private boolean auto;

    public static double phaseSlopeMsPerSecond(float rate) {
        return 1000.0 - 50.0 * rate;
    }

    public AlignMode mode() {
        return mode;
    }

    public void setMode(AlignMode mode) {
        this.mode = mode;
    }

    public Band bandFor(PhaseTracker tracker, double rttMs) {
        double lowTail = tracker.lowTailMs();
        double highTail = tracker.highTailMs();
        if (Double.isNaN(lowTail) || Double.isNaN(highTail) || rttMs < 0.0) {
            return null;
        }
        return Band.of(rttMs, lowTail, highTail);
    }

    public Decision decide(PhaseTracker tracker, double rttMs, int currentTick) {
        return decide(tracker, rttMs, currentTick, false);
    }

    public Decision decide(PhaseTracker tracker, double rttMs, int currentTick, boolean force) {
        if (!force && active.containsKey(tracker.playerId())) {
            return new Decision(null, "同步任务正在执行");
        }
        if (!force) {
            Integer last = lastAlignTick.get(tracker.playerId());
            if (last != null && currentTick - last < SETTLE_TICKS) {
                return new Decision(null, String.format(Locale.ROOT,
                        "正在等待数据 (距上次干扰消除还需 %d 秒)",
                        (SETTLE_TICKS - (currentTick - last)) / 20));
            }
            if (tracker.windowCount() < MIN_WINDOW_SAMPLES) {
                return new Decision(null, String.format(Locale.ROOT,
                        "样本数量不足 (%d/%d)", tracker.windowCount(), MIN_WINDOW_SAMPLES));
            }
        }
        double mean = tracker.meanMs();
        if (Double.isNaN(mean)) {
            return new Decision(null, "缺少相位估算数据");
        }
        if (rttMs < 0.0) {
            return new Decision(null, "缺少往返延迟(Ping)测量数据");
        }
        Band band = bandFor(tracker, rttMs);
        if (band == null) {
            if (!force) {
                return new Decision(null, "缺少网络波动数据");
            }

            band = Band.of(rttMs, 0.0, 0.0);
        }

        return mode == AlignMode.WORST
                ? degrade(tracker, band, mean, rttMs, force)
                : optimise(tracker, band, mode, mean, rttMs, force);
    }

    private Decision degrade(PhaseTracker tracker, Band band, double mean, double rttMs, boolean force) {
        double needed = tracker.lowTailMs() + tracker.highTailMs() + 2 * MARGIN_MS;
        double available = PhaseMath.PERIOD_MS - band.cliffMs();
        if (!force && needed > available) {
            return new Decision(null, String.format(Locale.ROOT,
                    "%s: 无法稳定停留 (波动范围需 %.1f ms 空间，跨刻区间宽 %.1f ms)",
                    AlignMode.WORST.id(), needed, available));
        }
        double target = band.worstMidpointMs();
        double correction = PhaseMath.walkDistanceMs(mean, target);
        if (correction <= (force ? 0.0 : MIN_CORRECTION_MS)) {
            return new Decision(null, String.format(Locale.ROOT,
                    "%s: 距目标 %.2f ms，无需调整", AlignMode.WORST.id(), correction));
        }
        double addedWait = PhaseMath.totalWaitMs(target, rttMs) - PhaseMath.totalWaitMs(mean, rttMs);
        return accept(AlignMode.WORST, mean, target, rttMs, false, addedWait);
    }

    private Decision optimise(PhaseTracker tracker, Band band, AlignMode mode,
                              double mean, double rttMs, boolean force) {
        boolean crossesTick = !fitsInOneTick(mean, rttMs);
        double desired = band.targetFor(mode);
        boolean desiredFits = fitsInOneTick(desired, rttMs);

        if (!desiredFits && !force && !crossesTick) {
            return new Decision(null, String.format(Locale.ROOT,
                    "%s: 无法找到安全区间 (波动范围需 %.1f ms 空间，单刻区间宽 %.1f ms)",
                    mode.id(), tracker.lowTailMs() + tracker.highTailMs() + 2 * MARGIN_MS, band.cliffMs()));
        }

        double target = desiredFits ? desired : band.safeMidpointMs();
        double correction = PhaseMath.walkDistanceMs(mean, target);
        double objectiveGain = waitFor(mode, mean, rttMs) - waitFor(mode, target, rttMs);

        boolean strayed = band.isEmpty() || mean < band.floorMs() || mean > band.ceilingMs();

        if (force) {
            return correction <= 0.0
                    ? new Decision(null, mode.id() + ": 已处于目标位置")
                    : accept(mode, mean, target, rttMs, crossesTick, objectiveGain);
        }
        if (crossesTick) {
            return accept(mode, mean, target, rttMs, true, objectiveGain);
        }
        if (correction < MIN_CORRECTION_MS) {
            return new Decision(null, String.format(Locale.ROOT,
                    "%s: 误差在 %.2f ms 以内，无需调整", mode.id(), correction));
        }
        if (!strayed && objectiveGain < MIN_GAIN_MS) {
            return new Decision(null, String.format(Locale.ROOT,
                    "%s: 链路分配已达目标 (继续向%s链路优化最多可减少 %.1f ms)",
                    mode.id(), mode == AlignMode.C2S ? "上行" : "下行", objectiveGain));
        }
        return accept(mode, mean, target, rttMs, false, objectiveGain);
    }

    private Decision accept(AlignMode mode, double mean, double target, double rttMs,
                            boolean rescue, double objectiveGain) {
        double correction = PhaseMath.walkDistanceMs(mean, target);
        double seconds = correction / phaseSlopeMsPerSecond(CORRECTION_RATE);
        return new Decision(new Plan(mode, mean, target, correction, seconds, rescue, objectiveGain), null);
    }

    private static boolean fitsInOneTick(double outboundWaitMs, double rttMs) {
        return PhaseMath.totalWaitMs(outboundWaitMs, rttMs) < PhaseMath.PERIOD_MS;
    }

    private static double waitFor(AlignMode mode, double outboundWaitMs, double rttMs) {
        return mode == AlignMode.C2S ? outboundWaitMs : PhaseMath.returnWaitMs(outboundWaitMs, rttMs);
    }

    public Decision align(ServerPlayer player, PhaseTracker tracker, int currentTick) {
        return align(player, tracker, currentTick, false);
    }

    public Decision align(ServerPlayer player, PhaseTracker tracker, int currentTick, boolean force) {
        if (force && active.containsKey(player.getUUID())) {
            cancel(player.level().getServer(), player.getUUID());
        }
        Decision decision = decide(tracker, PhaseBroadcaster.rttMs(player), currentTick, force);
        if (decision.accepted()) {
            apply(player, decision.plan(), currentTick);
        }
        return decision;
    }

    private void apply(ServerPlayer player, Plan plan, int currentTick) {
        int durationTicks = Math.max(1, (int) Math.round(plan.seconds() * 20.0));
        active.put(player.getUUID(), currentTick + durationTicks);
        lastAlignTick.put(player.getUUID(), currentTick);
        player.connection.send(new ClientboundTickingStatePacket(CORRECTION_RATE, false));
    }

    public List<String> alignAll(MinecraftServer server, Collection<PhaseTracker> trackers, int currentTick) {
        List<String> report = new ArrayList<>();
        for (PhaseTracker tracker : trackers) {
            ServerPlayer player = server.getPlayerList().getPlayer(tracker.playerId());
            if (player == null) {
                continue;
            }
            Decision decision = align(player, tracker, currentTick);
            report.add(tracker.playerName() + ": "
                    + (decision.accepted() ? decision.plan().toString() : "跳过 — " + decision.rejection()));
        }
        return report;
    }

    public boolean auto() {
        return auto;
    }

    public void setAuto(boolean value) {
        this.auto = value;
    }

    public void tick(MinecraftServer server, Collection<PhaseTracker> trackers, int currentTick) {
        expire(server, currentTick);
        if (auto && currentTick % AUTO_SCAN_INTERVAL_TICKS == 0) {
            autoScan(server, trackers, currentTick);
        }
    }

    private void expire(MinecraftServer server, int currentTick) {
        if (active.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (currentTick >= entry.getValue()) {
                restore(server, entry.getKey());
                iterator.remove();
            }
        }
    }

    private void autoScan(MinecraftServer server, Collection<PhaseTracker> trackers, int currentTick) {
        for (PhaseTracker tracker : trackers) {
            ServerPlayer player = server.getPlayerList().getPlayer(tracker.playerId());
            if (player == null) {
                continue;
            }
            Decision decision = decide(tracker, PhaseBroadcaster.rttMs(player), currentTick);
            if (decision.accepted()) {
                apply(player, decision.plan(), currentTick);
            }
        }
    }

    private void restore(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sendServerTickState(server, player);
        }
    }

    public static void sendServerTickState(MinecraftServer server, ServerPlayer player) {
        player.connection.send(new ClientboundTickingStatePacket(
                server.tickRateManager().tickrate(), server.tickRateManager().isFrozen()));
    }

    public void cancel(MinecraftServer server, UUID playerId) {
        if (active.remove(playerId) != null) {
            restore(server, playerId);
        }
    }

    /**
     * Behind a proxy a "disconnect" is a server switch: the same client keeps playing on a
     * backend that never sent it a tick rate, so the correction rate has to be undone here
     * while the connection is still writable.
     */
    public void release(MinecraftServer server, ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (active.remove(playerId) != null) {
            sendServerTickState(server, player);
        }
        lastAlignTick.remove(playerId);
    }

    public void cancelAll(MinecraftServer server) {
        for (UUID playerId : active.keySet()) {
            restore(server, playerId);
        }
        active.clear();
    }

    public boolean isCorrecting(UUID playerId) {
        return active.containsKey(playerId);
    }

    public int activeCount() {
        return active.size();
    }

    public record Band(double cliffMs, double floorMs, double ceilingMs) {

        public static Band of(double rttMs, double lowTailMs, double highTailMs) {
            double cliff = PhaseMath.cliffMs(rttMs);
            return new Band(cliff, lowTailMs + MARGIN_MS, cliff - highTailMs - MARGIN_MS);
        }

        public boolean isEmpty() {
            return ceilingMs < floorMs;
        }

        public double widthMs() {
            return Math.max(0.0, ceilingMs - floorMs);
        }

        public double targetFor(AlignMode mode) {
            if (mode == AlignMode.WORST) {
                return worstMidpointMs();
            }
            if (isEmpty()) {
                return safeMidpointMs();
            }
            double headroom = Math.min(HEADROOM_MS, widthMs() / 2.0);
            return mode == AlignMode.C2S ? floorMs + headroom : ceilingMs - headroom;
        }

        public double safeMidpointMs() {
            return cliffMs / 2.0;
        }

        public double worstMidpointMs() {
            return (cliffMs + PhaseMath.PERIOD_MS) / 2.0;
        }
    }

    public record Decision(Plan plan, String rejection) {
        public boolean accepted() {
            return plan != null;
        }
    }

    public record Plan(AlignMode mode, double fromMs, double targetMs, double correctionMs,
                       double seconds, boolean rescue, double objectiveGainMs) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "%s %.2f ms -> %.2f ms (需偏移 %.2f ms，耗时 %.2f 秒，%s)",
                    mode.id(), fromMs, targetMs, correctionMs, seconds, effect());
        }

        private String effect() {
            if (mode == AlignMode.WORST) {
                return String.format(Locale.ROOT, "总等待增加 %.2f ms", objectiveGainMs);
            }
            String leg = mode == AlignMode.C2S ? "上行" : "下行";
            if (rescue) {
                return String.format(Locale.ROOT, "%s链路等待减少 %.2f ms，规避跨刻延迟 %.0f ms",
                        leg, objectiveGainMs, PhaseMath.PERIOD_MS);
            }
            return String.format(Locale.ROOT, "%s链路等待减少 %.2f ms", leg, objectiveGainMs);
        }
    }
}
