package com.wiyuka.ticksync.command;

import com.wiyuka.ticksync.phase.PhaseMath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Locale;

final class CommandText {

    private CommandText() {
    }

    static void ok(CommandSourceStack source, String text, boolean broadcast) {
        source.sendSuccess(() -> Component.literal(text), broadcast);
    }

    static void fail(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
    }

    static String clip(String name) {
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    static String ms(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f ms", value);
    }

    static String pct(double fraction) {
        return Double.isNaN(fraction) ? "-" : String.format(Locale.ROOT, "%.0f%%", fraction * 100.0);
    }

    static String perMinute(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%+.2f/分", value);
    }

    static String drift(double msPerMinute) {
        return Double.isNaN(msPerMinute)
            ? "- (计算需要约 10 分钟连续样本)"
            : String.format(Locale.ROOT, "%+.3f ms/分 — 偏移 1 刻 (50ms) 约需 %.0f 分钟",
                msPerMinute, Math.abs(PhaseMath.PERIOD_MS / msPerMinute));
    }

    static String verdict(double sigma) {
        if (Double.isNaN(sigma)) {
            return "样本不足";
        }
        if (sigma < 5.0) {
            return "网络稳定";
        }
        return sigma < 12.0
            ? "网络存在波动"
            : "网络波动较大";
    }
}
