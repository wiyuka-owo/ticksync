package com.wiyuka.ticksync.phase;

final class DriftEstimator {

    private static final int POINTS = 600;
    private static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MIN_POINTS = 10;

    private final double[] timeSec = new double[POINTS];
    private final double[] phaseMs = new double[POINTS];
    private int size;
    private int cursor;
    private long lastNanos;
    private long firstNanos;
    private double unwrappedPhaseMs = Double.NaN;

    void sample(long nowNanos, double meanMs) {
        if (lastNanos != 0L && nowNanos - lastNanos < SAMPLE_INTERVAL_NANOS) {
            return;
        }
        lastNanos = nowNanos;

        if (Double.isNaN(unwrappedPhaseMs)) {
            unwrappedPhaseMs = meanMs;
            firstNanos = nowNanos;
        } else {
            unwrappedPhaseMs += PhaseMath.shortestSignedDeltaMs(meanMs, PhaseMath.wrap(unwrappedPhaseMs));
        }

        timeSec[cursor] = (nowNanos - firstNanos) / 1e9;
        phaseMs[cursor] = unwrappedPhaseMs;
        cursor = (cursor + 1) % POINTS;
        if (size < POINTS) {
            size++;
        }
    }

    double slopeMsPerMinute() {
        if (size < MIN_POINTS) {
            return Double.NaN;
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXX = 0.0;
        double sumXY = 0.0;
        for (int i = 0; i < size; i++) {
            double x = timeSec[i];
            double y = phaseMs[i];
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
        }
        double n = size;
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-9) {
            return Double.NaN;
        }
        return (n * sumXY - sumX * sumY) / denominator * 60.0;
    }

    void reset() {
        size = 0;
        cursor = 0;
        lastNanos = 0L;
        firstNanos = 0L;
        unwrappedPhaseMs = Double.NaN;
    }
}
