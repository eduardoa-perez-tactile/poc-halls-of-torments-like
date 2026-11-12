package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaHudStateTest {
    @Test void preservesReadingTimeAndFifoWithoutDuplicateSpam() {
        ArenaHudState hud = new ArenaHudState();
        hud.notify("Joined", "A companion arrives", ArenaHudState.Tone.SUCCESS, 1);
        ArenaHudState.Notice first = hud.current();
        hud.update(.25f);
        hud.notify("Joined", "A companion arrives", ArenaHudState.Tone.SUCCESS, 8);
        assertSame(first, hud.current());
        assertEquals(.75f, first.remaining);
        hud.notify("Danger", "Thirty seconds remain", ArenaHudState.Tone.WARNING, 1);
        hud.notify("Danger", "Thirty seconds remain", ArenaHudState.Tone.WARNING, 1);
        elapse(hud, 3);
        assertEquals("Danger", hud.current().title);
        assertEquals(1f, hud.current().remaining);
        elapse(hud, 4);
        assertNull(hud.current());
    }

    @Test void overflowKeepsActiveAndLetsWarningReplaceOldestPending() {
        ArenaHudState hud = new ArenaHudState();
        hud.notify("Active", "", ArenaHudState.Tone.INFO, .5f);
        hud.notify("Oldest pending", "", ArenaHudState.Tone.INFO, .5f);
        hud.notify("Latest pending", "", ArenaHudState.Tone.SUCCESS, .5f);
        hud.notify("Dropped", "", ArenaHudState.Tone.INFO, .5f);
        hud.notify("Warning", "", ArenaHudState.Tone.WARNING, .5f);
        assertEquals("Active", hud.current().title);
        elapse(hud, 2);
        assertEquals("Latest pending", hud.current().title);
        elapse(hud, 2);
        assertEquals("Warning", hud.current().title);
        elapse(hud, 2);
        assertNull(hud.current());
    }

    @Test void invalidAndLargeDeltasCannotDrainUnreadQueue() {
        ArenaHudState hud = new ArenaHudState();
        hud.notify("One", null, null, .5f);
        hud.notify("Two", "", ArenaHudState.Tone.INFO, Float.NaN);
        hud.update(Float.NaN); hud.update(Float.POSITIVE_INFINITY); hud.update(-4f);
        assertEquals(.5f, hud.current().remaining);
        hud.update(500f);
        assertEquals(.25f, hud.current().remaining);
        hud.update(500f);
        assertEquals("Two", hud.current().title);
        assertEquals(3f, hud.current().remaining);
        assertEquals("", hud.current().detail);
    }

    @Test void bossDataClampsAndResetClearsRunPresentation() {
        ArenaHudState hud = new ArenaHudState();
        hud.setBoss("Warden", "Enraged", 130f, 100f);
        assertTrue(hud.bossVisible);
        assertEquals(100f, hud.bossHealth);
        hud.setBoss(null, null, Float.NaN, Float.NEGATIVE_INFINITY);
        assertEquals(0f, hud.bossHealth);
        assertEquals(1f, hud.bossMaxHealth);
        hud.notify("Joined", "", ArenaHudState.Tone.INFO, 2f);
        hud.reset();
        assertFalse(hud.bossVisible);
        assertEquals("", hud.bossName);
        assertNull(hud.current());
    }

    private static void elapse(ArenaHudState hud, int ticks) {
        for (int i = 0; i < ticks; i++) hud.update(.25f);
    }
}
