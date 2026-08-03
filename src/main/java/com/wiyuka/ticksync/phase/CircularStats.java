package com.wiyuka.ticksync.phase;

import java.util.Arrays;

// 通过将样本视为圆周上的单位进行向量求和，长度即为样本的集中程度
public final class CircularStats {

    private final double periodMs;
    private final double radiansPerMs;
    private final double[] window;
    private int size;
    private int cursor;

    public CircularStats(double periodMs, int capacity) {
        this.periodMs = periodMs;
        this.radiansPerMs = (Math.PI * 2) / periodMs;
        this.window = new double[capacity];
    }

    public void add(double sampleMs) {
        window[cursor] = wrap(sampleMs);
        cursor = (cursor + 1) % window.length;
        if (size < window.length) {
            size++;
        }
    }

    public void clear() {
        size = 0;
        cursor = 0;
    }

    public int count() {
        return size;
    }

    public double meanMs() {
        if (size == 0) {
            return Double.NaN;
        }
        Resultant r = resultant();
        return wrap(Math.atan2(r.sin(), r.cos()) / radiansPerMs);
    }

    public double sigmaMs() {
        if (size < 2) {
            return Double.NaN;
        }
        double meanLength = resultant().length() / size;
        if (meanLength <= 1e-9) {
            return periodMs;
        }
        double sigmaRadians = Math.sqrt(-2.0 * Math.log(Math.min(1.0, meanLength)));
        return Math.min(periodMs, sigmaRadians / radiansPerMs);
    }

    public double[] snapshot() {
        double[] out = new double[size];
        System.arraycopy(window, 0, out, 0, size);
        return out;
    }

    public double[] sortedOffsetsMs() {
        if (size == 0) {
            return new double[0];
        }
        double mean = meanMs();
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = wrapSignedAroundZero(window[i] - mean);
        }
        Arrays.sort(out);
        return out;
    }

    public double offsetQuantileMs(double quantile) {
        double[] sorted = sortedOffsetsMs();
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int index = (int) Math.ceil(quantile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private Resultant resultant() {
        double sin = 0.0;
        double cos = 0.0;
        for (int i = 0; i < size; i++) {
            double theta = window[i] * radiansPerMs;
            sin += Math.sin(theta);
            cos += Math.cos(theta);
        }
        return new Resultant(sin, cos);
    }

    private record Resultant(double sin, double cos) {
        double length() {
            return Math.hypot(sin, cos);
        }
    }

    private double wrap(double value) {
        double m = value % periodMs;
        return m < 0.0 ? m + periodMs : m;
    }

    private double wrapSignedAroundZero(double value) {
        double half = periodMs / 2.0;
        double m = value % periodMs;
        if (m < -half) {
            return m + periodMs;
        }
        return m >= half ? m - periodMs : m;
    }
}
