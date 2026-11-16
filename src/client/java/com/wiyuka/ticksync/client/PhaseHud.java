package com.wiyuka.ticksync.client;

import com.wiyuka.ticksync.net.HudSubscriptionPayload;
import com.wiyuka.ticksync.net.PhaseSnapshotPayload;
import com.wiyuka.ticksync.phase.PhaseMath;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PhaseHud {
    private static boolean visible, subscribed;
    private static PhaseSnapshotPayload latest;
    private static long received;

    private PhaseHud() {}

    public static void accept(PhaseSnapshotPayload payload) {
        latest = payload;
        received = System.nanoTime();
    }

    static void clear() {
        subscribed = false;
        latest = null;
        AttackTrace.clear();
        LocalPhase.update(null);
    }

    public static void toggle() {
        visible = !visible;
        if (!visible) AttackTrace.clear();
    }

    public static boolean visible() {
        return visible && !Minecraft.getInstance().options.hideGui;
    }

    static void updateSubscription() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        boolean supported = ClientPlayNetworking.canSend(HudSubscriptionPayload.TYPE);
        boolean fresh = latest != null && System.nanoTime() - received < 2_000_000_000L;
        LocalPhase.update(visible() && !fresh ? connection.getConnection() : null);
        boolean want = visible() && supported;
        if (want == subscribed) return;
        subscribed = want;
        latest = null;
        if (supported) ClientPlayNetworking.send(new HudSubscriptionPayload(want));
        if (!want) AttackTrace.clear();
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible() || mc.player == null || mc.getConnection() == null) return;
        long now = System.nanoTime();
        PhaseSnapshotPayload snapshot = now - received < 2_000_000_000L ? latest : null;
        PlayerInfo self = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int rtt = snapshot != null ? snapshot.rttMs() : self == null ? -1 : self.getLatency();
        boolean watchingOther = snapshot != null && !snapshot.subjectName().equals(mc.player.getName().getString());
        String network = "RTT  " + (rtt >= 0 ? rtt + " ms" : "—");
        if (watchingOther) network = snapshot.subjectName() + "  " + network;
        double down = LocalPhase.waitMs();
        double up = snapshot == null ? PhaseMath.returnWaitMs(down, rtt)
            : Float.isFinite(snapshot.livePhaseMs()) ? snapshot.livePhaseMs() : snapshot.meanMs();
        boolean timeline = rtt >= 0 && Double.isFinite(up);
        if (timeline) {
            if (snapshot != null) down = PhaseMath.returnWaitMs(up, rtt);
            network += (snapshot == null ? "   等待 ↑≈" : "   等待 ↑") + Math.round(up)
                + (snapshot == null ? "  ↓" : "  ↓≈") + Math.round(down) + " ms";
        }
        int moved = watchingOther ? -1 : AttackTrace.latestMs(now);
        String hit = moved >= 0 ? "攻击 → 位移  " + moved + " ms" : "";
        Font font = mc.font;
        int maxWidth = graphics.guiWidth() - 16;
        if (maxWidth <= 0) return;
        network = font.plainSubstrByWidth(network, maxWidth);
        hit = font.plainSubstrByWidth(hit, maxWidth);
        int width = Math.max(Math.max(font.width(network), font.width(hit)), timeline ? Math.min(180, maxWidth) : 0);
        int row = font.lineHeight + 2;
        int graphHeight = timeline ? PhaseTimeline.height(font) + 3 : 0;
        graphics.fill(4, 4, 12 + width, 10 + font.lineHeight + graphHeight + (hit.isEmpty() ? 0 : row), 0xAA101317);
        graphics.drawString(font, network, 8, 7, 0xFFE7EAEC, false);
        if (timeline) PhaseTimeline.render(graphics, font, 8, 7 + row, width, up, rtt);
        if (!hit.isEmpty()) graphics.drawString(font, hit, 8, 7 + row + graphHeight, 0xFF9FDFCC, false);
    }
}
