package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class LocalPhasePanel {

    private static final int BAR_HEIGHT = 8;
    private static final int LINE_GAP = 2;
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_LINES = 3;

    private static final double EDGE_ZONE_MS = 1.5;

    private static final int COLOR_TRACK = -14013902;
    private static final int COLOR_EDGE = -9560289;
    private static final int COLOR_MEAN = -1;
    private static final int COLOR_LIVE = -12003073;
    private static final int COLOR_TEXT = -2039578;
    private static final int COLOR_DIM = -7697770;
    private static final int COLOR_GOOD = -9710485;
    private static final int COLOR_WARN = -38048;

    private LocalPhasePanel() {
    }

    public static int height(Font font) {
        return BAR_HEIGHT + LINE_GAP + (LINE_HEIGHT + LINE_GAP) * TEXT_LINES;
    }

    public static void render(GuiGraphics graphics, Font font, PhaseTracker tracker,
                              int left, int top, int width) {
        int lineHeight = LINE_HEIGHT + LINE_GAP;
        int barBottom = top + BAR_HEIGHT;
        double mean = tracker.meanMs();

        graphics.fill(left, top, left + width, barBottom, COLOR_TRACK);
        graphics.fill(left, top, x(left, width, EDGE_ZONE_MS), barBottom, COLOR_EDGE);

        if (Double.isNaN(mean)) {
            graphics.drawString(font, String.format(Locale.ROOT,
                    "本机测量  等待实体更新包   已采纳 %d   已丢弃 %d", tracker.accepted(), tracker.discarded()),
                left, barBottom + LINE_GAP, COLOR_DIM, false);
            return;
        }

        int meanX = x(left, width, mean);
        graphics.fill(meanX, top - 1, meanX + 1, barBottom + 1, COLOR_MEAN);

        double live = tracker.livePhaseMs();
        if (!Double.isNaN(live)) {
            int liveX = x(left, width, live);
            graphics.fill(liveX, barBottom, liveX + 1, barBottom + 3, COLOR_LIVE);
        }

        int y = barBottom + LINE_GAP * 2;
        graphics.drawString(font, String.format(Locale.ROOT,
                "本机测量  下行等待 %.2fms(均值)   实时 %.2fms", mean, Double.isNaN(live) ? mean : live),
            left, y, COLOR_TEXT, false);

        int rtt = LocalPhase.rttMs();
        if (rtt < 0) {
            graphics.drawString(font, "延迟未知", left, y + lineHeight, COLOR_DIM, false);
        } else {
            double outbound = PhaseMath.returnWaitMs(mean, rtt);
            double total = mean + outbound;
            boolean cheap = total < PhaseMath.PERIOD_MS;
            graphics.drawString(font, String.format(Locale.ROOT,
                    "延迟 %dms  推算上行 %.2fms   总等待 %.2fms  %s", rtt, outbound, total, cheap ? "" : ""),
                left, y + lineHeight, cheap ? COLOR_GOOD : COLOR_WARN, false);
        }

        graphics.drawString(font, String.format(Locale.ROOT,
                "网络波动 %.2fms   样本 %d   可计算的 tick 占 %.0f%%",
                tracker.sigmaMs(), tracker.windowCount(), tracker.coverage() * 100.0),
            left, y + lineHeight * 2, COLOR_DIM, false);
    }

    private static int x(int left, int width, double ms) {
        double clamped = Math.max(0.0, Math.min(PhaseMath.PERIOD_MS, ms));
        return left + (int) Math.round(clamped / PhaseMath.PERIOD_MS * width);
    }
}
