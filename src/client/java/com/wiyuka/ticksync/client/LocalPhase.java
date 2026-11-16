package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.mixin.ConnectionAccessor;
import com.wiyuka.ticksync.phase.PhaseTracker;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;

import java.util.UUID;

/** Local tick estimate for a visible timeline on servers without phase snapshots. */
final class LocalPhase extends ChannelInboundHandlerAdapter {
    private static LocalPhase active;
    private final Channel channel;
    private final PhaseTracker tracker = new PhaseTracker(new UUID(0, 0), "local");
    private long lastArrival, lastMarker;

    private LocalPhase(Channel channel) {
        this.channel = channel;
    }

    static void update(Connection connection) {
        Channel channel = connection == null ? null : ((ConnectionAccessor) connection).ticksync$channel();
        if (active != null && active.channel != channel) {
            LocalPhase old = active;
            active = null;
            old.channel.eventLoop().execute(() -> {
                if (old.channel.pipeline().context(old) != null) old.channel.pipeline().remove(old);
            });
        }
        if (channel == null) return;
        if (active == null) {
            LocalPhase sampler = new LocalPhase(channel);
            active = sampler;
            channel.eventLoop().execute(() -> {
                var handler = channel.pipeline().context(connection);
                if (handler != null) channel.pipeline().addBefore(handler.name(), null, sampler);
            });
        }
        active.tracker.drain(System.nanoTime(), true);
    }

    static double waitMs() {
        return active == null ? Double.NaN : active.tracker.livePhaseMs();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isMarker(msg)) {
            long now = System.nanoTime();
            if ((lastArrival == 0 || now - lastArrival > 8_000_000L)
                && (lastMarker == 0 || now - lastMarker > 25_000_000L)) {
                tracker.recordArrival(now);
                lastMarker = now;
            }
            lastArrival = now;
        }
        super.channelRead(ctx, msg);
    }

    private static boolean isMarker(Object packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var child : bundle.subPackets()) if (isMarker(child)) return true;
        }
        return packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundRotateHeadPacket
            || packet instanceof ClientboundSetEntityDataPacket;
    }
}
