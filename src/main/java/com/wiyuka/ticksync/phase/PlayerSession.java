package com.wiyuka.ticksync.phase;

import java.util.UUID;

public final class PlayerSession {

    private final UUID playerId;
    private final String playerName;
    private final PhaseTracker phase;
    private final RttTracker rtt = new RttTracker();

    public PlayerSession(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.phase = new PhaseTracker(playerId, playerName);
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public PhaseTracker phase() {
        return phase;
    }

    public RttTracker rtt() {
        return rtt;
    }

    public void reset() {
        phase.reset();
        rtt.reset();
    }
}
