package com.moulberry.flashback.playback;

final class ReplayTickSchedule {
    private ReplayTickSchedule() {}

    static long afterJump(long nextTickTimeNanos, long nowNanos, long nanosPerTick) {
        return Math.min(nextTickTimeNanos, nowNanos + nanosPerTick);
    }
}
