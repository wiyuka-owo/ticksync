package com.wiyuka.ticksync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.wiyuka.ticksync.TickSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import static com.wiyuka.ticksync.command.CommandText.ok;

public final class TickSyncCommand {

    private TickSyncCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ticksync")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(StatsCommand.stats())
            .then(StatsCommand.reset())
            .then(AlignCommand.align())
            .then(AlignCommand.mode())
            .then(HudCommand.hud())
            .then(LatencyCommand.gap())
            .then(LatencyCommand.flush())
            .then(LatencyCommand.firstMove())
            .then(Commands.literal("diagnostics")
                .executes(ctx -> diagnostics(ctx.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                    var config = TickSync.get().config();
                    config.diagnostics = BoolArgumentType.getBool(ctx, "enabled");
                    config.save(ctx.getSource().getServer());
                    return diagnostics(ctx.getSource());
                })))
            .then(RateSpoofCommand.ratespoof())
            .then(GhostCommand.ghost())
            .then(Commands.literal("version")
                .executes(ctx -> {
                    ok(ctx.getSource(), TickSync.modVersion(), false);
                    return 1;
                })));
    }

    private static int diagnostics(CommandSourceStack source) {
        boolean enabled = TickSync.get().config().diagnostics;
        ok(source, "全服诊断采集" + (enabled ? "已开启" : "已关闭，按需采样"), false);
        return enabled ? 1 : 0;
    }
}
