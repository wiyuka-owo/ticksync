package com.wiyuka.ticksync.client;

import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/** Click to first rendered displacement of a resting target, confirmed by our damage event. */
public final class AttackTrace {
    private static RemotePlayer target;
    private static Vec3 origin;
    private static long click, moved, lastResult;
    private static int elapsedMs;
    private static boolean confirmed;

    private AttackTrace() {}

    public static void start(RemotePlayer player) {
        target = null;
        if (player == null || player.getInterpolation().hasActiveInterpolation()
            || player.getPosition(0).distanceToSqr(player.position()) >= 1.0e-10) return;
        target = player;
        origin = player.position();
        click = System.nanoTime();
        moved = 0;
        confirmed = false;
    }

    public static void confirm(int targetId) {
        if (target == null || target.getId() != targetId) return;
        confirmed = true;
        finish(System.nanoTime());
    }

    public static void rendered(RemotePlayer player, double x, double y, double z) {
        if (target != player || moved != 0 || origin.distanceToSqr(x, y, z) < 1.0e-6) return;
        moved = System.nanoTime();
        finish(moved);
    }

    private static void finish(long now) {
        if (now - click > 1_500_000_000L) {
            target = null;
        } else if (confirmed && moved != 0) {
            elapsedMs = Math.round((moved - click) / 1_000_000f);
            lastResult = now;
            target = null;
        }
    }

    static int latestMs(long now) {
        if (target != null && now - click > 1_500_000_000L) target = null;
        return lastResult != 0 && now - lastResult < 6_000_000_000L ? elapsedMs : -1;
    }

    static void clear() {
        target = null;
        lastResult = 0;
    }
}
