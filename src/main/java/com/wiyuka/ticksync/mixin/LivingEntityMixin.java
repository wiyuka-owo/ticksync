package com.wiyuka.ticksync.mixin;

import com.wiyuka.ticksync.immediate.ImmediateFlush;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "knockback(DDD)V", at = @At("TAIL"))
    private void ticksync$markKnockback(double strength, double x, double z, CallbackInfo ci) {
        ImmediateFlush.markVelocityChanged((LivingEntity) (Object) this);
    }
}
