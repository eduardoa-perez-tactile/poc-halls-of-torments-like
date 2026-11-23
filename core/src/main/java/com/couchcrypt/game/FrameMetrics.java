package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Opt-in render-thread diagnostics. Collection uses fixed storage; only reports allocate text. */
public final class FrameMetrics {
    public static final int HANDOFF = 0, SIMULATION = 1, AUDIO = 2, STATUS = 3, ARENA = 4, UI = 5;
    public static final int FLOOR = 6, GATHER = 7, WORLD_DRAW = 8, HUD = 9;
    public static final int STAGE_COUNT = 10;
    private static final String[] STAGE_NAMES = {"handoff", "simulation", "audio", "status", "arena", "ui", "floor", "gather", "worldDraw", "hud"};
    private static final String[] POOL_NAMES = {"enemies", "bolts", "pickups", "shots", "hazards", "effects"};
    private static final String[] RENDER_COUNT_NAMES = {"floorDraws", "worldDraws", "hudDraws", "dynamicSpriteBatchTextureSelections", "visibleSprites"};
    private static final int CAPACITY = 2048;
    private static final long REPORT_NS = 5_000_000_000L;
    private static final long BUDGET_NS = 16_700_000L, DOUBLE_BUDGET_NS = 33_400_000L;
    private final LongSupplier clock;
    private final Consumer<String> sink;
    private final long[] intervals = new long[CAPACITY], callback = new long[CAPACITY];
    private final long[][] stages = new long[STAGE_COUNT][CAPACITY];
    private final long[] stageStarts = new long[STAGE_COUNT], frameStages = new long[STAGE_COUNT];
    private final boolean[] stageRunning = new boolean[STAGE_COUNT];
    private final int[] draws = new int[CAPACITY], steps = new int[CAPACITY];
    private final int[][] renderCounts = new int[RENDER_COUNT_NAMES.length][CAPACITY];
    private final int[] frameRenderCounts = new int[RENDER_COUNT_NAMES.length];
    private final int[] framePools = new int[POOL_NAMES.length], lastPools = new int[POOL_NAMES.length], peakPools = new int[POOL_NAMES.length];
    private final long[] memoryMin = new long[3], memoryMax = new long[3];
    private long previousBegin, frameBegin, interval, windowBegin;
    private boolean previousFrame, frameClamped;
    private int count, intervalCount, overBudget, overDoubleBudget, frameSteps, totalSteps, clampedFrames;
    private String phase;
    private long lastTextures, lastPixmaps, lastHeap;
    private int lastDraws;

    public FrameMetrics() { this(System::nanoTime, message -> Gdx.app.log("FrameMetrics", message)); }

    FrameMetrics(LongSupplier clock, Consumer<String> sink) { this.clock = clock; this.sink = sink; }

    /** Call before any simulation, networking handoff, UI or rendering work. */
    public void beginFrame() {
        frameBegin = clock.getAsLong();
        interval = previousFrame ? Math.max(0, frameBegin - previousBegin) : 0;
        previousBegin = frameBegin;
        previousFrame = true;
        Arrays.fill(frameStages, 0);
        Arrays.fill(stageRunning, false);
        Arrays.fill(framePools, 0);
        Arrays.fill(frameRenderCounts, 0);
        frameSteps = 0; frameClamped = false;
    }

    /**
     * Host stages are disjoint; FLOOR/GATHER/WORLD_DRAW/HUD nest inside ARENA. Their durations must
     * not be summed with ARENA. Repeated stages, including each camera, accumulate within a frame.
     */
    public void beginStage(int stage) {
        if (stageRunning[stage]) throw new IllegalStateException("Stage already running: " + stage);
        stageStarts[stage] = clock.getAsLong();
        stageRunning[stage] = true;
    }

    public void endStage(int stage) {
        if (!stageRunning[stage]) throw new IllegalStateException("Stage not running: " + stage);
        frameStages[stage] += Math.max(0, clock.getAsLong() - stageStarts[stage]);
        stageRunning[stage] = false;
    }

    /** Supply actual fixed steps and the original frame delta, before the host's 100 ms clamp. */
    public void addSimulationSteps(int stepCount, float rawDelta) {
        frameSteps += Math.max(0, stepCount);
        frameClamped |= Float.isFinite(rawDelta) && rawDelta > .1f;
    }

    /** Active counts at the end of this frame; high-water marks are frame-sampled, not intra-tick peaks. */
    public void recordPoolCounts(int enemies, int bolts, int pickups, int shots, int hazards, int effects) {
        framePools[0] = enemies; framePools[1] = bolts; framePools[2] = pickups;
        framePools[3] = shots; framePools[4] = hazards; framePools[5] = effects;
    }

    /** Texture selections cover dynamic SpriteBatch switches only, excluding static mesh/other GL binds. */
    public void recordRenderCounts(int floorDraws, int worldDraws, int hudDraws, int textureSelections, int visibleSprites) {
        frameRenderCounts[0] = floorDraws; frameRenderCounts[1] = worldDraws; frameRenderCounts[2] = hudDraws;
        frameRenderCounts[3] = textureSelections; frameRenderCounts[4] = visibleSprites;
    }

    /**
     * Measures render callback wall duration, including blocking submission, not thread CPU or GPU time.
     * Every stage gets one sample per frame; a stage not executed contributes zero. Capture ends before
     * sorting/logging, while the following frame interval still includes diagnostics overhead.
     */
    public void endFrame(String mode, long textureBytes, long retainedPixmapBytes, long javaHeapBytes, int drawCalls) {
        long now = clock.getAsLong();
        if (count > 0 && !phase.equals(mode)) {
            report();
            clearWindow();
        }
        phase = mode;
        if (count == 0) windowBegin = frameBegin;
        callback[count] = Math.max(0, now - frameBegin);
        for (int stage = 0; stage < STAGE_COUNT; stage++) stages[stage][count] = frameStages[stage];
        for (int render = 0; render < frameRenderCounts.length; render++) renderCounts[render][count] = frameRenderCounts[render];
        draws[count] = drawCalls; steps[count] = frameSteps;
        totalSteps += frameSteps;
        if (frameClamped) clampedFrames++;
        memory(0, textureBytes); memory(1, retainedPixmapBytes); memory(2, javaHeapBytes);
        for (int pool = 0; pool < framePools.length; pool++) {
            lastPools[pool] = framePools[pool];
            peakPools[pool] = Math.max(peakPools[pool], framePools[pool]);
        }
        lastTextures = textureBytes; lastPixmaps = retainedPixmapBytes; lastHeap = javaHeapBytes; lastDraws = drawCalls;
        count++;
        if (interval > 0) {
            intervals[intervalCount++] = interval;
            if (interval > BUDGET_NS) overBudget++;
            if (interval > DOUBLE_BUDGET_NS) overDoubleBudget++;
        }
        if (now - windowBegin >= REPORT_NS || count == CAPACITY) {
            report();
            clearWindow();
        }
    }

    /** Exclude time spent backgrounded. Also use when creating/replacing a renderer. */
    public void reset() {
        previousFrame = false; previousBegin = frameBegin = interval = 0; phase = null;
        clearWindow();
    }

    private void memory(int index, long value) {
        memoryMin[index] = count == 0 ? value : Math.min(memoryMin[index], value);
        memoryMax[index] = count == 0 ? value : Math.max(memoryMax[index], value);
    }

    private void clearWindow() {
        count = intervalCount = overBudget = overDoubleBudget = totalSteps = clampedFrames = 0;
        windowBegin = 0;
        Arrays.fill(peakPools, 0);
    }

    private void report() {
        if (count == 0) return;
        Arrays.sort(intervals, 0, intervalCount);
        Arrays.sort(callback, 0, count);
        Arrays.sort(draws, 0, count); Arrays.sort(steps, 0, count);
        StringBuilder out = new StringBuilder(1600);
        out.append("phase=").append(phase).append(" frames=").append(count)
            .append(" intervalSamples=").append(intervalCount);
        distribution(out, "interval", intervals, intervalCount, false);
        // Legacy field retained for existing capture scripts; its meaning is explicitly corrected.
        distribution(out, "cpu", callback, count, false);
        out.append(" cpuMetric=renderCallbackWall gpuTiming=unmeasured");
        distribution(out, "renderCallbackWall", callback, count, true);
        for (int stage = 0; stage < STAGE_COUNT; stage++) {
            Arrays.sort(stages[stage], 0, count);
            distribution(out, STAGE_NAMES[stage], stages[stage], count, true);
        }
        out.append(" stageSamples=allFramesIncludingZero renderStages=nestedInArenaNonadditive over16.7ms=").append(overBudget)
            .append(" over33.4ms=").append(overDoubleBudget)
            .append(" gpuTextureBytes=").append(lastTextures).append(" retainedPixmapBytes=").append(lastPixmaps)
            .append(" javaHeapBytes=").append(lastHeap).append(" drawCalls=").append(lastDraws);
        range(out, "gpuTextureBytes", 0); range(out, "retainedPixmapBytes", 1); range(out, "javaHeapBytes", 2);
        integerDistribution(out, "drawCalls", draws);
        integerDistribution(out, "simulationSteps", steps);
        for (int render = 0; render < RENDER_COUNT_NAMES.length; render++) {
            Arrays.sort(renderCounts[render], 0, count);
            integerDistribution(out, RENDER_COUNT_NAMES[render], renderCounts[render]);
        }
        out.append(" simulationStepsTotal=").append(totalSteps).append(" deltaClampedFrames=").append(clampedFrames);
        for (int pool = 0; pool < POOL_NAMES.length; pool++)
            out.append(' ').append(POOL_NAMES[pool]).append("[last=").append(lastPools[pool])
                .append(",frameSampledPeak=").append(peakPools[pool]).append(']');
        sink.accept(out.toString());
    }

    private void range(StringBuilder out, String name, int index) {
        out.append(' ').append(name).append("[min=").append(memoryMin[index])
            .append(",max=").append(memoryMax[index]).append(']');
    }

    private void integerDistribution(StringBuilder out, String name, int[] sorted) {
        out.append(' ').append(name).append("[min=").append(sorted[0])
            .append(",p50=").append(sorted[percentileIndex(count, 50)])
            .append(",p95=").append(sorted[percentileIndex(count, 95)])
            .append(",p99=").append(sorted[percentileIndex(count, 99)])
            .append(",max=").append(sorted[count - 1]).append(']');
    }

    private static void distribution(StringBuilder out, String name, long[] sorted, int size, boolean median) {
        out.append(' ').append(name).append("Ms[");
        if (median) { out.append("p50="); milliseconds(out, percentile(sorted, size, 50)); out.append(','); }
        out.append("p95="); milliseconds(out, percentile(sorted, size, 95));
        out.append(",p99="); milliseconds(out, percentile(sorted, size, 99));
        out.append(",max="); milliseconds(out, size == 0 ? 0 : sorted[size - 1]); out.append(']');
    }

    private static long percentile(long[] sorted, int size, int percent) {
        return size == 0 ? 0 : sorted[percentileIndex(size, percent)];
    }

    private static int percentileIndex(int size, int percent) {
        return Math.max(0, (size * percent + 99) / 100 - 1);
    }

    private static void milliseconds(StringBuilder out, long nanos) {
        long hundredths = (nanos + 5_000) / 10_000;
        out.append(hundredths / 100).append('.');
        if (hundredths % 100 < 10) out.append('0');
        out.append(hundredths % 100);
    }
}
