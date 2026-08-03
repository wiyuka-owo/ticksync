package com.wiyuka.ticksync.immediate;

import com.wiyuka.ticksync.mixin.ChunkMapAccessor;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class ImmediateFlush {

    private static final int MAX_PENDING = 64;

    private static final List<Entity> PENDING = new ArrayList<>(4);

    private static volatile boolean enabled = true;

    private static long flushed;

    private ImmediateFlush() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long flushed() {
        return flushed;
    }

    public static void markVelocityChanged(Entity entity) {
        if (!enabled || PENDING.size() >= MAX_PENDING || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        for (Entity queued : PENDING) {
            if (queued == entity) {
                return;
            }
        }
        PENDING.add(entity);
    }

    public static void flushPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Entity entity : PENDING) {
            flush(entity);
        }
        PENDING.clear();
    }

    public static void discardPending() {
        PENDING.clear();
    }

    private static void flush(Entity entity) {
        if (!enabled || !entity.hurtMarked || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isTracked(level, entity)) {
            return;
        }
        entity.hurtMarked = false;
        level.getChunkSource().sendToTrackingPlayersAndSelf(entity,
            new ClientboundSetEntityMotionPacket(entity));
        flushed++;
    }

    private static boolean isTracked(ServerLevel level, Entity entity) {
        return ((ChunkMapAccessor) level.getChunkSource().chunkMap)
            .ticksync$entityMap().containsKey(entity.getId());
    }
}
