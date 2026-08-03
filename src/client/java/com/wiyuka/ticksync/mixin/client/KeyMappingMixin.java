package com.wiyuka.ticksync.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wiyuka.ticksync.client.AttackTrace;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Inject(method = "set", at = @At("HEAD"))
    private static void ticksync$onKeySet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        if (!held) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        KeyMapping attack = mc.options.keyAttack;
        if (!key.equals(((KeyMappingAccessor) attack).ticksync$boundKey())) {
            return;
        }

        int targetId = -1;
        if (mc.hitResult instanceof EntityHitResult hit) {
            targetId = hit.getEntity().getId();
        }
        AttackTrace.onAttackKeyPress(targetId, mc.player.getId(), System.nanoTime());
    }
}
