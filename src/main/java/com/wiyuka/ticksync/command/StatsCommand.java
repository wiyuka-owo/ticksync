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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;

import static com.wiyuka.ticksync.command.CommandText.clip;
import static com.wiyuka.ticksync.command.CommandText.drift;
import static com.wiyuka.ticksync.command.CommandText.fail;
import static com.wiyuka.ticksync.command.CommandText.ms;
import static com.wiyuka.ticksync.command.CommandText.ok;
import static com.wiyuka.ticksync.command.CommandText.pct;
import static com.wiyuka.ticksync.command.CommandText.perMinute;
import static com.wiyuka.ticksync.command.CommandText.verdict;

final class StatsCommand {

    private static final char EMPTY_BIN = ' ';
    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private static final String RULER = "|         |         |         |         |         |";
    private static final String RULER_LABELS = "0         10        20        30        40        50 ms";

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
        Collection<PhaseTracker> all = TickSync.get().registry().all();
        if (all.isEmpty()) {
            ok(source, "TickSync: 无玩家追踪数据", false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TickSync — 当前追踪 ").append(all.size()).append(" 名玩家\n");
        sb.append(String.format(Locale.ROOT, "%-16s %5s %8s %8s %6s %6s %10s%n",
            "玩家", "样本数", "均值", "抖动", "到达率", "堆积", "相位偏移"));
        for (PhaseTracker tracker : all) {
            sb.append(String.format(Locale.ROOT, "%-16s %5d %8s %8s %6s %6s %10s%n",
                clip(tracker.playerName()), tracker.windowCount(),
                ms(tracker.meanMs()), ms(tracker.sigmaMs()),
                pct(tracker.coverage()), pct(tracker.doubledRate()),
                perMinute(tracker.driftMsPerMinute())));
        }
        ok(source, sb.toString(), false);
        return all.size();
    }

    private static int statsOne(CommandSourceStack source, ServerPlayer player) {
        PhaseTracker tracker = TickSync.get().registry().get(player.getUUID());
        if (tracker == null) {
            fail(source, "TickSync: 无玩家 " + player.getName().getString() + " 的追踪数据");
            return 0;
        }

        long total = tracker.accepted() + tracker.discarded();
        double discardPct = total == 0L ? 0.0 : 100.0 * tracker.discarded() / total;

        StringBuilder sb = new StringBuilder();
        sb.append("TickSync — ").append(tracker.playerName()).append('\n');
        sb.append(String.format(Locale.ROOT, "  统计窗口   %d 个样本%n", tracker.windowCount()));
        sb.append(String.format(Locale.ROOT, "  平均等待   %s%n", ms(tracker.meanMs())));
        sb.append(String.format(Locale.ROOT, "  网络波动   %s   (%s)%n",
            ms(tracker.sigmaMs()), verdict(tracker.sigmaMs())));
        sb.append(String.format(Locale.ROOT, "  延迟范围   %s (p1-p99), 波动下限 %s / 上限 %s%n",
            ms(tracker.spreadMs()), ms(tracker.lowTailMs()), ms(tracker.highTailMs())));
        sb.append(String.format(Locale.ROOT, "  分位数     p1 %s   p50 %s   p95 %s   p99 %s%n",
            ms(tracker.percentileMs(0.01)), ms(tracker.percentileMs(0.5)),
            ms(tracker.percentileMs(0.95)), ms(tracker.percentileMs(0.99))));
        sb.append(placement(tracker, player));
        sb.append(roundTrip(player));
        sb.append(String.format(Locale.ROOT, "  相位偏移   %s%n", drift(tracker.driftMsPerMinute())));
        sb.append(String.format(Locale.ROOT, "  样本记录   采纳 %d，丢弃 %d (%.1f%% 无效数据)%n",
            tracker.accepted(), tracker.discarded(), discardPct));
        sb.append(histogram(tracker.histogram()));

        ok(source, sb.toString(), false);
        return 1;
    }

    private static String placement(PhaseTracker tracker, ServerPlayer player) {
        PhaseController controller = TickSync.get().controller();
        double rtt = PhaseBroadcaster.rttMs(player);
        PhaseController.Band band = controller.bandFor(tracker, rtt);
        if (band == null || Double.isNaN(tracker.meanMs())) {
            return "  落点位置   -\n";
        }

        AlignMode mode = controller.mode();
        double mean = tracker.meanMs();
        double returnWait = PhaseMath.returnWaitMs(mean, rtt);
        double totalWait = mean + returnWait;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "  链路等待   上行 %s + 下行 %s = 合计 %s   (当前处于%s区间，重发临界值 %s，延迟 %s)%n",
            ms(mean), ms(returnWait), ms(totalWait),
            totalWait < PhaseMath.PERIOD_MS ? "单刻" : "跨刻", ms(band.cliffMs()), ms(rtt)));
        sb.append(band.isEmpty()
            ? String.format(Locale.ROOT,
                "  可用区间   无可用空间 — 波动范围 %s，区间宽度 %s%n",
                ms(tracker.lowTailMs() + tracker.highTailMs() + 2.0), ms(band.cliffMs()))
            : String.format(Locale.ROOT,
                "  可用区间   %s 至 %s   (上下行可调整余量: %s)%n",
                ms(band.floorMs()), ms(band.ceilingMs()), ms(band.widthMs())));
        sb.append(String.format(Locale.ROOT, "  同步配置   %s 模式 — 期望目标上行等待时间为 %s%n",
            mode.id(), ms(band.targetFor(mode))));
        return sb.toString();
    }

    private static String roundTrip(ServerPlayer player) {
        PlayerSession session = TickSync.get().registry().session(player.getUUID());
        if (session == null || !session.rtt().ready()) {
            return String.format(Locale.ROOT,
                "  往返延迟   等待探测响应... (暂用原版 ping 值 %d ms)%n",
                player.connection.latency());
        }
        RttTracker rtt = session.rtt();
        return String.format(Locale.ROOT,
            "  往返延迟   实测 %s (已剔除渲染等待帧 %s)   (数据采集率 %s，原版 ping 值 %d ms)%n",
            ms(rtt.rttMs()), ms(rtt.paddingMs()), pct(rtt.fill()), player.connection.latency());
    }

    private static String histogram(long[] bins) {
        long peak = 1L;
        for (long count : bins) {
            peak = Math.max(peak, count);
        }
        StringBuilder line = new StringBuilder(bins.length);
        for (long count : bins) {
            line.append(barFor(count, peak));
        }
        return "  延迟分布 (1 ms / 字符)\n  " + line + "\n  " + RULER + "\n  " + RULER_LABELS + "\n";
    }

    private static char barFor(long count, long peak) {
        if (count == 0L) {
            return EMPTY_BIN;
        }
        return BARS[(int) (count * (BARS.length - 1) / peak)];
    }
}
