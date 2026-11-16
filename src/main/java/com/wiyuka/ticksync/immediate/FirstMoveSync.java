package com.wiyuka.ticksync.immediate;

import com.wiyuka.ticksync.mixin.ChunkMapAccessor;
import com.wiyuka.ticksync.mixin.TrackedEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes the first accepted displacement after a player's melee knockback. */
public final class FirstMoveSync {
    /** Squared displacement threshold used by ServerEntity.sendChanges. */
    public static final double MIN_MOVE_SQR = 7.62939453125E-6;
    private static final int MAX_WAIT_TICKS = 10;

    public interface Tracker {
        boolean ticksync$sendFirstMove();
    }

    private record Hit(ServerPlayer player, ServerLevel level, Vec3 position, int tick) {
        boolean expired() {
            return player.isRemoved() || player.tickCount - tick > MAX_WAIT_TICKS;
        }
    }

    private static final Map<UUID, Hit> pending = new HashMap<>();
    private static boolean enabled;
    private static long sent;

    private FirstMoveSync() {}

    public static boolean enabled() {
        return enabled;
    }

    public static long sent() {
        return sent;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) pending.clear();
    }

    public static void discard(UUID id) {
        pending.remove(id);
    }

    public static void onVelocitySent(ServerPlayer victim) {
        if (enabled && !victim.isPassenger() && victim.isAlive()) {
            pending.put(victim.getUUID(), new Hit(victim, victim.level(), victim.position(), victim.tickCount));
        }
    }

    public static void expire() {
        if (!pending.isEmpty()) pending.values().removeIf(Hit::expired);
    }

    public static void onAcceptedPosition(ServerPlayer player) {
        if (!enabled || pending.isEmpty()) return;
        UUID id = player.getUUID();
        Hit hit = pending.get(id);
        if (hit == null) return;
        if (hit.player != player || hit.level != player.level() || hit.expired() || !player.isAlive() || player.isPassenger()) {
            discard(id);
            return;
        }
        if (hit.position.distanceToSqr(player.position()) < MIN_MOVE_SQR) return;

        pending.remove(id);
        Object entry = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap)
            .ticksync$entityMap().get(player.getId());
        if (entry instanceof TrackedEntityAccessor tracked && ((Tracker) tracked.ticksync$serverEntity()).ticksync$sendFirstMove()) {
            sent++;
        }
    }
}
