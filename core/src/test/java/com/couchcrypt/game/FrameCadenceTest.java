package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameCadenceTest {
    @Test void productionThirtyFpsUsesEverySecondSixtyHzTickAndEveryFourthOneTwentyTick() {
        for (float hz : new float[]{59.94f, 60, 90, 119.88f, 120}) {
            FrameCadence cadence = new FrameCadence(); cadence.configure(hz);
            assertEquals(30, cadence.targetFps());
            int divisor = Math.round(hz / 30);
            for (int i = 0; i < 3_600; i++) {
                long jitter = i % 2 == 0 ? 70_000 : -70_000;
                assertEquals(i % divisor == 0,
                    cadence.due(1_000_000_000L + i * cadence.displayPeriodNs() + jitter), "Tick " + i + " at " + hz);
            }
        }
    }
    @Test void thirtyFpsPauseResetDropsOldClockPhaseAndPendingCredits() {
        FrameCadence cadence = new FrameCadence(); FrameRequestGate gate = new FrameRequestGate();
        assertTrue(cadence.due(1_000_000_000L));
        gate.offer(1_000_000_000L);
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(1_000_000_000L, 8_333_333));
        long oldTicket = gate.beginDraw();
        cadence.reset(); gate.reset();
        assertFalse(gate.complete(oldTicket));
        long resumed = 90_000_000_000L, tick = cadence.displayPeriodNs();
        assertEquals(FrameRequestGate.EMPTY, gate.claim(resumed, tick / 2));
        assertTrue(cadence.due(resumed));
        assertFalse(cadence.due(resumed + tick));
        assertTrue(cadence.due(resumed + tick * 2));
    }
    @Test void sixtyAndFractionalTelevisionRatesNeverAccidentallyHalve() {
        for (float hz : new float[]{59.94f, 60, 60.01f}) {
            FrameCadence cadence = new FrameCadence(60); cadence.configure(hz);
            long period = cadence.displayPeriodNs();
            for (int i = 0; i < 3_600; i++) {
                long jitter = i % 2 == 0 ? 70_000 : -70_000;
                assertTrue(cadence.due(1_000_000_000L + i * period + jitter), "Missed display tick " + i + " at " + hz);
            }
        }
    }
    @Test void highRefreshRequestsEverySecondTickAndNinetyKeepsSixtyAverage() {
        for (float hz : new float[]{119.88f, 120, 90}) {
            FrameCadence cadence = new FrameCadence(60); cadence.configure(hz);
            int requests = 0, ticks = Math.round(hz * 10);
            for (int i = 0; i < ticks; i++) if (cadence.due(1_000_000_000L + i * cadence.displayPeriodNs())) requests++;
            assertEquals(600, requests, 2, "Ten-second target at " + hz);
        }
    }
    @Test void stallsDoNotQueueCatchUpBurstsAndRefreshChangesResetPhase() {
        FrameCadence cadence = new FrameCadence(60); cadence.configure(120);
        assertTrue(cadence.due(1_000_000_000L));
        assertFalse(cadence.due(1_008_333_333L));
        assertTrue(cadence.due(5_000_000_000L));
        assertFalse(cadence.due(5_008_333_333L));
        cadence.configure(60);
        assertTrue(cadence.due(5_010_000_000L));
        cadence.reset(); assertTrue(cadence.due(9_000_000_000L));
    }
    @Test void slowerDisplaysRenderTheirAvailableTicksAndInvalidRatesFallBackToSixty() {
        FrameCadence cadence = new FrameCadence(60); cadence.configure(50);
        for (int i = 0; i < 1_000; i++) assertTrue(cadence.due(1_000_000_000L + i * 20_000_000L));
        for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            cadence.configure(invalid); assertEquals(Math.round(1_000_000_000d / 60), cadence.displayPeriodNs());
        }
    }
}
