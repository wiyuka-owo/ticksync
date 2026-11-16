package com.wiyuka.ticksync.net;

import com.wiyuka.ticksync.TickSync;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record GhostBoxPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final int PROTOCOL_VERSION = 1;

    public static final int MAX_ENTRIES = 8;

    public static final GhostBoxPayload EMPTY = new GhostBoxPayload(List.of());

    public record Entry(int targetEntityId, Vec3 seenPosition, Vec3 livePosition) {
    }

    public static final Type<GhostBoxPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(TickSync.MOD_ID, "ghost_box_v" + PROTOCOL_VERSION));

    public static final StreamCodec<FriendlyByteBuf, GhostBoxPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            int count = Math.min(MAX_ENTRIES, payload.entries.size());
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                Entry entry = payload.entries.get(i);
                buf.writeVarInt(entry.targetEntityId);
                writeVec3(buf, entry.seenPosition);
                writeVec3(buf, entry.livePosition);
            }
        },
        buf -> {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new DecoderException("TickSync ghost box size out of range: " + count);
            }
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readVarInt(), readVec3(buf), readVec3(buf)));
            }
            return new GhostBoxPayload(List.copyOf(entries));
        });

    private static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
