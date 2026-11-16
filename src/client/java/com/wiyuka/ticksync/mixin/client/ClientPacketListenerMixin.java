package com.wiyuka.ticksync.mixin.client;

import com.wiyuka.ticksync.client.AttackTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
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
        var player = Minecraft.getInstance().player;
        if (player != null && packet.sourceCauseId() == player.getId()) AttackTrace.confirm(packet.entityId());
    }
}
