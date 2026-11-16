package com.wiyuka.ticksync.net;

import com.wiyuka.ticksync.TickSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HudSubscriptionPayload(boolean visible) implements CustomPacketPayload {
    public static final Type<HudSubscriptionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(TickSync.MOD_ID, "hud_subscription"));
    public static final StreamCodec<FriendlyByteBuf, HudSubscriptionPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, HudSubscriptionPayload::visible, HudSubscriptionPayload::new);

    @Override
    public Type<HudSubscriptionPayload> type() {
        return TYPE;
    }
}
