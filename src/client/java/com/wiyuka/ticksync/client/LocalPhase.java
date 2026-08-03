package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.mixin.ConnectionAccessor;
import com.wiyuka.ticksync.mixin.client.ClientListenerConnectionAccessor;
import com.wiyuka.ticksync.phase.PhaseTracker;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.Connection;

import java.util.UUID;

public final class LocalPhase {

    private static final UUID LOCAL = new UUID(0L, 0L);

    private static volatile PhaseTracker tracker;

    private LocalPhase() {
    }

    public static PhaseTracker tracker() {
        return tracker;
    }

    static void record(long arrivalNanos) {
        PhaseTracker current = tracker;
        if (current != null) {
            current.recordArrival(arrivalNanos);
        }
    }

    public static int rttMs() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection == null || client.player == null) {
            return -1;
        }
        PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    public static void attach(ClientPacketListener handler) {
        tracker = new PhaseTracker(LOCAL, "local");
        try {
            Connection connection = ((ClientListenerConnectionAccessor) handler).ticksync$connection();
            Channel channel = ((ConnectionAccessor) connection).ticksync$channel();
            if (channel == null) {
                TickSync.LOGGER.warn("No channel on this connection; local phase measurement disabled");
                return;
            }
            channel.eventLoop().execute(() -> install(channel, connection));
        } catch (Throwable t) {
            TickSync.LOGGER.error("Could not reach the connection; local phase measurement disabled", t);
        }
    }

    private static void install(Channel channel, Connection connection) {
        try {
            ChannelPipeline pipeline = channel.pipeline();
            ChannelHandlerContext ctx = pipeline.context(connection);
            if (pipeline.get(ServerTickStampHandler.NAME) == null) {
                ServerTickStampHandler stamper = new ServerTickStampHandler();
                if (ctx != null) {
                    pipeline.addBefore(ctx.name(), ServerTickStampHandler.NAME, stamper);
                } else {
                    TickSync.LOGGER.warn("Connection not found in its own pipeline; appending stamper at the end");
                    pipeline.addLast(ServerTickStampHandler.NAME, stamper);
                }
            }
            if (pipeline.get(AttackTraceHandler.NAME) == null) {
                AttackTraceHandler attack = new AttackTraceHandler();
                ChannelHandlerContext again = pipeline.context(connection);
                if (again != null) {
                    pipeline.addBefore(again.name(), AttackTraceHandler.NAME, attack);
                } else {
                    pipeline.addLast(AttackTraceHandler.NAME, attack);
                }
            }
            TickSync.LOGGER.info("Local phase handlers installed on {}", pipeline.names());
        } catch (Throwable t) {
            TickSync.LOGGER.error("Failed to install client netty handlers", t);
        }
    }

    public static void detach() {
        tracker = null;
        AttackTrace.clear();
    }

    public static void onTickStart() {
        PhaseTracker current = tracker;
        if (current != null) {
            current.drain(System.nanoTime(), true);
        }
    }
}
