package com.wiyuka.ticksync.api;

import com.wiyuka.ticksync.TickSync;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ImmediatePackets {

    @FunctionalInterface
    public interface Listener<P> {
        void onPacket(ServerPlayer player, P packet);
    }

    private static final Map<Class<?>, CopyOnWriteArrayList<Listener<?>>> BY_TYPE = new ConcurrentHashMap<>();

    private ImmediatePackets() {
    }

    public static <P extends Packet<?>> void register(Class<P> type, Listener<? super P> listener) {
        BY_TYPE.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public static <P extends Packet<?>> boolean unregister(Class<P> type, Listener<? super P> listener) {
        List<Listener<?>> registered = BY_TYPE.get(type);
        return registered != null && registered.remove(listener);
    }

    @SuppressWarnings("unchecked")
    public static void dispatch(PacketListener listener, Packet<?> packet) {
        if (BY_TYPE.isEmpty() || !(listener instanceof ServerGamePacketListenerImpl connection)) {
            return;
        }
        List<Listener<?>> registered = BY_TYPE.get(packet.getClass());
        if (registered == null) {
            return;
        }

        ServerPlayer player = connection.player;
        for (Listener<?> entry : registered) {
            try {
                ((Listener<Packet<?>>) entry).onPacket(player, packet);
            } catch (Throwable t) {
                TickSync.LOGGER.error("Immediate packet listener failed for {}",
                    packet.getClass().getName(), t);
            }
        }
    }
}
