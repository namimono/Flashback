package com.moulberry.flashback.playback;

public final class ReplayTickScheduleTest {
    public static void main(String[] args) {
        long interval = 50_000_000L;
        long now = 1_000_000_000L;
        long deadline = now;

        // MinecraftServer advances its deadline before each tickServer call. Export seeks
        // bypass the wait, processing many ticks faster than their real-time interval.
        for (int tick = 0; tick < 400; tick++) {
            deadline += interval;
            deadline = ReplayTickSchedule.afterJump(deadline, now, interval);
            now += 5_000_000L;
        }

        // A pending snapshot suppresses client rendering until the first post-export tick.
        long firstEditorFrameDelay = Math.max(0, deadline - now);
        if (firstEditorFrameDelay > interval) {
            throw new AssertionError("Editor waits " + firstEditorFrameDelay / 1_000_000
                + " ms for the first post-export tick");
        }
        if (ReplayTickSchedule.afterJump(now - interval, now, interval) != now - interval) {
            throw new AssertionError("An overdue tick must not be postponed");
        }
        if (ReplayTickSchedule.afterJump(now + interval, now, interval) != now + interval) {
            throw new AssertionError("A normally scheduled tick must not change");
        }
        long slowInterval = 1_000_000_000L;
        if (ReplayTickSchedule.afterJump(now + 10 * slowInterval, now, slowInterval) != now + slowInterval) {
            throw new AssertionError("The replay's configured tick interval must be respected");
        }
        System.out.println("PASS: fast export leaves at most one tick of scheduled wait");
    }
}
