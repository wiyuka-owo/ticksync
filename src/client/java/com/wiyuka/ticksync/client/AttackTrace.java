package com.wiyuka.ticksync.client;

public final class AttackTrace {

    private static final long COLLECT_NANOS = 1_500_000_000L;
    private static final long DISPLAY_NANOS = 6_000_000_000L;

    private static final Object LOCK = new Object();
    private static Pending pending;
    private static volatile Snapshot display;

    private AttackTrace() {
    }

    public static void clear() {
        synchronized (LOCK) {
            pending = null;
        }
        display = null;
    }

    public static Snapshot display() {
        Snapshot snap = display;
        if (snap == null) {
            return null;
        }
        if (System.nanoTime() - snap.publishedNanos() > DISPLAY_NANOS) {
            display = null;
            return null;
        }
        return snap;
    }

    public static void onAttackKeyPress(int targetId, int attackerId, long originNanos) {
        synchronized (LOCK) {
            pending = new Pending(targetId, attackerId, originNanos);
        }
    }

    public static void onAttackSent(int entityId, long sendNanos) {
        synchronized (LOCK) {
            Pending p = pending;
            if (p == null || p.sendNanos != Long.MIN_VALUE) {
                return;
            }
            if (p.targetId >= 0 && p.targetId != entityId) {
                return;
            }
            if (p.targetId < 0) {
                p.targetId = entityId;
            }
            if (sendNanos - p.originNanos > COLLECT_NANOS) {
                pending = null;
                return;
            }
            p.sendNanos = sendNanos;
        }
    }

    public static void onDamageArrived(int entityId, int causeId, long arriveNanos) {
        synchronized (LOCK) {
            Pending p = pending;
            if (p == null || p.targetId != entityId) {
                return;
            }
            if (causeId > 0 && causeId != p.attackerId) {
                return;
            }
            if (arriveNanos - p.originNanos > COLLECT_NANOS) {
                pending = null;
                return;
            }
            if (p.damageArriveNanos == Long.MIN_VALUE) {
                p.damageArriveNanos = arriveNanos;
            }
            p.confirmed = true;
            publishLocked(p);
        }
    }

    public static void onMotionArrived(int entityId, long arriveNanos) {
        synchronized (LOCK) {
            Pending p = pending;
            if (p == null || p.targetId != entityId) {
                return;
            }
            if (arriveNanos - p.originNanos > COLLECT_NANOS) {
                return;
            }
            if (p.motionArriveNanos == Long.MIN_VALUE) {
                p.motionArriveNanos = arriveNanos;
                publishLocked(p);
            }
        }
    }

    public static void onHurtAnimationProcessed(int entityId, long processNanos) {
        synchronized (LOCK) {
            Pending p = pending;
            if (p == null || p.targetId != entityId) {
                return;
            }
            if (processNanos - p.originNanos > COLLECT_NANOS) {
                pending = null;
                return;
            }
            if (p.animProcessNanos == Long.MIN_VALUE) {
                p.animProcessNanos = processNanos;
            }
            p.confirmed = true;
            publishLocked(p);
        }
    }

    private static void publishLocked(Pending p) {
        if (!p.confirmed || p.sendNanos == Long.MIN_VALUE) {
            return;
        }
        display = new Snapshot(
            p.originNanos,
            p.sendNanos,
            p.damageArriveNanos,
            p.motionArriveNanos,
            p.animProcessNanos,
            System.nanoTime());
    }

    private static final class Pending {
        int targetId;
        final int attackerId;
        final long originNanos;
        long sendNanos = Long.MIN_VALUE;
        long damageArriveNanos = Long.MIN_VALUE;
        long motionArriveNanos = Long.MIN_VALUE;
        long animProcessNanos = Long.MIN_VALUE;
        boolean confirmed;

        Pending(int targetId, int attackerId, long originNanos) {
            this.targetId = targetId;
            this.attackerId = attackerId;
            this.originNanos = originNanos;
        }
    }

    public record Snapshot(
        long originNanos,
        long sendNanos,
        long damageArriveNanos,
        long motionArriveNanos,
        long animProcessNanos,
        long publishedNanos
    ) {
        public double sendMs() {
            return ms(sendNanos);
        }

        public double damageMs() {
            return ms(damageArriveNanos);
        }

        public double motionMs() {
            return ms(motionArriveNanos);
        }

        public double animMs() {
            return ms(animProcessNanos);
        }

        public double spanMs() {
            double end = 0.0;
            end = maxFinite(end, sendMs());
            end = maxFinite(end, damageMs());
            end = maxFinite(end, motionMs());
            end = maxFinite(end, animMs());
            return Math.max(150.0, end + 40.0);
        }

        private static double maxFinite(double a, double b) {
            return Double.isNaN(b) ? a : Math.max(a, b);
        }

        private double ms(long nanos) {
            return nanos == Long.MIN_VALUE ? Double.NaN : (nanos - originNanos) / 1_000_000.0;
        }
    }
}
