package com.wiyuka.ticksync.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wiyuka.ticksync.TickSync;
import com.wiyuka.ticksync.control.AlignMode;
import com.wiyuka.ticksync.control.PhaseController;
import com.wiyuka.ticksync.net.PhaseBroadcaster;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import com.wiyuka.ticksync.phase.PlayerSession;
import com.wiyuka.ticksync.phase.RttTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static com.wiyuka.ticksync.command.CommandText.clip;
import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.ok;
import static com.wiyuka.ticksync.command.CommandText.send;

final class StatsCommand {

    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    private static final int COLUMNS = 50;

    private static final int EMPTY = 0x22262A;
    private static final int SHAPE = 0x4FB3D9;
    private static final int OUTSIDE = 0x33383D;
    private static final int BAND = 0x3E5A47;
    private static final int JITTER = 0x5FA377;
    private static final int MEAN = 0xF2F2F2;
    private static final int TARGET = 0xE0A03C;

    private StatsCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> stats() {
        return Commands.literal("stats")
            .executes(ctx -> statsAll(ctx.getSource()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> statsOne(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> reset() {
        return Commands.literal("reset").executes(ctx -> {
            TickSync.get().registry().resetAll();
            ok(ctx.getSource(), "TickSync: 统计数据已清除", false);
            return 1;
        });
    }

    private static int statsAll(CommandSourceStack source) {
        TickSync.get().registry().requestSamples();
        Collection<PhaseTracker> all = TickSync.get().registry().all();
        if (all.isEmpty()) {
            send(source, Component.literal("TickSync: ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("暂无样本，已请求采集").withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        header(source, "追踪中", all.size() + " 名玩家");
        for (PhaseTracker tracker : all) {
            send(source, indent()
                .append(Component.literal(clip(tracker.playerName())).withStyle(ChatFormatting.WHITE))
                .append(dim("  均值 ")).append(num(tracker.meanMs(), "ms"))
                .append(dim("  抖动 ")).append(num(tracker.sigmaMs(), "ms"))
                .append(dim("  到达 ")).append(numPct(tracker.coverage()))
                .append(dim("  堆积 ")).append(numPct(tracker.doubledRate())), false);
        }
        return all.size();
    }

    private static int statsOne(CommandSourceStack source, ServerPlayer player) {
        TickSync.get().registry().requestSamples(player.getUUID());
        PhaseTracker tracker = TickSync.get().registry().get(player.getUUID());
        if (tracker == null) {
            fail(source, "TickSync: 正在为 " + player.getName().getString() + " 采样，请稍后查看");
            return 0;
        }

        PhaseController controller = TickSync.get().controller();
        AlignMode mode = controller.mode();
        double rtt = PhaseBroadcaster.rttMs(player);
        PhaseController.Band band = controller.bandFor(tracker, rtt);

        track(source, tracker, band, mode);
        sync(source, tracker, player, band, mode, rtt);
        return 1;
    }

    private static void track(
        CommandSourceStack source, PhaseTracker tracker, PhaseController.Band band, AlignMode mode) {

        long total = tracker.accepted() + tracker.discarded();
        double discard = total == 0L ? 0.0 : 100.0 * tracker.discarded() / total;

        header(source, "相位轨道", String.format(Locale.ROOT,
            "一个游戏刻 50 ms，%d 样本，%.0f%% 无效", tracker.windowCount(), discard));

        shape(source, tracker.histogram());
        lane(source, tracker, band, mode);

        send(source, indent()
            .append(chip(MEAN)).append(dim(" 均值 ")).append(num(tracker.meanMs(), "ms"))
            .append(Component.literal("    "))
            .append(chip(JITTER)).append(dim(" 抖动 "))
            .append(num(tracker.lowTailMs(), "")).append(dim(" 前 / "))
            .append(num(tracker.highTailMs(), "ms")).append(dim(" 后"))
            .append(verdict(tracker.sigmaMs())), false);

        if (band != null && !band.isEmpty()) {
            send(source, indent()
                .append(chip(TARGET)).append(dim(" 目标 ")).append(num(band.targetFor(mode), "ms"))
                .append(Component.literal("    "))
                .append(chip(BAND)).append(dim(" 可用 ")).append(num(band.floorMs(), ""))
                .append(dim(" ~ ")).append(num(band.ceilingMs(), "ms")), false);
        }

        row(source, "相位偏移", drift(tracker.driftMsPerMinute()));
    }

    private static void shape(CommandSourceStack source, long[] bins) {
        long peak = 1L;
        for (long count : bins) {
            peak = Math.max(peak, count);
        }
        int width = Math.min(COLUMNS, bins.length);
        char[] chars = new char[width];
        int[] colors = new int[width];
        for (int i = 0; i < width; i++) {
            if (bins[i] == 0L) {
                chars[i] = BARS[0];
                colors[i] = EMPTY;
            } else {
                chars[i] = BARS[(int) (bins[i] * (BARS.length - 1) / peak)];
                colors[i] = SHAPE;
            }
        }
        send(source, indent().append(strip(chars, colors)), false);
    }

    private static void lane(
        CommandSourceStack source, PhaseTracker tracker, PhaseController.Band band, AlignMode mode) {

        char[] chars = new char[COLUMNS];
        int[] colors = new int[COLUMNS];
        Arrays.fill(chars, BARS[0]);
        Arrays.fill(colors, OUTSIDE);

        boolean usable = band != null && !band.isEmpty();
        if (usable) {
            paint(colors, band.floorMs(), band.ceilingMs(), BAND);
        }
        double mean = tracker.meanMs();
        if (!Double.isNaN(mean)) {
            paint(colors, mean - tracker.lowTailMs(), mean + tracker.highTailMs(), JITTER);
        }
        if (usable) {
            mark(colors, band.targetFor(mode), TARGET);
        }
        mark(colors, mean, MEAN);

        send(source, indent().append(strip(chars, colors)), false);
    }

    private static void paint(int[] colors, double fromMs, double toMs, int color) {
        if (Double.isNaN(fromMs) || Double.isNaN(toMs)) {
            return;
        }
        int from = (int) Math.floor(fromMs);
        int span = Math.min(COLUMNS, (int) Math.ceil(toMs) - from + 1);
        for (int i = 0; i < span; i++) {
            colors[Math.floorMod(from + i, COLUMNS)] = color;
        }
    }

    private static void mark(int[] colors, double ms, int color) {
        if (!Double.isNaN(ms)) {
            colors[Math.floorMod((int) Math.floor(ms), COLUMNS)] = color;
        }
    }

    private static MutableComponent strip(char[] chars, int[] colors) {
        MutableComponent out = Component.empty();
        int start = 0;
        for (int i = 1; i <= chars.length; i++) {
            if (i == chars.length || colors[i] != colors[start]) {
                out.append(Component.literal(new String(chars, start, i - start))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colors[start]))));
                start = i;
            }
        }
        return out;
    }

    private static MutableComponent chip(int color) {
        return Component.literal("▄").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    private static void sync(
        CommandSourceStack source, PhaseTracker tracker, ServerPlayer player,
        PhaseController.Band band, AlignMode mode, double rtt) {

        header(source, "链路同步", mode.id() + " 模式");

        PlayerSession session = TickSync.get().registry().session(player.getUUID());
        RttTracker probe = session == null ? null : session.rtt();
        if (probe == null || !probe.ready()) {
            row(source, "往返延迟", Component.literal("探测中").withStyle(ChatFormatting.DARK_GRAY)
                .append(dim("    原版 ping ")).append(num(player.connection.latency(), "ms")));
        } else {
            row(source, "往返延迟", num(probe.rttMs(), "ms")
                .append(dim("    原版 ping ")).append(num(player.connection.latency(), "ms"))
                .append(dim("    采信 ")).append(numPct(probe.fill())));
        }

        if (band == null || Double.isNaN(tracker.meanMs())) {
            row(source, "可用区间", Component.literal("样本不足").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        double returnWait = PhaseMath.returnWaitMs(tracker.meanMs(), rtt);
        double totalWait = tracker.meanMs() + returnWait;
        boolean single = totalWait < PhaseMath.PERIOD_MS;

        row(source, "链路等待", num(tracker.meanMs(), "").append(dim(" 上行 + "))
            .append(num(returnWait, "")).append(dim(" 下行 = ")).append(num(totalWait, "ms"))
            .append(Component.literal("  " + (single ? "单刻" : "跨刻"))
                .withStyle(single ? ChatFormatting.DARK_GREEN : ChatFormatting.GOLD)));

        if (band.isEmpty()) {
            row(source, "可用区间", Component.literal("无").withStyle(ChatFormatting.RED)
                .append(dim("    波动 ")).append(num(tracker.lowTailMs() + tracker.highTailMs() + 2.0, ""))
                .append(dim(" 超出临界 ")).append(num(band.cliffMs(), "ms")));
        } else {
            row(source, "调整余量", num(band.widthMs(), "ms"));
        }
    }

    private static void header(CommandSourceStack source, String title, String context) {
        send(source, Component.empty(), false);
        send(source, Component.literal("> ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(title).withStyle(ChatFormatting.GOLD))
            .append(dim("  " + context)), false);
    }

    private static void row(CommandSourceStack source, String label, MutableComponent value) {
        send(source, indent()
            .append(Component.literal(label + "  ").withStyle(ChatFormatting.GRAY))
            .append(value), false);
    }

    private static MutableComponent indent() {
        return Component.literal("   ");
    }

    private static MutableComponent dim(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent num(double value, String unit) {
        if (Double.isNaN(value)) {
            return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
        }
        MutableComponent out = Component.literal(String.format(Locale.ROOT, "%.1f", value))
            .withStyle(ChatFormatting.WHITE);
        return unit.isEmpty() ? out : out.append(dim(" " + unit));
    }

    private static MutableComponent num(int value, String unit) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE)
            .append(dim(" " + unit));
    }

    private static MutableComponent numPct(double fraction) {
        if (Double.isNaN(fraction)) {
            return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.literal(String.format(Locale.ROOT, "%.0f%%", fraction * 100.0))
            .withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent drift(double msPerMinute) {
        if (Double.isNaN(msPerMinute)) {
            return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY)
                .append(dim("    需要更长的连续样本"));
        }
        return Component.literal(String.format(Locale.ROOT, "%+.2f", msPerMinute))
            .withStyle(ChatFormatting.WHITE).append(dim(" ms/分"));
    }

    private static MutableComponent verdict(double sigma) {
        if (Double.isNaN(sigma)) {
            return dim("    样本不足");
        }
        if (sigma < 5.0) {
            return Component.literal("    稳定").withStyle(ChatFormatting.DARK_GREEN);
        }
        return sigma < 12.0
            ? Component.literal("    有波动").withStyle(ChatFormatting.GOLD)
            : Component.literal("    波动较大").withStyle(ChatFormatting.RED);
    }
}
