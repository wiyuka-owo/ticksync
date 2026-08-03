package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.GapProcessing;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "pollTask", at = @At("HEAD"))
    private void ticksync$drainPacketsInGap(CallbackInfoReturnable<Boolean> cir) {
        GapProcessing.drainInGap((MinecraftServer) (Object) this);
    }
}
