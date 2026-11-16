package com.wiyuka.ticksync.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.viz.GhostViz;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.send;

final class GhostCommand {

    private GhostCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> ghost() {
        return Commands.literal("ghost")
            .then(Commands.literal("off")
                .executes(ctx -> off(ctx.getSource())))
            .then(Commands.literal("on")
                .executes(ctx -> on(ctx.getSource(), GhostViz.DEFAULT_RANGE_BLOCKS))
                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0, 128.0))
                    .executes(ctx -> on(ctx.getSource(),
                        DoubleArgumentType.getDouble(ctx, "range")))));
    }

    private static int on(CommandSourceStack source, double rangeBlocks)
        throws CommandSyntaxException {

        ServerPlayer observer = source.getPlayerOrException();
        if (!GhostViz.supported(observer)) {
            fail(source, "TickSync: 你的客户端没有安装本 mod，无法绘制碰撞框");
            return 0;
        }
        GhostViz.enable(observer, rangeBlocks);

        send(source, prefix().append(Component.literal(String.format(Locale.ROOT,
            "碰撞框投影已开启 (半径 %.0f 格)", rangeBlocks)).withStyle(ChatFormatting.WHITE)), false);
        send(source, hint("绿框", String.format(Locale.ROOT,
            "回溯 %.1f 刻，应与你看到的模型重合", PhaseMath.INTERPOLATION_TICKS_EXACT)), false);
        send(source, hint("红框", "服务端位置，应在模型前方"), false);
        return 1;
    }

    private static int off(CommandSourceStack source) throws CommandSyntaxException {
        boolean was = GhostViz.disable(source.getPlayerOrException().getUUID());
        send(source, prefix().append(Component.literal(was ? "碰撞框投影已关闭" : "碰撞框投影未开启")
            .withStyle(ChatFormatting.GRAY)), false);
        return was ? 1 : 0;
    }

    private static MutableComponent hint(String label, String text) {
        return Component.literal("  " + label + " ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent prefix() {
        return Component.literal("TickSync: ").withStyle(ChatFormatting.DARK_AQUA);
    }
}
