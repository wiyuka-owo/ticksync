package com.wiyuka.ticksync.control;

import java.util.Locale;

public enum AlignMode {

    C2S("降低上行延迟"),
    S2C("降低下行延迟"),
    WORST("推到跨刻区间，额外增加一刻等待");

    private final String summary;

    AlignMode(String summary) {
        this.summary = summary;
    }

    public String summary() {
        return summary;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AlignMode byId(String id) {
        for (AlignMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return null;
    }
}
