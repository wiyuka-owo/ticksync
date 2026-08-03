package com.wiyuka.ticksync.command;

import com.mojang.brigadier.CommandDispatcher;
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
            .then(RateSpoofCommand.ratespoof())
            .then(Commands.literal("version")
                .executes(ctx -> {
                    ok(ctx.getSource(), TickSync.modVersion(), false);
                    return 1;
                })));
    }
}
