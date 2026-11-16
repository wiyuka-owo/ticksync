package com.wiyuka.ticksync.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TrackedState(
    int tick,
    Vec3 position,
    AABB bounds,
    Vec3 broadcastPosition,
    float yRot,
    float xRot
) {

    public AABB broadcastBounds() {
        return bounds.move(broadcastPosition.subtract(position));
    }
}
