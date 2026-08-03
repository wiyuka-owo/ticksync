package com.wiyuka.ticksync.ratespoof;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.PhaseController;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RateSpoofManager {

    private UUID targetId;
    private String targetName;
    private int endTick;

    public boolean active() {
        return targetId != null;
    }

    public String targetName() {
        return targetName;
    }

    public boolean start(ServerPlayer player, float rate, int durationTicks, int currentTick) {
        if (active()) {
            return false;
        }
        targetId = player.getUUID();
        targetName = player.getName().getString();
        endTick = currentTick + durationTicks;
        player.connection.send(new ClientboundTickingStatePacket(rate, false));
        TickSync.LOGGER.info("Rate spoof started: {} at {} TPS for {} ticks", targetName, rate, durationTicks);
        return true;
    }

    public void tick(MinecraftServer server, int currentTick) {
        if (active() && currentTick >= endTick) {
            stop(server);
        }
    }

    public void release(MinecraftServer server, ServerPlayer player) {
        if (!active() || !player.getUUID().equals(targetId)) {
            return;
        }
        PhaseController.sendServerTickState(server, player);
        TickSync.LOGGER.info("Rate spoof released: {} left mid-spoof", targetName);
        targetId = null;
        targetName = null;
    }

    public void stop(MinecraftServer server) {
        if (!active()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player != null) {
            float rate = server.tickRateManager().tickrate();
            player.connection.send(new ClientboundTickingStatePacket(rate, server.tickRateManager().isFrozen()));
        }
        TickSync.LOGGER.info("Rate spoof ended: {} restored to {} TPS", targetName, server.tickRateManager().tickrate());
        targetId = null;
        targetName = null;
    }
}
