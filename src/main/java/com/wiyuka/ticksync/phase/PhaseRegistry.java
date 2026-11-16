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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhaseRegistry {

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestedPlayers = new ConcurrentHashMap<>();
    private static final long SAMPLE_WINDOW_NANOS = 60_000_000_000L;
    private volatile long requestedUntil;

    /** Keep the phase window warm for stats, one-shot alignment and timing API queries. */
    public void requestSamples() {
        requestedUntil = System.nanoTime() + SAMPLE_WINDOW_NANOS;
    }

    public void requestSamples(UUID playerId) {
        requestedPlayers.put(playerId, System.nanoTime() + SAMPLE_WINDOW_NANOS);
    }

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

    public void update(MinecraftServer server, boolean allPlayers, Set<UUID> subjects) {
        long now = System.nanoTime();
        long until = requestedUntil;
        allPlayers = allPlayers || (until != 0 && now - until < 0);
        if (!requestedPlayers.isEmpty()) requestedPlayers.values().removeIf(end -> now - end >= 0);
        if (!allPlayers && subjects.isEmpty() && requestedPlayers.isEmpty() && sessions.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (allPlayers || subjects.contains(id) || requestedPlayers.containsKey(id)) {
                if (!sessions.containsKey(id)) attach(player.connection);
                player.connection.send(new ClientboundPingPacket(sessions.get(id).rtt().nextProbeId()));
            } else if (sessions.remove(id) != null) {
                Connection conn = ((ServerCommonPacketListenerAccessor) player.connection).ticksync$connection();
                Channel channel = ((ConnectionAccessor) conn).ticksync$channel();
                if (channel != null) channel.eventLoop().execute(() -> {
                    if (channel.pipeline().get(ArrivalStampHandler.NAME) != null) {
                        channel.pipeline().remove(ArrivalStampHandler.NAME);
                    }
                });
            }
        }
    }

    public void detach(UUID playerId) {
        sessions.remove(playerId);
        requestedPlayers.remove(playerId);
    }

    public PlayerSession session(UUID playerId) {
        return sessions.get(playerId);
    }

    public PhaseTracker get(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        return session == null ? null : session.phase();
    }

    public Collection<PhaseTracker> all() {
        return sessions.isEmpty() ? List.of() : sessions.values().stream().map(PlayerSession::phase).toList();
    }

    public void resetAll() {
        sessions.values().forEach(PlayerSession::reset);
    }

    public void clear() {
        sessions.clear();
        requestedPlayers.clear();
        requestedUntil = 0;
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
