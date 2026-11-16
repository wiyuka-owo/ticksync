package com.wiyuka.ticksync.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wiyuka.ticksync.net.GhostBoxPayload;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public final class GhostRenderer {

    private static final int SEEN_COLOR = 0xCC3DDC84;
    private static final int LIVE_COLOR = 0x99E8503A;

    private static final float LINE_WIDTH = 3.0f;

    private static final long EXPIRY_NANOS = 500_000_000L;

    private static volatile List<GhostBoxPayload.Entry> entries = List.of();

    private static volatile long receivedNanos;

    private GhostRenderer() {
    }

    public static void accept(GhostBoxPayload payload) {
        entries = payload.entries();
        receivedNanos = System.nanoTime();
    }

    public static void clear() {
        entries = List.of();
        receivedNanos = 0L;
    }

    public static void render(WorldRenderContext context) {
        List<GhostBoxPayload.Entry> current = entries;
        if (current.isEmpty() || System.nanoTime() - receivedNanos > EXPIRY_NANOS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack poses = context.matrices();
        if (consumers == null || poses == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());

        for (GhostBoxPayload.Entry entry : current) {
            Entity target = mc.level.getEntity(entry.targetEntityId());
            if (target == null) {
                continue;
            }
            AABB shape = target.getBoundingBox();
            Vec3 origin = target.position();
            box(poses, lines, shape.move(entry.seenPosition().subtract(origin)), camera, SEEN_COLOR);
            box(poses, lines, shape.move(entry.livePosition().subtract(origin)), camera, LIVE_COLOR);
        }
    }

    private static void box(
        PoseStack poses, VertexConsumer lines, AABB box, Vec3 camera, int color) {

        double minX = box.minX - camera.x;
        double minY = box.minY - camera.y;
        double minZ = box.minZ - camera.z;
        double maxX = box.maxX - camera.x;
        double maxY = box.maxY - camera.y;
        double maxZ = box.maxZ - camera.z;

        edge(poses, lines, minX, minY, minZ, maxX, minY, minZ, color);
        edge(poses, lines, maxX, minY, minZ, maxX, minY, maxZ, color);
        edge(poses, lines, maxX, minY, maxZ, minX, minY, maxZ, color);
        edge(poses, lines, minX, minY, maxZ, minX, minY, minZ, color);

        edge(poses, lines, minX, maxY, minZ, maxX, maxY, minZ, color);
        edge(poses, lines, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        edge(poses, lines, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        edge(poses, lines, minX, maxY, maxZ, minX, maxY, minZ, color);

        edge(poses, lines, minX, minY, minZ, minX, maxY, minZ, color);
        edge(poses, lines, maxX, minY, minZ, maxX, maxY, minZ, color);
        edge(poses, lines, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        edge(poses, lines, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private static void edge(
        PoseStack poses, VertexConsumer lines,
        double x1, double y1, double z1, double x2, double y2, double z2, int color) {

        PoseStack.Pose pose = poses.last();
        Vector3f normal = new Vector3f(
            (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
        lines.addVertex(pose, (float) x1, (float) y1, (float) z1)
            .setColor(color).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
        lines.addVertex(pose, (float) x2, (float) y2, (float) z2)
            .setColor(color).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
    }
}
