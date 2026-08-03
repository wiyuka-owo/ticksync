package com.wiyuka.ticksync.net;

import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.AlignMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PhaseSnapshotPayload(
    String subjectName,
    float meanMs,
    float livePhaseMs,
    float sigmaMs,
    float lowTailMs,
    float targetMs,
    AlignMode mode,
    int rttMs,
    int samples,
    boolean correcting
) implements CustomPacketPayload {

    public static final int PROTOCOL_VERSION = 1;

    public static final Type<PhaseSnapshotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(TickSync.MOD_ID, "phase_v" + PROTOCOL_VERSION));

    public static final StreamCodec<FriendlyByteBuf, PhaseSnapshotPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.subjectName, 32);
            buf.writeFloat(payload.meanMs);
            buf.writeFloat(payload.livePhaseMs);
            buf.writeFloat(payload.sigmaMs);
            buf.writeFloat(payload.lowTailMs);
            buf.writeFloat(payload.targetMs);
            buf.writeByte(payload.mode.ordinal());
            buf.writeVarInt(payload.rttMs);
            buf.writeVarInt(payload.samples);
            buf.writeBoolean(payload.correcting);
        },
        buf -> new PhaseSnapshotPayload(
            buf.readUtf(32),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            readMode(buf),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean()));

    private static AlignMode readMode(FriendlyByteBuf buf) {
        int ordinal = buf.readByte();
        AlignMode[] values = AlignMode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AlignMode.C2S;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
