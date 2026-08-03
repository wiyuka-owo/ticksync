package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.phase.PhaseMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class AttackTimelinePanel {

    private static final double PERIOD_MS = PhaseMath.PERIOD_MS;
    private static final double MIN_SPAN_MS = 150.0;
    private static final double TRAILING_MS = 40.0;

    private static final int MARK_HEIGHT = 5;
    private static final int EVENT_HEIGHT = 4;
    private static final int LINE_GAP = 2;
    private static final int LINE_HEIGHT = 9;

    private static final int COLOR_TRACK = -2039578;
    private static final int COLOR_TICK = -11053225;
    private static final int COLOR_ORIGIN = -1;
    private static final int COLOR_SEND = -11740828;
    private static final int COLOR_DAMAGE = -20448;
    private static final int COLOR_MOTION = -8407809;
    private static final int COLOR_ANIM = -38048;
    private static final int COLOR_LABEL = -2039578;
    private static final int COLOR_DIM = -7697770;

    private AttackTimelinePanel() {
    }

    public static int height(Font font) {
        int lineHeight = LINE_HEIGHT + LINE_GAP;
        return lineHeight + MARK_HEIGHT + EVENT_HEIGHT + LINE_GAP + lineHeight * 2;
    }

    public static void render(GuiGraphics graphics, Font font, AttackTrace.Snapshot snap,
                              int left, int top, int width) {
        double span = snap.spanMs();
        int lineHeight = LINE_HEIGHT + LINE_GAP;
        int trackY = top + lineHeight + MARK_HEIGHT;

        graphics.drawString(font, "攻击时序", left, top, COLOR_LABEL, false);

        graphics.fill(left, trackY, left + width, trackY + 1, COLOR_TRACK);
        for (double t = 0.0; t <= span; t += PERIOD_MS) {
            int x = x(left, width, span, t);
            graphics.fill(x, trackY - MARK_HEIGHT, x + 1, trackY, COLOR_TICK);
        }

        mark(graphics, left, width, span, trackY, 0.0, COLOR_ORIGIN);
        event(graphics, left, width, span, trackY, snap.sendMs(), COLOR_SEND);
        event(graphics, left, width, span, trackY, snap.damageMs(), COLOR_DAMAGE);
        event(graphics, left, width, span, trackY, snap.motionMs(), COLOR_MOTION);
        event(graphics, left, width, span, trackY, snap.animMs(), COLOR_ANIM);

        int textTop = trackY + EVENT_HEIGHT + LINE_GAP + 1;
        graphics.drawString(font, String.format(Locale.ROOT,
                "发包 %s   受伤广播 %s   击退 %s",
                fmt(snap.sendMs()), fmt(snap.damageMs()), fmt(snap.motionMs())),
            left, textTop, COLOR_LABEL, false);
        graphics.drawString(font, String.format(Locale.ROOT,
                "动画 %s   窗宽 %.0fms",
                fmt(snap.animMs()), span),
            left, textTop + lineHeight, COLOR_DIM, false);
    }

    private static void mark(GuiGraphics graphics, int left, int width, double span, int trackY,
                             double ms, int color) {
        if (Double.isNaN(ms)) {
            return;
        }
        int x = x(left, width, span, ms);
        graphics.fill(x, trackY - MARK_HEIGHT, x + 1, trackY + 1 + EVENT_HEIGHT, color);
    }

    private static void event(GuiGraphics graphics, int left, int width, double span, int trackY,
                              double ms, int color) {
        if (Double.isNaN(ms)) {
            return;
        }
        int x = x(left, width, span, ms);
        graphics.fill(x, trackY + 1, x + 2, trackY + 1 + EVENT_HEIGHT, color);
        graphics.fill(x, trackY - MARK_HEIGHT, x + 1, trackY, color);
    }

    private static String fmt(double ms) {
        return Double.isNaN(ms) ? "-" : String.format(Locale.ROOT, "%.1fms", ms);
    }

    private static int x(int left, int width, double span, double ms) {
        double clamped = Math.max(0.0, Math.min(span, ms));
        return left + (int) Math.round(clamped / span * width);
    }
}
