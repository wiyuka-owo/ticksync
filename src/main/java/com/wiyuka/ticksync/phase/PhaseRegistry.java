package com.wiyuka.ticksync.phase;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.mixin.ConnectionAccessor;
import com.wiyuka.ticksync.mixin.ServerCommonPacketListenerAccessor;
import com.wiyuka.ticksync.net.ArrivalStampHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhaseRegistry {

    private static final int PROBE_INTERVAL_TICKS = 5;

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public void attach(ServerGamePacketListenerImpl handler) {
        ServerPlayer player = handler.player;
        UUID id = player.getUUID();
        PlayerSession session = new PlayerSession(id, player.getName().getString());
        sessions.put(id, session);

        Connection connection = ((ServerCommonPacketListenerAccessor) handler).ticksync$connection();
        Channel channel = ((ConnectionAccessor) connection).ticksync$channel();
        if (channel == null) {
            TickSync.LOGGER.warn("No channel for {}; phase sampling disabled for this session", session.playerName());
            return;
        }
        channel.eventLoop().execute(() -> install(channel, connection, session));
    }

    private void install(Channel channel, Connection connection, PlayerSession session) {
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(ArrivalStampHandler.NAME) != null) {
                return;
            }
            ArrivalStampHandler stamper = new ArrivalStampHandler(session);
            ChannelHandlerContext ctx = pipeline.context(connection);
            if (ctx != null) {
                pipeline.addBefore(ctx.name(), ArrivalStampHandler.NAME, stamper);
            } else {
                pipeline.addLast(ArrivalStampHandler.NAME, stamper);
            }
        } catch (Throwable t) {
            TickSync.LOGGER.warn("Failed to install arrival stamper for {}", session.playerName(), t);
        }
    }

    public void sendRttProbes(MinecraftServer server, int tick) {
        if (tick % PROBE_INTERVAL_TICKS != 0) {
            return;
        }
        for (PlayerSession session : sessions.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player != null) {
                player.connection.send(new ClientboundPingPacket(session.rtt().nextProbeId()));
            }
        }
    }

    public void detach(UUID playerId) {
        sessions.remove(playerId);
    }

    public PlayerSession session(UUID playerId) {
        return sessions.get(playerId);
    }

    public PhaseTracker get(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        return session == null ? null : session.phase();
    }

    public Collection<PhaseTracker> all() {
        return sessions.values().stream().map(PlayerSession::phase).toList();
    }

    public void resetAll() {
        sessions.values().forEach(PlayerSession::reset);
    }

    public void clear() {
        sessions.clear();
    }

    public double rttMs(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) {
            return -1.0;
        }
        double measured = session.rtt().rttMs();
        return Double.isNaN(measured) ? -1.0 : measured;
    }
}
