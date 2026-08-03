package com.wiyuka.ticksync.immediate;

import com.wiyuka.ticksync.mixin.MinecraftServerAccessor;
import com.wiyuka.ticksync.mixin.PacketProcessorAccessor;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.locks.LockSupport;

public final class GapProcessing {

    private static volatile boolean enabled;

    private static volatile PacketProcessor serverProcessor;

    private static long drains;

    private static boolean draining;

    private GapProcessing() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long drains() {
        return drains;
    }

    public static void bind(MinecraftServer server) {
        serverProcessor = server.packetProcessor();
    }

    public static void unbind() {
        serverProcessor = null;
    }

    public static void onScheduled(PacketProcessor processor) {
        if (!enabled || processor != serverProcessor) {
            return;
        }
        LockSupport.unpark(((PacketProcessorAccessor) processor).ticksync$runningThread());
    }

    public static void drainInGap(MinecraftServer server) {
        if (!enabled || draining
            || !((MinecraftServerAccessor) server).ticksync$waitingForNextTick()) {
            return;
        }
        PacketProcessor processor = server.packetProcessor();
        if (((PacketProcessorAccessor) processor).ticksync$queue().isEmpty()) {
            return;
        }

        draining = true;
        try {
            processor.processQueuedPackets();
        } finally {
            draining = false;
        }
        drains++;
    }
}
