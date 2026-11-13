package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import java.util.Arrays;

/** Opt-in render-thread diagnostics. Storage is fixed; only five-second reports allocate text. */
public final class FrameMetrics {
    private static final int CAPACITY = 2048;
    private static final long REPORT_NS = 5_000_000_000L;
    private static final long BUDGET_NS = 16_700_000L, DOUBLE_BUDGET_NS = 33_400_000L;
    private final long[] intervals = new long[CAPACITY], cpu = new long[CAPACITY];
    private long previousBegin, frameBegin, interval, windowBegin;
    private int count, intervalCount, overBudget, overDoubleBudget;
    private String phase;
    private long lastTextures, lastPixmaps, lastHeap;
    private int lastDraws;

    /** Call before any simulation, networking handoff, UI or rendering work. */
    public void beginFrame() {
        frameBegin = System.nanoTime();
        interval = previousBegin == 0 ? 0 : frameBegin - previousBegin;
        previousBegin = frameBegin;
    }

    /** CPU time measures render() submission, not asynchronous GPU execution or presentation. */
    public void endFrame(String mode, long textureBytes, long retainedPixmapBytes, long javaHeapBytes, int drawCalls) {
        long now = System.nanoTime();
        if (phase != null && !phase.equals(mode)) {
            report(lastTextures, lastPixmaps, lastHeap, lastDraws);
            clearWindow();
        }
        phase = mode;
        lastTextures = textureBytes; lastPixmaps = retainedPixmapBytes; lastHeap = javaHeapBytes; lastDraws = drawCalls;
        if (windowBegin == 0) windowBegin = frameBegin;
        cpu[count++] = Math.max(0, now - frameBegin);
        if (interval > 0) {
            intervals[intervalCount++] = interval;
            if (interval > BUDGET_NS) overBudget++;
            if (interval > DOUBLE_BUDGET_NS) overDoubleBudget++;
        }
        if (now - windowBegin >= REPORT_NS || count == CAPACITY) {
            report(textureBytes, retainedPixmapBytes, javaHeapBytes, drawCalls);
            clearWindow();
        }
    }

    /** Exclude time spent backgrounded. Also use when creating/replacing a renderer. */
    public void reset() { previousBegin = frameBegin = interval = 0; phase = null; clearWindow(); }

    private void clearWindow() {
        count = intervalCount = overBudget = overDoubleBudget = 0;
        windowBegin = 0;
    }

    private void report(long textures, long pixmaps, long heap, int draws) {
        if (count == 0) return;
        Arrays.sort(intervals, 0, intervalCount);
        Arrays.sort(cpu, 0, count);
        StringBuilder out = new StringBuilder(320);
        out.append("phase=").append(phase).append(" frames=").append(count)
            .append(" intervalSamples=").append(intervalCount);
        distribution(out, "interval", intervals, intervalCount);
        distribution(out, "cpu", cpu, count);
        out.append(" over16.7ms=").append(overBudget).append(" over33.4ms=").append(overDoubleBudget)
            .append(" gpuTextureBytes=").append(textures).append(" retainedPixmapBytes=").append(pixmaps)
            .append(" javaHeapBytes=").append(heap).append(" drawCalls=").append(draws);
        Gdx.app.log("FrameMetrics", out.toString());
    }

    private static void distribution(StringBuilder out, String name, long[] sorted, int size) {
        out.append(' ').append(name).append("Ms[p95=");
        milliseconds(out, percentile(sorted, size, 95));
        out.append(",p99="); milliseconds(out, percentile(sorted, size, 99));
        out.append(",max="); milliseconds(out, size == 0 ? 0 : sorted[size - 1]); out.append(']');
    }

    private static long percentile(long[] sorted, int size, int percent) {
        return size == 0 ? 0 : sorted[Math.max(0, (size * percent + 99) / 100 - 1)];
    }

    private static void milliseconds(StringBuilder out, long nanos) {
        long hundredths = (nanos + 5_000) / 10_000;
        out.append(hundredths / 100).append('.');
        if (hundredths % 100 < 10) out.append('0');
        out.append(hundredths % 100);
    }
}
