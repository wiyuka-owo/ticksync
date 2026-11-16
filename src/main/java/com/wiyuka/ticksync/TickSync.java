package com.wiyuka.ticksync;

import com.wiyuka.ticksync.command.TickSyncCommand;
import com.wiyuka.ticksync.config.TickSyncConfig;
import com.wiyuka.ticksync.control.AlignMode;
import com.wiyuka.ticksync.control.PhaseController;
import com.wiyuka.ticksync.history.StateHistory;
import com.wiyuka.ticksync.immediate.GapProcessing;
import com.wiyuka.ticksync.immediate.FirstMoveSync;
import com.wiyuka.ticksync.immediate.ImmediateFlush;
import com.wiyuka.ticksync.net.GhostBoxPayload;
import com.wiyuka.ticksync.net.HudSubscriptionPayload;
import com.wiyuka.ticksync.net.PhaseBroadcaster;
import com.wiyuka.ticksync.net.PhaseSnapshotPayload;
import com.wiyuka.ticksync.phase.PhaseRegistry;
import com.wiyuka.ticksync.phase.PhaseTracker;
import com.wiyuka.ticksync.ratespoof.RateSpoofManager;
import com.wiyuka.ticksync.viz.GhostViz;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class TickSync implements ModInitializer {

    public static final String MOD_ID = "ticksync";
    public static final Logger LOGGER = LoggerFactory.getLogger("TickSync");

    private static final long NOMINAL_TICK_NANOS = 50_000_000L;

    private static final long GRID_TOLERANCE_NANOS = 5_000_000L;
    private static final int PROBE_INTERVAL_TICKS = 5;

    private static TickSync instance;

    private final PhaseRegistry registry = new PhaseRegistry();
    private final RateSpoofManager rateSpoof = new RateSpoofManager();
    private final PhaseController controller = new PhaseController();
    private final StateHistory history = new StateHistory();
    private final TickSyncConfig config = new TickSyncConfig();

    private long tickStartNanos;

    public TickSyncConfig config() {
        return config;
    }

    public void setGapEnabled(MinecraftServer server, boolean enabled) {
        GapProcessing.setEnabled(enabled);
        config.gap = enabled;
        config.save(server);
    }

    public void setFlushEnabled(MinecraftServer server, boolean enabled) {
        ImmediateFlush.setEnabled(enabled);
        config.flush = enabled;
        config.save(server);
    }

    public void setFirstMove(MinecraftServer server, boolean enabled) {
        FirstMoveSync.setEnabled(enabled);
        config.firstMove = enabled;
        config.save(server);
    }

    public void setAutoAlign(MinecraftServer server, boolean enabled) {
        controller.setAuto(enabled);
        config.autoAlign = enabled;
        config.save(server);
    }

    public void setMode(MinecraftServer server, AlignMode mode) {
        controller.setMode(mode);
        config.mode = mode.id();
        config.save(server);
    }

    public static TickSync get() {
        return instance;
    }

    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    public PhaseRegistry registry() {
        return registry;
    }

    public RateSpoofManager rateSpoof() {
        return rateSpoof;
    }

    public PhaseController controller() {
        return controller;
    }

    public StateHistory history() {
        return history;
    }

    @Override
    public void onInitialize() {
        instance = this;

        PayloadTypeRegistry.playS2C().register(PhaseSnapshotPayload.TYPE, PhaseSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(GhostBoxPayload.TYPE, GhostBoxPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(HudSubscriptionPayload.TYPE, HudSubscriptionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HudSubscriptionPayload.TYPE,
            (payload, context) -> PhaseBroadcaster.subscribe(context.player().getUUID(), payload.visible()));

        ServerTickEvents.START_SERVER_TICK.register(this::onTickStart);
        ServerTickEvents.END_SERVER_TICK.register(this::onTickEnd);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            controller.release(server, handler.player);
            rateSpoof.release(server, handler.player);
            registry.detach(id);
            history.detach(id);
            FirstMoveSync.discard(id);
            GhostViz.disable(id);
            PhaseBroadcaster.discardHud(id);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            config.readFrom(TickSyncConfig.load(server));
            config.apply(controller);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GapProcessing.bind(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            config.readFrom(controller);
            config.save(server);
            GapProcessing.unbind();
            rateSpoof.stop(server);
            controller.cancelAll(server);
            registry.clear();
            history.clear();
            FirstMoveSync.setEnabled(false);
            GhostViz.clear();
            PhaseBroadcaster.clearHudWatches();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            TickSyncCommand.register(dispatcher));

        LOGGER.info("TickSync {}", modVersion());
    }

    private void onTickStart(MinecraftServer server) {
        ImmediateFlush.discardPending();

        long now = System.nanoTime();
        long interval = tickStartNanos == 0 ? -1 : now - tickStartNanos;
        boolean gridClean = interval > 0
            && Math.abs(interval - NOMINAL_TICK_NANOS) <= GRID_TOLERANCE_NANOS;

        int tick = server.getTickCount();

        boolean probeTick = tick % PROBE_INTERVAL_TICKS == 0;
        if (probeTick) {
            registry.update(server, config.diagnostics || controller.auto(), PhaseBroadcaster.subjects());
        }
        var trackers = registry.all();
        for (PhaseTracker tracker : trackers) {
            tracker.drain(now, gridClean);
        }

        tickStartNanos = now;
        rateSpoof.tick(server, tick);
        controller.tick(server, trackers, tick);
        if (probeTick) PhaseBroadcaster.broadcast(server, controller);
    }

    private void onTickEnd(MinecraftServer server) {
        FirstMoveSync.expire();
        history.tick(server, server.getTickCount(), config.diagnostics || GhostViz.active());
        GhostViz.tick(server, history);
    }

}
