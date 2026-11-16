package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.FirstMoveSync;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin implements FirstMoveSync.Tracker {

    @Shadow
    @Final
    private Entity entity;

    @Shadow @Final private VecDeltaCodec positionCodec;
    @Shadow @Final private ServerEntity.Synchronizer synchronizer;
    @Shadow private boolean wasOnGround;
    @Shadow private boolean wasRiding;
    @Shadow private int teleportDelay;
    @Shadow private byte lastSentYRot;
    @Shadow private byte lastSentXRot;

    @Override
    public boolean ticksync$sendFirstMove() {
        if (wasRiding || positionCodec.delta(entity.position()).lengthSqr() < FirstMoveSync.MIN_MOVE_SQR) {
            return false;
        }
        // One absolute position for all observers. Keep vanilla's next relative
        // delta and new pairings on exactly the position in the packet.
        ClientboundEntityPositionSyncPacket packet = ClientboundEntityPositionSyncPacket.of(entity);
        if (!packet.values().position().equals(entity.position())) {
            return false;
        }
        synchronizer.sendToTrackingPlayers(packet);
        positionCodec.setBase(packet.values().position());
        wasOnGround = packet.onGround();
        teleportDelay = 0;
        lastSentYRot = Mth.packDegrees(packet.values().yRot());
        lastSentXRot = Mth.packDegrees(packet.values().xRot());
        return true;
    }
}
