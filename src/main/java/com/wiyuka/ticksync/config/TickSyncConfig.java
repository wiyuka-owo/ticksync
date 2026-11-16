package com.wiyuka.ticksync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.AlignMode;
import com.wiyuka.ticksync.control.PhaseController;
import com.wiyuka.ticksync.immediate.GapProcessing;
import com.wiyuka.ticksync.immediate.FirstMoveSync;
import com.wiyuka.ticksync.immediate.ImmediateFlush;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TickSyncConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean gap = true;
    public boolean flush = true;
    public boolean firstMove = true;
    public boolean diagnostics;
    public boolean autoAlign;
    public String mode = AlignMode.C2S.id();

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory().resolve("config/ticksync.json");
    }

    public static TickSyncConfig load(MinecraftServer server) {
        Path path = path(server);
        if (!Files.isRegularFile(path)) {
            TickSyncConfig defaults = new TickSyncConfig();
            defaults.save(server);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            TickSyncConfig loaded = GSON.fromJson(reader, TickSyncConfig.class);
            if (loaded == null) {
                return new TickSyncConfig();
            }
            if (loaded.mode == null || AlignMode.byId(loaded.mode) == null) {
                loaded.mode = AlignMode.C2S.id();
            }
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
            TickSync.LOGGER.warn("Failed to read {}, using defaults", path, e);
            return new TickSyncConfig();
        }
    }

    public void save(MinecraftServer server) {
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            TickSync.LOGGER.warn("Failed to write {}", path, e);
        }
    }

    public void apply(PhaseController controller) {
        GapProcessing.setEnabled(gap);
        ImmediateFlush.setEnabled(flush);
        FirstMoveSync.setEnabled(firstMove);
        controller.setAuto(autoAlign);
        AlignMode resolved = AlignMode.byId(mode);
        controller.setMode(resolved == null ? AlignMode.C2S : resolved);
    }

    public void readFrom(TickSyncConfig other) {
        gap = other.gap;
        flush = other.flush;
        firstMove = other.firstMove;
        diagnostics = other.diagnostics;
        autoAlign = other.autoAlign;
        mode = other.mode;
    }

    public void readFrom(PhaseController controller) {
        gap = GapProcessing.enabled();
        flush = ImmediateFlush.enabled();
        firstMove = FirstMoveSync.enabled();
        autoAlign = controller.auto();
        mode = controller.mode().id();
    }
}
