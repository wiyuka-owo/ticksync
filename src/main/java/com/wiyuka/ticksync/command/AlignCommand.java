package com.wiyuka.ticksync.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.AlignMode;
import com.wiyuka.ticksync.control.PhaseController;
import com.wiyuka.ticksync.phase.PhaseTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.ok;

final class AlignCommand {

    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (ctx, builder) -> {
        for (AlignMode mode : AlignMode.values()) {
            builder.suggest(mode.id());
        }
        return builder.buildFuture();
    };

    private AlignCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> align() {
        return Commands.literal("align")
            .then(Commands.literal("all").executes(ctx -> alignAll(ctx.getSource())))
            .then(Commands.literal("cancel").executes(ctx -> alignCancel(ctx.getSource())))
            .then(Commands.literal("auto")
                .executes(ctx -> autoStatus(ctx.getSource()))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> autoSet(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> alignOne(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))
                .then(Commands.literal("force")
                    .executes(ctx -> alignOne(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> mode() {
        return Commands.literal("mode")
            .executes(ctx -> modeStatus(ctx.getSource()))
            .then(Commands.argument("mode", StringArgumentType.word())
                .suggests(MODE_SUGGESTIONS)
                .executes(ctx -> modeSet(ctx.getSource(), StringArgumentType.getString(ctx, "mode"))));
    }

    private static int alignOne(CommandSourceStack source, ServerPlayer player, boolean force) {
        PhaseTracker tracker = TickSync.get().registry().get(player.getUUID());
        if (tracker == null) {
            fail(source, "TickSync: 无玩家 " + player.getName().getString() + " 的追踪数据");
            return 0;
        }

        PhaseController.Decision decision = TickSync.get().controller()
            .align(player, tracker, source.getServer().getTickCount(), force);
        if (!decision.accepted()) {
            fail(source, "TickSync: " + (force ? "强制执行失败 " : "同步跳过 ")
                + player.getName().getString() + " — " + decision.rejection());
            return 0;
        }
        ok(source, "TickSync: " + (force ? "已强制执行 " : "已同步 ")
            + player.getName().getString() + " — " + decision.plan(), true);
        return 1;
    }

    private static int alignAll(CommandSourceStack source) {
        Collection<PhaseTracker> all = TickSync.get().registry().all();
        if (all.isEmpty()) {
            fail(source, "TickSync: 无玩家追踪数据");
            return 0;
        }
        List<String> report = TickSync.get().controller()
            .alignAll(source.getServer(), all, source.getServer().getTickCount());
        ok(source, "TickSync — 批量同步结果\n  " + String.join("\n  ", report), true);
        return report.size();
    }

    private static int alignCancel(CommandSourceStack source) {
        PhaseController controller = TickSync.get().controller();
        int count = controller.activeCount();
        if (count == 0) {
            fail(source, "TickSync: 当前无活动同步任务");
            return 0;
        }
        controller.cancelAll(source.getServer());
        ok(source, "TickSync: 已取消 " + count + " 个同步任务，客户端已恢复", true);
        return count;
    }

    private static int autoStatus(CommandSourceStack source) {
        PhaseController controller = TickSync.get().controller();
        ok(source, "TickSync: 自动同步 [" + (controller.auto() ? "已开启" : "已关闭")
            + "]，当前活动同步任务: " + controller.activeCount(), false);
        return controller.auto() ? 1 : 0;
    }

    private static int autoSet(CommandSourceStack source, boolean enabled) {
        PhaseController controller = TickSync.get().controller();
        TickSync.get().setAutoAlign(source.getServer(), enabled);
        if (!enabled) {
            controller.cancelAll(source.getServer());
        }
        ok(source, "TickSync: 自动同步" + (enabled ? "已开启" : "已关闭，客户端状态已重置"), true);
        return 1;
    }

    private static int modeStatus(CommandSourceStack source) {
        PhaseController controller = TickSync.get().controller();
        StringBuilder sb = new StringBuilder("TickSync — 当前同步模式\n");
        for (AlignMode mode : AlignMode.values()) {
            sb.append(String.format(Locale.ROOT, "  %-4s %s%s%n", mode.id(), mode.summary(),
                mode == controller.mode() ? " (当前)" : ""));
        }
        ok(source, sb.toString(), false);
        return 1;
    }

    private static int modeSet(CommandSourceStack source, String id) {
        AlignMode mode = AlignMode.byId(id);
        if (mode == null) {
            fail(source, "TickSync: 未知模式 '" + id + "'");
            return 0;
        }
        TickSync.get().setMode(source.getServer(), mode);
        ok(source, "TickSync: 同步模式现已更改为 " + mode.id() + " — " + mode.summary() + " (下次同步时生效)", true);
        return 1;
    }
}
