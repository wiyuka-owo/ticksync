package com.wiyuka.ticksync.command;

import com.wiyuka.ticksync.TickSync;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wiyuka.ticksync.immediate.GapProcessing;
import com.wiyuka.ticksync.immediate.ImmediateFlush;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import static com.wiyuka.ticksync.command.CommandText.ok;

final class LatencyCommand {

    private LatencyCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> gap() {
        return Commands.literal("gap")
            .executes(ctx -> gapStatus(ctx.getSource()))
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> gapSet(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> flush() {
        return Commands.literal("flush")
            .executes(ctx -> flushStatus(ctx.getSource()))
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> flushSet(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))));
    }

    private static int gapStatus(CommandSourceStack source) {
        ok(source, "TickSync: tick 间隙收包处理 [" + (GapProcessing.enabled() ? "已开启" : "已关闭")
            + "]，累计提前处理 " + GapProcessing.drains() + " 次", false);
        return GapProcessing.enabled() ? 1 : 0;
    }

    private static int gapSet(CommandSourceStack source, boolean enabled) {
        TickSync.get().setGapEnabled(source.getServer(), enabled);
        ok(source, "TickSync: tick 间隙收包处理"
            + (enabled ? "已开启，入站包到达即处理 (建议同时开启 flush)"
                       : "已关闭，恢复原版处理时序"), true);
        return 1;
    }

    private static int flushStatus(CommandSourceStack source) {
        ok(source, "TickSync: 速度即时下发 [" + (ImmediateFlush.enabled() ? "已开启" : "已关闭")
            + "]，累计提前下发 " + ImmediateFlush.flushed() + " 次", false);
        return ImmediateFlush.enabled() ? 1 : 0;
    }

    private static int flushSet(CommandSourceStack source, boolean enabled) {
        TickSync.get().setFlushEnabled(source.getServer(), enabled);
        ok(source, "TickSync: 速度即时下发"
            + (enabled ? "已开启" : "已关闭，恢复原版处理时序"), true);
        return 1;
    }
}
