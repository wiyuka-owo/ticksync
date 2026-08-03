package com.wiyuka.ticksync.net;

import com.wiyuka.ticksync.phase.PlayerSession;
import com.wiyuka.ticksync.phase.RttTracker;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;

public final class ArrivalStampHandler extends ChannelDuplexHandler {

    public static final String NAME = "ticksync_arrival_stamp";

    private final PlayerSession session;

    public ArrivalStampHandler(PlayerSession session) {
        this.session = session;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ServerboundClientTickEndPacket) {
            session.phase().recordArrival(System.nanoTime());
        } else if (msg instanceof ServerboundPongPacket pong && RttTracker.isProbe(pong.getId())) {
            session.rtt().recordReply(pong.getId(), System.nanoTime());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ClientboundPingPacket ping && RttTracker.isProbe(ping.getId())) {
            session.rtt().recordSent(ping.getId(), System.nanoTime());
        }
        super.write(ctx, msg, promise);
    }
}
