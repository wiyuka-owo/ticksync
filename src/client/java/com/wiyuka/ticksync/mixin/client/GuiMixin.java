package com.wiyuka.ticksync.mixin.client;

import com.wiyuka.ticksync.client.PhaseHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"))
    private void ticksync$renderPhaseOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        PhaseHud.render(graphics);
    }
}
