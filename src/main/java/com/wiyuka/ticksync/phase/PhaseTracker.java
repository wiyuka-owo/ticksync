package com.wiyuka.ticksync.phase;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhaseTracker {

    public static final double PERIOD_MS = PhaseMath.PERIOD_MS;

    private static final int HISTOGRAM_BINS = 50;
    private static final int WINDOW_SAMPLES = 600;
    private static final double TAIL_QUANTILE = 0.05;
    private static final int LIVE_SAMPLES = 8;
    private static final double MAX_WAIT_MS = 1.5 * PERIOD_MS;
    private static final int DRIFT_MIN_SAMPLES = 32;
    private static final int PENDING_CAP = 2048;

    private final ConcurrentLinkedQueue<Long> arrivals = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();

    private final UUID playerId;
    private final String playerName;

    private final CircularStats stats = new CircularStats(PERIOD_MS, WINDOW_SAMPLES);
    private final CircularStats live = new CircularStats(PERIOD_MS, LIVE_SAMPLES);
    private final DeliveryWindow delivery = new DeliveryWindow();
    private final DriftEstimator drift = new DriftEstimator();

    private long accepted;
    private long discarded;

    private volatile double lastSample = Double.NaN;

    public PhaseTracker(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void recordArrival(long arrivalNanos) {
        if (pending.get() < PENDING_CAP) {
            arrivals.add(arrivalNanos);
            pending.incrementAndGet();
        }
    }

    public void drain(long tickStartNanos, boolean gridClean) {
        int seen = 0;
        Long arrival;
        while ((arrival = arrivals.poll()) != null) {
            pending.decrementAndGet();
            seen++;
            double waitMs = (tickStartNanos - arrival) / 1_000_000.0;
            if (!gridClean || waitMs < 0.0 || waitMs > MAX_WAIT_MS) {
                discarded++;
                continue;
            }
            double phaseMs = PhaseMath.wrap(waitMs);
            stats.add(phaseMs);
            live.add(phaseMs);
            lastSample = phaseMs;
            accepted++;
        }
        delivery.record(seen);
        maybeSampleDrift(tickStartNanos);
    }

    private void maybeSampleDrift(long nowNanos) {
        if (stats.count() < DRIFT_MIN_SAMPLES) {
            return;
        }
        double mean = stats.meanMs();
        if (!Double.isNaN(mean)) {
            drift.sample(nowNanos, mean);
        }
    }

    public double meanMs() {
        return stats.meanMs();
    }

    public double lastSampleMs() {
        return lastSample;
    }

    public double livePhaseMs() {
        return live.meanMs();
    }

    public double sigmaMs() {
        return stats.sigmaMs();
    }

    public int windowCount() {
        return stats.count();
    }

    public long accepted() {
        return accepted;
    }

    public long discarded() {
        return discarded;
    }

    public double coverage() {
        return delivery.coverage();
    }

    public double doubledRate() {
        return delivery.doubledRate();
    }

    public double driftMsPerMinute() {
        return drift.slopeMsPerMinute();
    }

    public long[] histogram() {
        long[] bins = new long[HISTOGRAM_BINS];
        for (double sample : stats.snapshot()) {
            bins[Math.max(0, Math.min(HISTOGRAM_BINS - 1, (int) sample))]++;
        }
        return bins;
    }

    public double percentileMs(double quantile) {
        double mean = stats.meanMs();
        double offset = stats.offsetQuantileMs(quantile);
        if (Double.isNaN(mean) || Double.isNaN(offset)) {
            return Double.NaN;
        }
        return PhaseMath.wrap(mean + offset);
    }

    public double spreadMs() {
        double lo = stats.offsetQuantileMs(TAIL_QUANTILE);
        double hi = stats.offsetQuantileMs(1.0 - TAIL_QUANTILE);
        return Double.isNaN(lo) || Double.isNaN(hi) ? Double.NaN : hi - lo;
    }

    public double lowTailMs() {
        double lo = stats.offsetQuantileMs(TAIL_QUANTILE);
        return Double.isNaN(lo) ? Double.NaN : Math.max(0.0, -lo);
    }

    public double highTailMs() {
        double hi = stats.offsetQuantileMs(1.0 - TAIL_QUANTILE);
        return Double.isNaN(hi) ? Double.NaN : Math.max(0.0, hi);
    }

    public void reset() {
        stats.clear();
        live.clear();
        delivery.reset();
        drift.reset();
        lastSample = Double.NaN;
        accepted = 0L;
        discarded = 0L;
    }
}
