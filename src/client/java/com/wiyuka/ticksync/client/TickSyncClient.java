package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.net.GhostBoxPayload;
import com.wiyuka.ticksync.net.PhaseSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F6;

public final class TickSyncClient implements ClientModInitializer {

    private static final KeyMapping TOGGLE = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.ticksync.toggle", GLFW_KEY_F6, KeyMapping.Category.MISC));

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PhaseSnapshotPayload.TYPE,
            (payload, context) -> PhaseHud.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(GhostBoxPayload.TYPE,
            (payload, context) -> GhostRenderer.accept(payload));

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(GhostRenderer::render);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PhaseHud.clear();
            GhostRenderer.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) {
                PhaseHud.toggle();
            }
            PhaseHud.updateSubscription();
        });
    }
}
