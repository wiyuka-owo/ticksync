package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.FirstMoveSync;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AcceptedMoveMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V", shift = At.Shift.AFTER))
    private void ticksync$afterAcceptedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (packet.hasPosition()) FirstMoveSync.onAcceptedPosition(player);
    }

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void ticksync$cancelFirstMove(CallbackInfo ci) {
        FirstMoveSync.discard(player.getUUID());
    }
}
