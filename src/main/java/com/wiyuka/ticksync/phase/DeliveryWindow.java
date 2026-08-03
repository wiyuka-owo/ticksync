package com.wiyuka.ticksync.phase;

import java.util.Arrays;

final class DeliveryWindow {

    private static final int WINDOW_TICKS = 1200;
    private static final int MARKERS_PER_TICK_CAP = 2;

    private final byte[] markersPerTick = new byte[WINDOW_TICKS];
    private int cursor;
    private int count;
    private int ticksWithSample;
    private int ticksWithMultiple;

    void record(int markers) {
        if (count == WINDOW_TICKS) {
            byte evicted = markersPerTick[cursor];
            if (evicted > 0) {
                ticksWithSample--;
            }
            if (evicted > 1) {
                ticksWithMultiple--;
            }
        } else {
            count++;
        }

        byte capped = (byte) Math.min(markers, MARKERS_PER_TICK_CAP);
        markersPerTick[cursor] = capped;
        if (capped > 0) {
            ticksWithSample++;
        }
        if (capped > 1) {
            ticksWithMultiple++;
        }
        cursor = (cursor + 1) % WINDOW_TICKS;
    }

    double coverage() {
        return count == 0 ? Double.NaN : ticksWithSample / (double) count;
    }

    double doubledRate() {
        return count == 0 ? Double.NaN : ticksWithMultiple / (double) count;
    }

    void reset() {
        Arrays.fill(markersPerTick, (byte) 0);
        cursor = 0;
        count = 0;
        ticksWithSample = 0;
        ticksWithMultiple = 0;
    }
}
