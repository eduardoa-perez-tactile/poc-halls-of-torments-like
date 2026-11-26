package com.couchcrypt.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameMetricsTest {
    @Test void worldPoolSnapshotIncludesExpansionAndKeepsFrameSampledPeaksAfterReset() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        ArenaWorld world = new ArenaWorld();
        world.meleeWaves[0].active = world.meleeWaves[1].active = true;
        world.skills.orbiters[0].active = world.skills.fields[0].active = world.skills.glaives[0].active = true;
        world.skills.relics[0].active = world.skills.delayed[0].active = world.chests[0].active = true;
        metrics.beginFrame(); metrics.recordPoolCounts(world); metrics.endFrame("coop", 1, 2, 3, 4);
        world.reset();
        metrics.beginFrame(); metrics.recordPoolCounts(world); metrics.endFrame("coop", 1, 2, 3, 4);
        metrics.beginFrame(); metrics.endFrame("home", 1, 2, 3, 4);
        metrics.beginFrame(); metrics.endFrame("lobby", 1, 2, 3, 4);
        assertEquals(2, logs.size());
        String combat = logs.get(0), home = logs.get(1);
        assertTrue(combat.contains("meleeWaves[last=0,frameSampledPeak=2]"), combat);
        for (String pool : new String[]{"orbiters", "fields", "glaives", "relics", "delayedStrikes", "chests"}) {
            assertTrue(combat.contains(pool + "[last=0,frameSampledPeak=1]"), combat);
            assertTrue(home.contains(pool + "[last=0,frameSampledPeak=0]"), home);
        }
    }

    @Test void rendererStagesNestInsideArenaAndRenderCountsResetAcrossPhases() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        metrics.beginFrame(); metrics.beginStage(FrameMetrics.ARENA);
        metrics.beginStage(FrameMetrics.FLOOR); clock.ms(1); metrics.endStage(FrameMetrics.FLOOR);
        metrics.beginStage(FrameMetrics.GATHER); clock.ms(2); metrics.endStage(FrameMetrics.GATHER);
        metrics.beginStage(FrameMetrics.WORLD_DRAW); clock.ms(3); metrics.endStage(FrameMetrics.WORLD_DRAW);
        metrics.beginStage(FrameMetrics.FLOOR); clock.ms(1); metrics.endStage(FrameMetrics.FLOOR);
        metrics.beginStage(FrameMetrics.WORLD_DRAW); clock.ms(2); metrics.endStage(FrameMetrics.WORLD_DRAW);
        metrics.beginStage(FrameMetrics.HUD); clock.ms(1); metrics.endStage(FrameMetrics.HUD);
        metrics.endStage(FrameMetrics.ARENA);
        metrics.recordRenderCounts(2, 24, 6, 19, 42, 3);
        metrics.endFrame("coop", 1, 2, 3, 32);
        metrics.beginFrame(); metrics.endFrame("home", 1, 2, 3, 4);
        metrics.beginFrame(); metrics.endFrame("lobby", 1, 2, 3, 4);
        assertEquals(2, logs.size());
        String coop = logs.get(0), home = logs.get(1);
        assertTrue(coop.contains("arenaMs[p50=10.00,p95=10.00,p99=10.00,max=10.00]"), coop);
        assertTrue(coop.contains("floorMs[p50=2.00,p95=2.00,p99=2.00,max=2.00]"), coop);
        assertTrue(coop.contains("gatherMs[p50=2.00,p95=2.00,p99=2.00,max=2.00]"), coop);
        assertTrue(coop.contains("worldDrawMs[p50=5.00,p95=5.00,p99=5.00,max=5.00]"), coop);
        assertTrue(coop.contains("hudMs[p50=1.00,p95=1.00,p99=1.00,max=1.00]"), coop);
        assertTrue(coop.contains("dynamicSpriteBatchVertexUploads[min=3,p50=3,p95=3,p99=3,max=3]"), coop);
        assertTrue(coop.contains("renderStages=nestedInArenaNonadditive"), coop);
        assertTrue(coop.contains("floorDraws[min=2,p50=2,p95=2,p99=2,max=2]"), coop);
        assertTrue(coop.contains("worldDraws[min=24,p50=24,p95=24,p99=24,max=24]"), coop);
        assertTrue(coop.contains("hudDraws[min=6,p50=6,p95=6,p99=6,max=6]"), coop);
        assertTrue(coop.contains("dynamicSpriteBatchTextureSelections[min=19,p50=19,p95=19,p99=19,max=19]"), coop);
        assertTrue(coop.contains("visibleSprites[min=42,p50=42,p95=42,p99=42,max=42]"), coop);
        assertTrue(home.contains("visibleSprites[min=0,p50=0,p95=0,p99=0,max=0]"), home);
        assertTrue(home.contains("dynamicSpriteBatchVertexUploads[min=0,p50=0,p95=0,p99=0,max=0]"), home);
        assertTrue(home.contains("floorMs[p50=0.00,p95=0.00,p99=0.00,max=0.00]"), home);
    }

    @Test void repeatedStagesAccumulateAndAbsentStagesContributeZero() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        metrics.beginFrame();
        metrics.beginStage(FrameMetrics.HANDOFF); clock.ms(2); metrics.endStage(FrameMetrics.HANDOFF);
        metrics.beginStage(FrameMetrics.HANDOFF); clock.ms(3); metrics.endStage(FrameMetrics.HANDOFF);
        metrics.beginStage(FrameMetrics.ARENA); clock.ms(7); metrics.endStage(FrameMetrics.ARENA);
        metrics.endFrame("coop", 100, 200, 300, 8);
        metrics.beginFrame(); clock.ms(1); metrics.endFrame("pause", 100, 200, 300, 1);
        String log = only(logs);
        assertTrue(log.contains("phase=coop frames=1 intervalSamples=0"), log);
        assertTrue(log.contains("handoffMs[p50=5.00,p95=5.00,p99=5.00,max=5.00]"), log);
        assertTrue(log.contains("arenaMs[p50=7.00,p95=7.00,p99=7.00,max=7.00]"), log);
        assertTrue(log.contains("simulationMs[p50=0.00,p95=0.00,p99=0.00,max=0.00]"), log);
        assertTrue(log.contains("cpuMs[p95=12.00,p99=12.00,max=12.00] cpuMetric=renderCallbackWall"), log);
        assertTrue(log.contains("stageSamples=allFramesIncludingZero"), log);
    }

    @Test void phaseChangeKeepsCurrentMetadataOutOfPreviousWindowAndResetsStages() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        metrics.beginFrame();
        metrics.beginStage(FrameMetrics.SIMULATION); clock.ms(2); metrics.endStage(FrameMetrics.SIMULATION);
        metrics.addSimulationSteps(2, .04f); metrics.recordPoolCounts(10, 11, 12, 13, 3, 14);
        metrics.endFrame("solo", 100, 200, 300, 8);
        metrics.beginFrame();
        metrics.beginStage(FrameMetrics.UI); clock.ms(4); metrics.endStage(FrameMetrics.UI);
        metrics.addSimulationSteps(0, .2f); metrics.recordPoolCounts(30, 31, 32, 33, 4, 34);
        metrics.endFrame("pause", 1000, 2000, 3000, 2);
        metrics.beginFrame(); clock.ms(1); metrics.endFrame("results", 0, 0, 0, 1);
        assertEquals(2, logs.size());
        String solo = logs.get(0), pause = logs.get(1);
        assertTrue(solo.contains("javaHeapBytes[min=300,max=300]"), solo);
        assertTrue(solo.contains("enemies[last=10,frameSampledPeak=10]"), solo);
        assertTrue(solo.contains("simulationStepsTotal=2 deltaClampedFrames=0"), solo);
        assertTrue(pause.contains("phase=pause frames=1 intervalSamples=1"), pause);
        assertTrue(pause.contains("javaHeapBytes[min=3000,max=3000]"), pause);
        assertTrue(pause.contains("enemies[last=30,frameSampledPeak=30]"), pause);
        assertTrue(pause.contains("simulationMs[p50=0.00,p95=0.00,p99=0.00,max=0.00]"), pause);
        assertTrue(pause.contains("simulationStepsTotal=0 deltaClampedFrames=1"), pause);
    }

    @Test void reportsDistributionsMemoryRangesAndFrameSampledPoolPeaks() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        metrics.beginFrame(); clock.ms(2);
        metrics.recordPoolCounts(20, 8, 40, 3, 2, 5); metrics.addSimulationSteps(2, .02f);
        metrics.endFrame("coop", 100, 200, 400, 30);
        clock.ms(18); metrics.beginFrame(); clock.ms(3);
        metrics.recordPoolCounts(10, 9, 30, 4, 1, 6); metrics.addSimulationSteps(1, .03f);
        metrics.endFrame("coop", 110, 190, 500, 5);
        clock.ms(17); metrics.beginFrame(); clock.ms(4);
        metrics.recordPoolCounts(15, 7, 20, 2, 3, 4); metrics.addSimulationSteps(6, .101f);
        metrics.endFrame("coop", 105, 195, 450, 10);
        metrics.beginFrame(); metrics.endFrame("results", 0, 0, 0, 0);
        String log = only(logs);
        assertTrue(log.contains("drawCalls=10"), log);
        assertTrue(log.contains("drawCalls[min=5,p50=10,p95=30,p99=30,max=30]"), log);
        assertTrue(log.contains("javaHeapBytes[min=400,max=500]"), log);
        assertTrue(log.contains("gpuTextureBytes[min=100,max=110]"), log);
        assertTrue(log.contains("retainedPixmapBytes[min=190,max=200]"), log);
        assertTrue(log.contains("enemies[last=15,frameSampledPeak=20]"), log);
        assertTrue(log.contains("simulationSteps[min=1,p50=2,p95=6,p99=6,max=6]"), log);
        assertTrue(log.contains("simulationStepsTotal=9 deltaClampedFrames=1"), log);
        assertTrue(log.contains("over16.7ms=2 over33.4ms=0"), log);
    }

    @Test void reportingOverheadIsOutsideCallbackButAppearsInFollowingInterval() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, message -> { logs.add(message); clock.ms(20); });
        metrics.beginFrame(); clock.ms(2); metrics.endFrame("solo", 1, 2, 3, 4);
        metrics.beginFrame(); clock.ms(3); metrics.endFrame("pause", 1, 2, 3, 4);
        metrics.beginFrame(); clock.ms(1); metrics.endFrame("results", 1, 2, 3, 4);
        String pause = logs.get(1);
        assertTrue(pause.contains("renderCallbackWallMs[p50=3.00,p95=3.00,p99=3.00,max=3.00]"), pause);
        assertTrue(pause.contains("intervalMs[p95=2.00,p99=2.00,max=2.00]"), pause);
        metrics.beginFrame(); metrics.endFrame("home", 1, 2, 3, 4);
        String results = logs.get(2);
        assertTrue(results.contains("intervalMs[p95=23.00,p99=23.00,max=23.00]"), results);
    }

    @Test void capacityReportsWithoutOverflowAndResetExcludesBackgroundGap() {
        FakeClock clock = new FakeClock();
        List<String> logs = new ArrayList<>();
        FrameMetrics metrics = new FrameMetrics(clock, logs::add);
        for (int frame = 0; frame < 2048; frame++) {
            metrics.beginFrame(); clock.ns += 1000; metrics.endFrame("home", 1, 2, 3, 4);
        }
        assertTrue(only(logs).contains("frames=2048 intervalSamples=2047"));
        metrics.reset(); clock.ms(60_000); metrics.beginFrame(); clock.ms(1);
        metrics.endFrame("home", 1, 2, 3, 4);
        metrics.beginFrame(); metrics.endFrame("lobby", 1, 2, 3, 4);
        assertEquals(2, logs.size());
        assertTrue(logs.get(1).contains("phase=home frames=1 intervalSamples=0"));
    }

    private static String only(List<String> logs) { assertEquals(1, logs.size()); return logs.get(0); }

    private static final class FakeClock implements LongSupplier {
        long ns;
        void ms(long amount) { ns += amount * 1_000_000L; }
        @Override public long getAsLong() { return ns; }
    }
}
