package com.couchcrypt.game;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameRequestGateTest {
    @Test void delayedSwapKeepsAThirtyFpsCreditWhosePresentationDeadlineIsStillAhead() {
        FrameCadence cadence = new FrameCadence();
        FrameRequestGate gate = new FrameRequestGate();
        long drawing = gate.beginDraw(), tick = 1_000_000_000L;
        gate.offer(tick);
        assertEquals(FrameRequestGate.BUSY, gate.claim(tick, cadence.maximumRequestAgeNs()));
        assertTrue(gate.complete(drawing));
        long completion = tick + 20_000_000L;
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(completion, cadence.maximumRequestAgeNs()));
        long next = gate.beginDraw();
        assertTrue(gate.presentationTimeNs(next, cadence.framePeriodNs()) > completion);
        assertTrue(gate.complete(next));
        assertEquals(FrameRequestGate.EMPTY, gate.claim(completion, cadence.maximumRequestAgeNs()));
        gate.offer(tick + cadence.framePeriodNs());
        assertEquals(FrameRequestGate.EXPIRED, gate.claim(tick + cadence.framePeriodNs()
            + cadence.maximumRequestAgeNs() + 1, cadence.maximumRequestAgeNs()));
        assertFalse(gate.inFlight());
    }
    @Test void presentationTargetUsesAdmittedVsyncRatherThanDrawStartAndIsBoundToItsTicket() {
        FrameRequestGate gate = new FrameRequestGate();
        long period = 33_333_334, vsync = 1_000_000_000L;
        assertEquals(0, gate.presentationTimeNs(gate.beginDraw(), period), "Bootstrap has no owned VSYNC");
        gate.reset(); gate.offer(vsync);
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(vsync + 2_000_000, 8_333_333));
        long ticket = gate.beginDraw();
        assertEquals(vsync + period, gate.presentationTimeNs(ticket, period));
        gate.offer(vsync + period);
        assertEquals(vsync + period, gate.presentationTimeNs(ticket, period), "Pending credit cannot change the current frame target");
        assertTrue(gate.complete(ticket));
        assertEquals(0, gate.presentationTimeNs(ticket, period));
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(vsync + period + 1_000_000, 8_333_333));
        long replacement = gate.beginDraw();
        assertEquals(vsync + period * 2, gate.presentationTimeNs(replacement, period));
        assertEquals(0, gate.presentationTimeNs(ticket, period));
        gate.reset();
        assertEquals(0, gate.presentationTimeNs(replacement, period), "Pause/surface reset invalidates presentation ownership");
    }
    @Test void swapFinishingAfterNextVsyncDrainsItsCreditInsteadOfLockingAtThirtyFps() throws Exception {
        FrameRequestGate gate = new FrameRequestGate();
        long period = 16_666_667;
        AtomicInteger rendered = new AtomicInteger();
        // UI offers tick N while swap N-1 is busy; GL then finishes 1ms after tick N.
        gate.offer(1_000_000_000L);
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(1_000_000_000L, period / 2));
        for (int frame = 1; frame <= 120; frame++) {
            long previous = gate.beginDraw(), vsync = 1_000_000_000L + frame * period;
            CountDownLatch offered = new CountDownLatch(1), completed = new CountDownLatch(1);
            Thread gl = new Thread(() -> {
                try {
                    if (!offered.await(1, TimeUnit.SECONDS)) return;
                    if (gate.complete(previous) && gate.claim(vsync + 1_000_000, period / 2) == FrameRequestGate.CLAIMED)
                        rendered.incrementAndGet();
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { completed.countDown(); }
            });
            gl.start();
            gate.offer(vsync);
            assertEquals(FrameRequestGate.BUSY, gate.claim(vsync, period / 2));
            offered.countDown();
            assertTrue(completed.await(1, TimeUnit.SECONDS)); gl.join(1_000);
        }
        assertEquals(120, rendered.get(), "Every busy VSYNC remains available after its preceding swap");
    }
    @Test void latestCreditReplacesOldOnesAndExpiredCreditCannotCauseCatchUpBurst() {
        FrameRequestGate gate = new FrameRequestGate();
        long ticket = gate.beginDraw();
        for (int i = 0; i < 10_000; i++) gate.offer(1_000_000_000L + i);
        assertTrue(gate.complete(ticket));
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(1_000_010_000L, 1));
        assertTrue(gate.complete(gate.beginDraw()));
        assertEquals(FrameRequestGate.EMPTY, gate.claim(1_000_010_000L, 1));
        gate.offer(2_000_000_000L);
        assertEquals(FrameRequestGate.EXPIRED, gate.claim(2_020_000_000L, 8_333_333));
        assertFalse(gate.inFlight());
    }
    @Test void completionFromBeforePauseOrSurfaceResetCannotClearNewRequest() {
        FrameRequestGate gate = new FrameRequestGate();
        long stale = gate.beginDraw(); gate.offer(1_000_000_000L); gate.reset();
        gate.offer(2_000_000_000L);
        assertEquals(FrameRequestGate.CLAIMED, gate.claim(2_000_000_000L, 8_333_333));
        assertFalse(gate.complete(stale));
        assertTrue(gate.inFlight());
        assertTrue(gate.complete(gate.beginDraw()));
    }
}
