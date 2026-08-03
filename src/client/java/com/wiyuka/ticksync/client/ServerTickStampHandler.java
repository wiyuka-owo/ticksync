package com.wiyuka.ticksync.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;

public final class ServerTickStampHandler extends ChannelInboundHandlerAdapter {

    public static final String NAME = "ticksync_server_tick_stamp";

    private static final long GAP_NANOS = 8_000_000L;

    private static final long MIN_SPACING_NANOS = 25_000_000L;

    private boolean seenArrival;
    private boolean seenMarker;
    private long lastArrivalNanos;
    private long lastMarkerNanos;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isTracking(msg)) {
            long now = System.nanoTime();
            boolean quiet = !seenArrival || now - lastArrivalNanos > GAP_NANOS;
            boolean spaced = !seenMarker || now - lastMarkerNanos > MIN_SPACING_NANOS;
            if (quiet && spaced) {
                lastMarkerNanos = now;
                seenMarker = true;
                LocalPhase.record(now);
            }
            lastArrivalNanos = now;
            seenArrival = true;
        }
        super.channelRead(ctx, msg);
    }

    private static boolean isTracking(Object msg) {
        return msg instanceof ClientboundMoveEntityPacket
            || msg instanceof ClientboundRotateHeadPacket
            || msg instanceof ClientboundSetEntityDataPacket;
    }
}
