package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefeatTransitionTest {
    @Test void holdsTheDefeatedArenaThenSwapsOnlyAtBlackBeforeRevealingResults() {
        DefeatTransition transition = new DefeatTransition();
        assertFalse(transition.active()); assertFalse(transition.needsResults()); assertEquals(0, transition.opacity());
        transition.begin();
        assertTrue(transition.active());
        transition.update(DefeatTransition.HOLD_SECONDS, true);
        assertEquals(0, transition.opacity()); assertFalse(transition.needsResults());
        transition.update(DefeatTransition.FADE_OUT_SECONDS * .5f, true);
        assertEquals(.5f, transition.opacity(), .00001f); assertFalse(transition.needsResults());
        transition.update(1, true);
        assertEquals(1, transition.opacity()); assertTrue(transition.needsResults());
        transition.resultsShown();
        assertEquals(1, transition.opacity()); assertFalse(transition.needsResults());
        transition.update(DefeatTransition.FADE_IN_SECONDS * .5f, true);
        assertEquals(.5f, transition.opacity(), .00001f); assertTrue(transition.active());
        transition.update(1, true);
        assertEquals(0, transition.opacity()); assertFalse(transition.active()); assertFalse(transition.needsResults());
    }

    @Test void overshootCannotSkipBlackOrRevealBeforeTheActualResultsSwap() {
        DefeatTransition transition = new DefeatTransition();
        transition.resultsShown(); // An accidental early acknowledgement cannot bypass the boundary.
        transition.begin(); transition.resultsShown();
        transition.update(100, true);
        assertEquals(1, transition.opacity()); assertTrue(transition.needsResults()); assertTrue(transition.active());
        transition.update(100, true);
        assertEquals(1, transition.opacity()); assertTrue(transition.needsResults());
        transition.resultsShown(); transition.resultsShown();
        assertEquals(1, transition.opacity(), "The acknowledgement itself must not advance the reveal");
        transition.update(.1f, true);
        assertTrue(transition.opacity() > 0 && transition.opacity() < 1);
    }

    @Test void beginCannotRestartAnActiveOrCompletedCeremonyUntilReset() {
        DefeatTransition transition = new DefeatTransition();
        transition.begin(); transition.update(.6f, true);
        float opacity = transition.opacity();
        transition.begin(); assertEquals(opacity, transition.opacity());
        transition.update(100, true); transition.resultsShown(); transition.update(100, true);
        transition.begin(); transition.update(.6f, true);
        assertFalse(transition.active()); assertEquals(0, transition.opacity());
        transition.reset(); transition.begin();
        assertTrue(transition.active()); assertEquals(0, transition.opacity()); assertFalse(transition.needsResults());
    }

    @Test void backgroundAndInvalidDeltasFreezeBothFadesAndResetClearsTheSwapRequest() {
        DefeatTransition transition = new DefeatTransition();
        transition.begin(); transition.update(.6f, true);
        float opacity = transition.opacity();
        transition.update(100, false);
        for (float delta : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1, 0})
            transition.update(delta, true);
        assertEquals(opacity, transition.opacity()); assertFalse(transition.needsResults());
        transition.update(100, true); transition.resultsShown(); transition.update(.2f, true);
        opacity = transition.opacity(); transition.update(100, false);
        assertEquals(opacity, transition.opacity()); assertTrue(transition.active());
        transition.reset();
        assertEquals(0, transition.opacity()); assertFalse(transition.active()); assertFalse(transition.needsResults());
        transition.begin(); transition.update(DefeatTransition.BLACK_SECONDS, true);
        assertEquals(1, transition.opacity()); assertTrue(transition.needsResults());
        transition.reset(); assertFalse(transition.needsResults());
    }
}
