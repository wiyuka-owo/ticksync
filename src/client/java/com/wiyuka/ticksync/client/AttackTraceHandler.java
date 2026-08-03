package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.mixin.client.InteractPacketAccessor;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public final class AttackTraceHandler extends ChannelDuplexHandler {

    public static final String NAME = "ticksync_attack_trace";

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ServerboundInteractPacket packet) {
            boolean[] attack = {false};
            packet.dispatch(new ServerboundInteractPacket.Handler() {
                @Override
                public void onInteraction(InteractionHand hand) {
                }

                @Override
                public void onInteraction(InteractionHand hand, Vec3 pos) {
                }

                @Override
                public void onAttack() {
                    attack[0] = true;
                }
            });
            if (attack[0]) {
                AttackTrace.onAttackSent(
                    ((InteractPacketAccessor) packet).ticksync$entityId(), System.nanoTime());
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long now = System.nanoTime();
        if (msg instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                stampInbound(packet, now);
            }
        } else {
            stampInbound(msg, now);
        }
        super.channelRead(ctx, msg);
    }

    private static void stampInbound(Object msg, long now) {
        if (msg instanceof ClientboundDamageEventPacket packet) {
            AttackTrace.onDamageArrived(packet.entityId(), packet.sourceCauseId(), now);
        } else if (msg instanceof ClientboundSetEntityMotionPacket packet) {
            AttackTrace.onMotionArrived(packet.getId(), now);
        }
    }
}
