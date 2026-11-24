package com.couchcrypt.game;

/** One bounded defeat ceremony clock; the host owns the screen swap at full black. */
public final class DefeatTransition {
    public static final float HOLD_SECONDS = .35f, FADE_OUT_SECONDS = .55f, FADE_IN_SECONDS = .65f;
    public static final float BLACK_SECONDS = HOLD_SECONDS + FADE_OUT_SECONDS;
    public static final float TOTAL_SECONDS = BLACK_SECONDS + FADE_IN_SECONDS;
    private boolean started, resultsShown;
    private float elapsed;

    /** Latches once until reset, so repeated defeat observations cannot restart the ceremony. */
    public void begin() {
        if (started) return;
        started = true;
    }

    public void reset() {
        started = resultsShown = false;
        elapsed = 0;
    }

    public void update(float delta, boolean hostActive) {
        if (!active() || !hostActive || !Float.isFinite(delta) || delta <= 0) return;
        // Discard overshoot at the swap boundary: one update cannot jump past black.
        elapsed = Math.min(resultsShown ? TOTAL_SECONDS : BLACK_SECONDS, elapsed + delta);
    }

    public boolean active() { return started && elapsed < TOTAL_SECONDS; }

    /** Remains requested at black until the host acknowledges its actual screen swap. */
    public boolean needsResults() { return started && !resultsShown && elapsed >= BLACK_SECONDS; }

    public void resultsShown() {
        if (needsResults()) resultsShown = true;
    }

    public float opacity() {
        if (!started || elapsed <= HOLD_SECONDS || elapsed >= TOTAL_SECONDS) return 0;
        if (elapsed == BLACK_SECONDS) return 1;
        if (elapsed <= BLACK_SECONDS)
            return smooth((elapsed - HOLD_SECONDS) / FADE_OUT_SECONDS);
        return 1 - smooth((elapsed - BLACK_SECONDS) / FADE_IN_SECONDS);
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
