package com.wiyuka.ticksync.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.net.PhaseBroadcaster;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.ok;

final class HudCommand {

    private HudCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> hud() {
        return Commands.literal("hud")
            .executes(ctx -> hudStatus(ctx.getSource()))
            .then(Commands.literal("self").executes(ctx -> hudSelf(ctx.getSource())))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> hudWatch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    private static int hudStatus(CommandSourceStack source) {
        ServerPlayer viewer = viewerOf(source);
        if (viewer == null) {
            return 0;
        }
        UUID subjectId = PhaseBroadcaster.hudSubject(viewer.getUUID());
        if (subjectId.equals(viewer.getUUID())) {
            ok(source, "TickSync: HUD 服务端面板显示自己", false);
            return 0;
        }
        ServerPlayer subject = source.getServer().getPlayerList().getPlayer(subjectId);
        ok(source, "TickSync: HUD 服务端面板正在显示 "
            + (subject != null ? subject.getName().getString() : subjectId.toString()), false);
        return 1;
    }

    private static int hudSelf(CommandSourceStack source) {
        ServerPlayer viewer = viewerOf(source);
        if (viewer == null) {
            return 0;
        }
        PhaseBroadcaster.setHudWatch(viewer.getUUID(), null);
        ok(source, "TickSync: HUD 服务端面板显示自己", true);
        return 1;
    }

    private static int hudWatch(CommandSourceStack source, ServerPlayer subject) {
        ServerPlayer viewer = viewerOf(source);
        if (viewer == null) {
            return 0;
        }
        if (TickSync.get().registry().get(subject.getUUID()) == null) {
            fail(source, "TickSync: 没有 " + subject.getName().getString() + " 的追踪数据");
            return 0;
        }

        PhaseBroadcaster.setHudWatch(viewer.getUUID(), subject.getUUID());
        ok(source, subject.getUUID().equals(viewer.getUUID())
            ? "TickSync: HUD 服务端面板已切回自己"
            : "TickSync: HUD 服务端面板现在显示 " + subject.getName().getString(), true);
        return 1;
    }

    private static ServerPlayer viewerOf(CommandSourceStack source) {
        ServerPlayer viewer = source.getPlayer();
        if (viewer == null) {
            fail(source, "TickSync: hud 只能由玩家执行");
        }
        return viewer;
    }
}
