package com.wiyuka.ticksync.phase;

public final class PhaseMath {

    public static final double PERIOD_MS = 50.0;

    public static final double INTERPOLATION_TICKS_EXACT = 2.0;

    private PhaseMath() {
    }

    public static double wrap(double ms) {
        double wrapped = ms % PERIOD_MS;
        return wrapped < 0.0 ? wrapped + PERIOD_MS : wrapped;
    }

    public static double msUntilNextBoundary(double phaseMs) {
        return wrap(-phaseMs);
    }

    public static double shortestSignedDeltaMs(double fromMs, double toMs) {
        double delta = wrap(fromMs - toMs);
        return delta > PERIOD_MS / 2.0 ? delta - PERIOD_MS : delta;
    }

    public static double returnWaitMs(double outboundWaitMs, double rttMs) {
        return msUntilNextBoundary(outboundWaitMs + rttMs);
    }

    public static double totalWaitMs(double outboundWaitMs, double rttMs) {
        return outboundWaitMs + returnWaitMs(outboundWaitMs, rttMs);
    }

    public static double cliffMs(double rttMs) {
        return msUntilNextBoundary(rttMs);
    }

    public static double rttTicks(double rttMs) {
        return rttMs / PERIOD_MS;
    }

    public static double rewindTicksExact(double rttMs) {
        return rttTicks(rttMs) + INTERPOLATION_TICKS_EXACT;
    }

    public static int rewindTicks(double rttMs) {
        return Math.max(0, (int) Math.round(rewindTicksExact(rttMs)));
    }

    public static double walkDistanceMs(double fromMs, double toMs) {
        return wrap(fromMs - toMs);
    }
}
