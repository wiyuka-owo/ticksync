package com.wiyuka.ticksync.mixin.client;

import com.wiyuka.ticksync.client.AttackTrace;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
        method = "handleDamageEvent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void ticksync$onDamageProcessed(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        AttackTrace.onHurtAnimationProcessed(packet.entityId(), System.nanoTime());
    }

    @Inject(
        method = "handleHurtAnimation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;animateHurt(F)V"))
    private void ticksync$onHurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
        AttackTrace.onHurtAnimationProcessed(packet.id(), System.nanoTime());
    }
}
