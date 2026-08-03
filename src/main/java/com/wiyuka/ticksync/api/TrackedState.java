package com.wiyuka.ticksync.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TrackedState(int tick, Vec3 position, AABB bounds, float yRot, float xRot) {
}
