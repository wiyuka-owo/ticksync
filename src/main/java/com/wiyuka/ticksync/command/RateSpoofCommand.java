package com.wiyuka.ticksync.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.ratespoof.RateSpoofManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.ok;

final class RateSpoofCommand {

    private RateSpoofCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> ratespoof() {
        return Commands.literal("ratespoof")
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("rate", FloatArgumentType.floatArg(1.0F, 100.0F))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
                        .executes(ctx -> start(ctx.getSource(),
                            EntityArgument.getPlayer(ctx, "player"),
                            FloatArgumentType.getFloat(ctx, "rate"),
                            IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    private static int start(CommandSourceStack source, ServerPlayer player, float rate, int seconds) {
        RateSpoofManager spoof = TickSync.get().rateSpoof();
        if (!spoof.start(player, rate, seconds * 20, source.getServer().getTickCount())) {
            fail(source, "TickSync: 变速已被占用（当前目标: " + spoof.targetName() + "）");
            return 0;
        }
        ok(source, "TickSync: 开始向 " + player.getName().getString()
            + " 发送伪造速率 (" + rate + " TPS, 持续 " + seconds + " 秒)", true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        RateSpoofManager spoof = TickSync.get().rateSpoof();
        if (!spoof.active()) {
            fail(source, "TickSync: 当前没有进行中的变速");
            return 0;
        }
        spoof.stop(source.getServer());
        ok(source, "TickSync: 变速已中止，客户端速率已还原", true);
        return 1;
    }
}
