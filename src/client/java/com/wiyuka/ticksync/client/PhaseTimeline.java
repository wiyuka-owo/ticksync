package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.phase.PhaseMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Two tick grids with the outbound and return packet paths between them. */
final class PhaseTimeline {
    private static final int SERVER = 0xFF99C8FF, CLIENT = 0xFFEEC28E;
    private static final int UP = 0xFF78D5BC, DOWN = 0xFFFFAD78;
    private static final int ROW_GAP = 15;

    private PhaseTimeline() {}

    static int height(Font font) {
        return font.lineHeight + ROW_GAP;
    }

    static void render(GuiGraphics g, Font font, int left, int top, int width, double wait, int rtt) {
        int axis = left + Math.max(font.width("服务端"), font.width("客户端")) + 5;
        int length = left + width - axis - 1;
        if (length < 8) return;
        int serverY = top + font.lineHeight / 2, clientY = serverY + ROW_GAP;
        double transit = rtt / 2.0;
        double reply = transit + 60;
        double sent = reply - wait - transit;
        double span = Math.max(200, reply + transit + 60);
        g.drawString(font, "服务端", left, top, SERVER, false);
        g.drawString(font, "客户端", left, top + ROW_GAP, CLIENT, false);
        grid(g, axis, serverY, length, span, reply, -1, SERVER);
        grid(g, axis, clientY, length, span, sent, 1, CLIENT);

        int from = axis + px(sent, span, length);
        int arrives = axis + px(sent + transit, span, length);
        int replies = axis + px(reply, span, length);
        int returns = axis + px(reply + transit, span, length);
        int consumed = axis + px(reply + transit + PhaseMath.returnWaitMs(wait, rtt), span, length);
        path(g, from, clientY, arrives, serverY, UP);
        path(g, replies, serverY, returns, clientY, DOWN);
        g.fill(arrives, serverY - 2, replies + 1, serverY + 1, UP);
        g.fill(returns, clientY, consumed + 1, clientY + 3, DOWN);
    }

    private static void grid(GuiGraphics g, int x, int y, int length, double span, double phase, int side, int color) {
        g.fill(x, y, x + length + 1, y + 1, color);
        double period = PhaseMath.PERIOD_MS;
        double step = period * Math.max(1, Math.ceil(span / (length * period)));
        for (double t = PhaseMath.wrap(phase); t <= span; t += step) {
            int mark = x + px(t, span, length);
            g.fill(mark, Math.min(y, y + 3 * side), mark + 1, Math.max(y, y + 3 * side) + 1, color);
        }
    }

    private static int px(double time, double span, int length) {
        return (int) Math.round(time * length / span);
    }

    private static void path(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int direction = Integer.signum(y1 - y0);
        int steps = Math.abs(y1 - y0);
        for (int i = 0; i < steps; i++) {
            int from = x0 + (x1 - x0) * i / steps;
            int to = x0 + (x1 - x0) * (i + 1) / steps;
            int y = y0 + direction * i;
            g.fill(from, y, Math.max(from + 1, to + 1), y + 1, color);
        }
    }
}
