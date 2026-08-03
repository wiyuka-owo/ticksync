package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.net.PhaseSnapshotPayload;
import com.wiyuka.ticksync.phase.PhaseMath;
import com.wiyuka.ticksync.phase.PhaseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class PhaseHud {

    private static final double PERIOD_MS = PhaseMath.PERIOD_MS;

    private static final int LEFT = 6;
    private static final int TOP = 6;
    private static final int WIDTH = 288;
    private static final int BAR_HEIGHT = 10;
    private static final int PADDING = 5;
    private static final int LINE_GAP = 2;
    private static final int SECTION_GAP = 6;

    private static final int LINE_HEIGHT = 9;

    private static final double CLIFF_ZONE_MS = 1.5;

    private static final int COLOR_PANEL = -938471404;
    private static final int COLOR_TRACK = -14013902;
    private static final int COLOR_CLIFF = -9560289;
    private static final int COLOR_TARGET = -12594614;
    private static final int COLOR_TAIL = -1862291424;
    private static final int COLOR_MEAN = -1;
    private static final int COLOR_CORRECTING = -12003073;
    private static final int COLOR_LIVE = -12003073;
    private static final int COLOR_SPILL = -50384;
    private static final int COLOR_TEXT = -2039578;
    private static final int COLOR_DIM = -7697770;
    private static final int COLOR_WARN = -38048;

    private static final long SNAPSHOT_TIMEOUT_NANOS = 2_000_000_000L;

    private static volatile PhaseSnapshotPayload latest;
    private static volatile long latestNanos;
    private static volatile boolean visible = true;

    private PhaseHud() {
    }

    public static void accept(PhaseSnapshotPayload payload) {
        latest = payload;
        latestNanos = System.nanoTime();
    }

    public static void clear() {
        latest = null;
    }

    /**
     * A proxy backend switch reuses the play connection, so the disconnect event that would
     * call {@link #clear()} never fires. The snapshot has to age out on its own or the panel
     * stays frozen on whatever the previous server last sent.
     */
    private static PhaseSnapshotPayload freshSnapshot() {
        PhaseSnapshotPayload snapshot = latest;
        if (snapshot != null && System.nanoTime() - latestNanos > SNAPSHOT_TIMEOUT_NANOS) {
            latest = null;
            return null;
        }
        return snapshot;
    }

    public static void toggle() {
        visible = !visible;
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible || minecraft.options.hideGui) {
            return;
        }
        PhaseSnapshotPayload snapshot = freshSnapshot();
        PhaseTracker local = LocalPhase.tracker();
        AttackTrace.Snapshot attack = AttackTrace.display();
        if (snapshot == null && local == null && attack == null) {
            return;
        }

        Font font = minecraft.font;
        int lineHeight = LINE_HEIGHT + LINE_GAP;
        int contentLeft = LEFT + PADDING;
        int rtt = LocalPhase.rttMs();

        boolean localTimeline = snapshot == null && local != null
            && !Double.isNaN(local.meanMs()) && rtt >= 0;

        int serverHeight = snapshot == null ? 0 : serverHeight(font, snapshot, lineHeight);
        int localHeight = local == null ? 0
            : LocalPhasePanel.height(font) + (localTimeline ? SECTION_GAP + PhaseTimeline.height(font) : 0);
        int attackHeight = attack == null ? 0 : AttackTimelinePanel.height(font);
        int gap = serverHeight > 0 && localHeight > 0 ? SECTION_GAP : 0;
        int attackGap = (serverHeight > 0 || localHeight > 0) && attackHeight > 0 ? SECTION_GAP : 0;

        graphics.fill(LEFT, TOP, LEFT + PADDING + WIDTH + PADDING,
            TOP + PADDING + serverHeight + gap + localHeight + attackGap + attackHeight + PADDING, COLOR_PANEL);

        int y = TOP + PADDING;
        if (snapshot != null) {
            renderServer(graphics, font, snapshot, contentLeft, y, lineHeight);
            y += serverHeight + gap;
        }
        if (local != null) {
            LocalPhasePanel.render(graphics, font, local, contentLeft, y, WIDTH);
            if (localTimeline) {
                double inbound = Double.isNaN(local.livePhaseMs()) ? local.meanMs() : local.livePhaseMs();
                PhaseTimeline.render(graphics, font, PhaseMath.returnWaitMs(inbound, rtt), rtt,
                    contentLeft, y + LocalPhasePanel.height(font) + SECTION_GAP, WIDTH);
            }
            y += localHeight;
        }
        if (attack != null) {
            y += attackGap;
            AttackTimelinePanel.render(graphics, font, attack, contentLeft, y, WIDTH);
        }
    }

    private static int serverHeight(Font font, PhaseSnapshotPayload snapshot, int lineHeight) {
        if (Float.isNaN(snapshot.meanMs())) {
            return BAR_HEIGHT + LINE_GAP + lineHeight;
        }
        int textLines = isSpilling(snapshot) ? 3 : 2;
        return BAR_HEIGHT + LINE_GAP + lineHeight * textLines + SECTION_GAP + PhaseTimeline.height(font);
    }

    private static void renderServer(GuiGraphics graphics, Font font, PhaseSnapshotPayload snapshot,
                                     int contentLeft, int barTop, int lineHeight) {
        int barBottom = barTop + BAR_HEIGHT;
        if (Float.isNaN(snapshot.meanMs())) {
            graphics.fill(contentLeft, barTop, contentLeft + WIDTH, barBottom, COLOR_TRACK);
            graphics.drawString(font, "TickSync  " + snapshot.subjectName()
                    + "  正在采样,已收集 " + snapshot.samples() + " 个",
                contentLeft, barBottom + LINE_GAP, COLOR_DIM, false);
            return;
        }
        drawBar(graphics, snapshot, contentLeft, barTop, barBottom);
        int textTop = barBottom + LINE_GAP * 2;
        drawText(graphics, font, snapshot, contentLeft, textTop, lineHeight);
        int textLines = isSpilling(snapshot) ? 3 : 2;
        double wait = Float.isNaN(snapshot.livePhaseMs()) ? snapshot.meanMs() : snapshot.livePhaseMs();
        PhaseTimeline.render(graphics, font, wait, snapshot.rttMs(),
            contentLeft, textTop + lineHeight * textLines + SECTION_GAP, WIDTH);
    }

    private static void drawBar(GuiGraphics graphics, PhaseSnapshotPayload snapshot,
                                int barLeft, int barTop, int barBottom) {
        graphics.fill(barLeft, barTop, barLeft + WIDTH, barBottom, COLOR_TRACK);
        graphics.fill(barLeft, barTop, x(barLeft, CLIFF_ZONE_MS), barBottom, COLOR_CLIFF);

        double mean = snapshot.meanMs();
        double tail = Math.max(0.0, snapshot.lowTailMs());

        graphics.fill(x(barLeft, Math.max(0.0, mean - tail)), barTop + LINE_GAP,
            x(barLeft, Math.min(PERIOD_MS, mean + tail)), barBottom - LINE_GAP, COLOR_TAIL);

        if (mean - tail < 0.0) {
            graphics.fill(x(barLeft, PERIOD_MS + (mean - tail)), barTop + LINE_GAP,
                barLeft + WIDTH, barBottom - LINE_GAP, COLOR_SPILL);
        }

        int targetX = x(barLeft, snapshot.targetMs());
        graphics.fill(targetX, barTop - LINE_GAP, targetX + 1, barBottom + LINE_GAP, COLOR_TARGET);

        int meanX = x(barLeft, mean);
        graphics.fill(meanX, barTop - 1, meanX + 1, barBottom + 1,
            snapshot.correcting() ? COLOR_CORRECTING : COLOR_MEAN);

        if (!Float.isNaN(snapshot.livePhaseMs())) {
            int liveX = x(barLeft, snapshot.livePhaseMs());
            graphics.fill(liveX, barTop - 3, liveX + 1, barTop, COLOR_LIVE);
            graphics.fill(liveX, barBottom, liveX + 1, barBottom + 3, COLOR_LIVE);
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, PhaseSnapshotPayload snapshot,
                                 int left, int top, int lineHeight) {
        String mode = switch (snapshot.mode()) {
            case C2S -> "上行优先";
            case S2C -> "下行优先";
            case WORST -> "最大延迟";
        };
        String who = snapshot.subjectName();
        String headline = snapshot.correcting()
            ? String.format(Locale.ROOT, "TickSync  %s  正在校正  %.1fms → %.1fms  [%s]",
                who, snapshot.meanMs(), snapshot.targetMs(), mode)
            : String.format(Locale.ROOT, "TickSync  %s  上行等待 %.2fms(均值)   目标 %.2fms [%s]",
                who, snapshot.meanMs(), snapshot.targetMs(), mode);
        graphics.drawString(font, headline, left, top,
            snapshot.correcting() ? COLOR_CORRECTING : COLOR_TEXT, false);

        String detail = String.format(Locale.ROOT, "网络波动 %.2fms   样本 %d",
            snapshot.sigmaMs(), snapshot.samples());
        graphics.drawString(font, detail, left, top + lineHeight, COLOR_DIM, false);

        if (isSpilling(snapshot)) {
            graphics.drawString(font, "存在跨刻数据包", left, top + lineHeight * 2, COLOR_WARN, false);
        }
    }

    private static boolean isSpilling(PhaseSnapshotPayload snapshot) {
        return snapshot.meanMs() - snapshot.lowTailMs() < 0.0;
    }

    private static int x(int barLeft, double ms) {
        double clamped = Math.max(0.0, Math.min(PERIOD_MS, ms));
        return barLeft + (int) Math.round(clamped / PERIOD_MS * WIDTH);
    }
}
