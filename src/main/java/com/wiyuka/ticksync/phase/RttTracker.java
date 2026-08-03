package com.wiyuka.ticksync.phase;

import java.util.Arrays;

public final class RttTracker {

    private static final int WINDOW = 64;

    private static final int PROBE_ID_TAG = 0x7C1C0000;
    private static final int PROBE_ID_TAG_MASK = 0xFFFF0000;

    private static final int IN_FLIGHT = 16;

    private static final long TIMEOUT_NANOS = 2_000_000_000L;

    private final double[] samples = new double[WINDOW];
    private int size;
    private int cursor;

    private final int[] pendingIds = new int[IN_FLIGHT];
    private final long[] pendingNanos = new long[IN_FLIGHT];
    private int pendingCursor;

    private int sequence;

    private volatile double floorMs = Double.NaN;
    private volatile long lastSampleNanos;

    public static boolean isProbe(int id) {
        return (id & PROBE_ID_TAG_MASK) == PROBE_ID_TAG;
    }

    public int nextProbeId() {
        return PROBE_ID_TAG | (++sequence & 0xFFFF);
    }

    public void recordSent(int id, long sentNanos) {
        pendingIds[pendingCursor] = id;
        pendingNanos[pendingCursor] = sentNanos;
        pendingCursor = (pendingCursor + 1) % IN_FLIGHT;
    }

    public void recordReply(int id, long replyNanos) {
        for (int i = 0; i < IN_FLIGHT; i++) {
            if (pendingIds[i] != id) {
                continue;
            }
            long elapsed = replyNanos - pendingNanos[i];
            pendingIds[i] = 0;
            if (elapsed < 0 || elapsed > TIMEOUT_NANOS) {
                return;
            }
            samples[cursor] = elapsed / 1_000_000.0;
            cursor = (cursor + 1) % WINDOW;
            if (size < WINDOW) {
                size++;
            }
            floorMs = minSampleMs();
            lastSampleNanos = replyNanos;
            return;
        }
    }

    private double minSampleMs() {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            best = Math.min(best, samples[i]);
        }
        return best;
    }

    public double rttMs() {
        return floorMs;
    }

    public boolean ready() {
        return !Double.isNaN(floorMs);
    }

    public double fill() {
        return size / (double) WINDOW;
    }

    public double paddingMs() {
        if (size == 0) {
            return Double.NaN;
        }
        double[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return sorted[size / 2] - sorted[0];
    }

    public void reset() {
        size = 0;
        cursor = 0;
        floorMs = Double.NaN;
        Arrays.fill(pendingIds, 0);
    }
}
