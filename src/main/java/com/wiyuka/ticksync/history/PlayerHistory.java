package com.wiyuka.ticksync.history;

import com.wiyuka.ticksync.api.TrackedState;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

final class PlayerHistory {

    private final int depth;
    private final TrackedState[] states;
    private int cursor;
    private int newestTick = Integer.MIN_VALUE;

    PlayerHistory(int depth) {
        this.depth = depth;
        this.states = new TrackedState[depth];
    }

    void record(int tick, ServerPlayer player) {
        states[cursor] = new TrackedState(
            tick, player.position(), player.getBoundingBox(), player.getYRot(), player.getXRot());
        cursor = (cursor + 1) % depth;
        newestTick = tick;
    }

    Optional<TrackedState> at(int tick) {
        int age = newestTick - tick;
        if (age < 0 || age >= depth) {
            return Optional.empty();
        }
        int index = Math.floorMod(cursor - 1 - age, depth);
        TrackedState state = states[index];
        return state != null && state.tick() == tick ? Optional.of(state) : Optional.empty();
    }

    int newestTick() {
        return newestTick;
    }
}
