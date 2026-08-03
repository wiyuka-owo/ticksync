package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.GapProcessing;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @Inject(method = "scheduleIfPossible", at = @At("TAIL"))
    private void ticksync$wakeForGapDrain(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
        GapProcessing.onScheduled((PacketProcessor) (Object) this);
    }
}
