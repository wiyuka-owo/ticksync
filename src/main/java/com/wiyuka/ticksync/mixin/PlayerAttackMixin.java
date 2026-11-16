package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.FirstMoveSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Inject(method = "causeExtraKnockback", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
        shift = At.Shift.AFTER))
    private void ticksync$afterMeleeImpulse(Entity target, float strength, Vec3 oldVelocity, CallbackInfo ci) {
        FirstMoveSync.onVelocitySent((ServerPlayer) target);
    }
}
