package com.couchcrypt.game;

/** Display-tick aligned fixed frame requests; defaults to 30 FPS without changing simulation time. */
public final class FrameCadence {
    public static final int DEFAULT_TARGET_FPS = 30;
    public static final long TARGET_PERIOD_NS = 1_000_000_000L / DEFAULT_TARGET_FPS;
    private final int targetFps;
    private final long nominalFramePeriodNs;
    private long displayPeriodNs = Math.round(1_000_000_000d / 60);
    private long framePeriodNs;
    private long nextFrameNs;

    public FrameCadence() { this(DEFAULT_TARGET_FPS); }
    public FrameCadence(int targetFps) {
        if (targetFps < 1 || targetFps > 240) throw new IllegalArgumentException("Target FPS must be 1–240");
        this.targetFps = targetFps;
        nominalFramePeriodNs = 1_000_000_000L / targetFps;
        configure(60);
    }
    public void configure(float refreshHz) {
        if (!Float.isFinite(refreshHz) || refreshHz < 20 || refreshHz > 1_000) refreshHz = 60;
        long period = Math.round(1_000_000_000d / refreshHz);
        int multiple = Math.max(1, Math.round(refreshHz / targetFps));
        long target = Math.abs(refreshHz / multiple - targetFps) <= .2f ? period * multiple : nominalFramePeriodNs;
        if (period != displayPeriodNs || target != framePeriodNs) {
            displayPeriodNs = period; framePeriodNs = target; reset();
        }
    }
    public void reset() { nextFrameNs = 0; }
    public long displayPeriodNs() { return displayPeriodNs; }
    public long framePeriodNs() { return framePeriodNs; }
    /** Leave a tenth of the period for submission; UI and post-swap admission use the same deadline. */
    public long maximumRequestAgeNs() { return framePeriodNs * 9 / 10; }
    public int targetFps() { return targetFps; }

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
