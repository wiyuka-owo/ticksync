package com.wiyuka.ticksync.api;

public enum Confidence {

    NONE,

    LOW,

    GOOD;

    public boolean atLeast(Confidence other) {
        return compareTo(other) >= 0;
    }
}
