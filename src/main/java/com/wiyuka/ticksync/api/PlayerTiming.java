package com.wiyuka.ticksync.api;

import com.wiyuka.ticksync.phase.PhaseMath;

import java.util.UUID;

public record PlayerTiming(
    UUID playerId,
    double outboundWaitMs,
    double liveOutboundWaitMs,
    double spreadMs,
    double rttMs,
    int samples,
    Confidence confidence
) {

    public double returnWaitMs() {
        return PhaseMath.returnWaitMs(outboundWaitMs, rttMs);
    }

    public double totalWaitMs() {
        return PhaseMath.totalWaitMs(outboundWaitMs, rttMs);
    }

    public double cliffMs() {
        return PhaseMath.cliffMs(rttMs);
    }

    public boolean cheapBranch() {
        return totalWaitMs() < PhaseMath.PERIOD_MS;
    }

    public double wastedMs() {
        return cheapBranch() ? 0.0 : PhaseMath.PERIOD_MS;
    }

    public int stalenessTicks() {
        return PhaseMath.stalenessTicks(outboundWaitMs, rttMs);
    }

    public double stalenessMs() {
        return stalenessTicks() * PhaseMath.PERIOD_MS;
    }

    public int stalenessTicksFor(double arrivalOutboundWaitMs) {
        return PhaseMath.stalenessTicks(arrivalOutboundWaitMs, rttMs);
    }
}
