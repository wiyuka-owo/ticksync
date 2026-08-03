package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.api.ImmediatePackets;
import com.wiyuka.ticksync.immediate.ImmediateFlush;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class ListenerAndPacketMixin {

    @Shadow
    public abstract PacketListener listener();

    @Shadow
    public abstract Packet<?> packet();

    @Inject(
        method = "handle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void ticksync$dispatchImmediate(CallbackInfo ci) {
        ImmediatePackets.dispatch(listener(), packet());
    }

    @Inject(method = "handle", at = @At("TAIL"))
    private void ticksync$flushVelocities(CallbackInfo ci) {
        ImmediateFlush.flushPending();
    }
}
