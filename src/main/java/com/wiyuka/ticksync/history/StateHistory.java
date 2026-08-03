package com.wiyuka.ticksync.history;

import com.wiyuka.ticksync.api.TrackedState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StateHistory {

    public static final int DEPTH_TICKS = 40;

    private final Map<UUID, PlayerHistory> histories = new ConcurrentHashMap<>();

    public void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            histories
                .computeIfAbsent(player.getUUID(), id -> new PlayerHistory(DEPTH_TICKS))
                .record(tick, player);
        }
    }

    public void detach(UUID playerId) {
        histories.remove(playerId);
    }

    public void clear() {
        histories.clear();
    }

    public Optional<TrackedState> at(UUID playerId, int tick) {
        PlayerHistory history = histories.get(playerId);
        return history == null ? Optional.empty() : history.at(tick);
    }

    public Optional<TrackedState> ticksAgo(UUID playerId, int ticks) {
        PlayerHistory history = histories.get(playerId);
        if (history == null || ticks < 0) {
            return Optional.empty();
        }
        return history.at(history.newestTick() - ticks);
    }
}
