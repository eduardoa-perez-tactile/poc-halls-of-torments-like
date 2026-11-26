package com.couchcrypt.game;

/** Display-tick aligned 60 FPS requests; never sleeps or changes simulation time. */
public final class FrameCadence {
    public static final long TARGET_PERIOD_NS = 1_000_000_000L / 60;
    private long displayPeriodNs = TARGET_PERIOD_NS;
    private long framePeriodNs = TARGET_PERIOD_NS;
    private long nextFrameNs;

    public void configure(float refreshHz) {
        if (!Float.isFinite(refreshHz) || refreshHz < 20 || refreshHz > 1_000) refreshHz = 60;
        long period = Math.round(1_000_000_000d / refreshHz);
        int multiple = Math.max(1, Math.round(refreshHz / 60));
        long target = Math.abs(refreshHz / multiple - 60) <= .2f ? period * multiple : TARGET_PERIOD_NS;
        if (period != displayPeriodNs || target != framePeriodNs) {
            displayPeriodNs = period; framePeriodNs = target; reset();
        }
    }
    public void reset() { nextFrameNs = 0; }
    public long displayPeriodNs() { return displayPeriodNs; }
    public long framePeriodNs() { return framePeriodNs; }

    /** Consumes this display tick even when the caller must drop its request due to backpressure. */
    public boolean due(long frameTimeNs) {
        long tolerance = displayPeriodNs / 2;
        if (nextFrameNs == 0) nextFrameNs = frameTimeNs;
        if (frameTimeNs + tolerance < nextFrameNs) return false;
        nextFrameNs += framePeriodNs;
        // Never issue a burst of catch-up renders after a stall or a slower display.
        if (nextFrameNs < frameTimeNs - tolerance) nextFrameNs = frameTimeNs + framePeriodNs;
        return true;
    }
}
