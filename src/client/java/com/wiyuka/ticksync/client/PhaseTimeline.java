package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.phase.PhaseMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class PhaseTimeline {

    private static final double PERIOD_MS = PhaseMath.PERIOD_MS;

    private static final double MIN_SPAN_MS = 200.0;

    private static final double LEAD_IN_MS = 60.0;

    private static final double TRAILING_MS = 100.0;

    private static final int ROW_GAP = 26;
    private static final int MARK_HEIGHT = 5;
    private static final int WAIT_HEIGHT = 4;
    private static final int LINE_GAP = 2;
    private static final int LINE_HEIGHT = 9;

    private static final int COLOR_SERVER = -8407809;
    private static final int COLOR_CLIENT = -16264;
    private static final int COLOR_C2S = -11740828;
    private static final int COLOR_S2C = -38048;
    private static final int COLOR_WAIT_OUT = -10742;
    private static final int COLOR_WAIT_IN = -24822;
    private static final int COLOR_LABEL = -2039578;
    private static final int COLOR_GOOD = -12594614;
    private static final int COLOR_BAD = -38048;

    private PhaseTimeline() {
    }

    public static int height(Font font) {
        int lineHeight = LINE_HEIGHT + LINE_GAP;
        return lineHeight + MARK_HEIGHT + ROW_GAP + MARK_HEIGHT + LINE_GAP + lineHeight * 4;
    }

    public static double returnWaitMs(double outboundWaitMs, double rttMs) {
        return PhaseMath.returnWaitMs(outboundWaitMs, rttMs);
    }

    public static void render(GuiGraphics graphics, Font font, double outboundWaitMs, int rttMs,
                              int left, int top, int width) {
        double rtt = Math.max(0, rttMs);
        double oneWay = rtt / 2.0;
        double returnWait = returnWaitMs(outboundWaitMs, rtt);

        double focus = oneWay + LEAD_IN_MS;
        double span = Math.max(MIN_SPAN_MS, focus + oneWay + TRAILING_MS);

        int lineHeight = LINE_HEIGHT + LINE_GAP;
        int serverY = top + lineHeight + MARK_HEIGHT;
        int clientY = serverY + ROW_GAP;

        graphics.fill(left, serverY, left + width, serverY + 1, COLOR_SERVER);
        graphics.fill(left, clientY, left + width, clientY + 1, COLOR_CLIENT);

        double emitMs = focus - outboundWaitMs - oneWay;

        for (double t = mod(focus); t <= span; t += PERIOD_MS) {
            int x = x(left, width, span, t);
            graphics.fill(x, serverY - MARK_HEIGHT, x + 1, serverY, COLOR_SERVER);
        }
        for (double t = mod(emitMs); t <= span; t += PERIOD_MS) {
            int x = x(left, width, span, t);
            graphics.fill(x, clientY + 1, x + 1, clientY + 1 + MARK_HEIGHT, COLOR_CLIENT);
        }

        double landMs = emitMs + oneWay;
        line(graphics, x(left, width, span, emitMs), clientY,
            x(left, width, span, landMs), serverY, COLOR_C2S);
        graphics.fill(x(left, width, span, landMs), serverY - WAIT_HEIGHT,
            x(left, width, span, focus), serverY, COLOR_WAIT_OUT);

        double replyMs = focus + oneWay;
        line(graphics, x(left, width, span, focus), serverY,
            x(left, width, span, replyMs), clientY, COLOR_S2C);
        graphics.fill(x(left, width, span, replyMs), clientY + 1,
            x(left, width, span, replyMs + returnWait), clientY + 1 + WAIT_HEIGHT, COLOR_WAIT_IN);

        graphics.drawString(font, "服务端 tick", left, top, COLOR_SERVER, false);
        int clientLabelY = clientY + MARK_HEIGHT + LINE_GAP;
        graphics.drawString(font, "客户端 tick", left, clientLabelY, COLOR_CLIENT, false);

        int textTop = clientLabelY + lineHeight;
        graphics.drawString(font, String.format(Locale.ROOT,
                "上行等待 %.1fms(实时)   下行等待 %.1fms   延迟 %dms",
                outboundWaitMs, returnWait, rttMs),
            left, textTop, COLOR_LABEL, false);

        double total = outboundWaitMs + returnWait;
        boolean wasteful = total >= PERIOD_MS;
        String verdict = wasteful
            ? String.format(Locale.ROOT, "总等待 %.1fms   (跨刻)", total)
            : String.format(Locale.ROOT, "总等待 %.1fms   (单刻)", total);
        graphics.drawString(font, verdict, left, textTop + lineHeight,
            wasteful ? COLOR_BAD : COLOR_GOOD, false);
    }

    private static double mod(double v) {
        return PhaseMath.wrap(v);
    }

    private static int x(int left, int width, double span, double ms) {
        double clamped = Math.max(0.0, Math.min(span, ms));
        return left + (int) Math.round(clamped / span * width);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
