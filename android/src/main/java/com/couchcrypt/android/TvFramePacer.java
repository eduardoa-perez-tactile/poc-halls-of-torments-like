package com.couchcrypt.android;

import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.couchcrypt.game.FrameCadence;
import com.couchcrypt.game.FrameRequestGate;
import java.util.concurrent.atomic.AtomicLong;

/** One VSYNC callback and one requested frame; no per-frame posts, objects, sleeps, or delta override. */
final class TvFramePacer implements Choreographer.FrameCallback, SurfaceHolder.Callback, DisplayManager.DisplayListener {
    private final AndroidApplication app;
    private final GLSurfaceView view;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final FrameCadence cadence = new FrameCadence();
    private final FrameRequestGate requests = new FrameRequestGate();
    private final AtomicLong requested = new AtomicLong(), expiredCredits = new AtomicLong();
    private final DisplayManager displays;
    private final boolean profile;
    private volatile boolean active, surfaceReady, swapReady;
    private volatile long drawn, swapped, bootstrapAfter;
    private volatile long lateLimitNs = 15_000_000, creditAgeNs = 8_333_333, drainedCredits;
    private volatile long presentationPeriodNs = FrameCadence.TARGET_PERIOD_NS;
    private volatile long presentationSet, presentationFailed, presentationUnavailable;
    private volatile boolean presentationAvailable;
    private long drawingTicket;
    private boolean callbackPosted, closed;
    private float refreshHz = 60;
    private long busySkips, cadenceSkips, lateSkips, bootstrapSkips;
    private long reportStart, lastRequested, lastDrawn, lastSwapped;

    TvFramePacer(AndroidApplication app, GLSurfaceView view, boolean profile) {
        this.app = app; this.view = view; this.profile = profile;
        displays = (DisplayManager) app.getSystemService(android.content.Context.DISPLAY_SERVICE);
        view.getHolder().addCallback(this);
        surfaceReady = view.getHolder().getSurface().isValid();
        if (displays != null) displays.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
        refreshDisplay();
    }

    boolean active() { return active; }
    void prepareResume() {
        bootstrapAfter = drawn; swapReady = false; requests.reset(); cadence.reset();
    }
    void activate() {
        if (closed) return;
        active = true; refreshDisplay(); reportStart = System.nanoTime();
        lastRequested = requested.get(); lastDrawn = drawn; lastSwapped = swapped;
        postCallback();
    }
    void deactivate() {
        active = false; requests.reset(); swapReady = false; cadence.reset();
        if (callbackPosted) choreographer.removeFrameCallback(this);
        callbackPosted = false;
    }
    void close() {
        deactivate(); closed = true;
        view.getHolder().removeCallback(this);
        if (displays != null) displays.unregisterDisplayListener(this);
    }
    private void postCallback() {
        if (active && surfaceReady && !closed && !callbackPosted) {
            callbackPosted = true; choreographer.postFrameCallback(this);
        }
    }
    @Override public void doFrame(long frameTimeNanos) {
        callbackPosted = false;
        if (!active || !surfaceReady || closed) return;
        if (!cadence.due(frameTimeNanos)) { if (profile) cadenceSkips++; }
        else if (System.nanoTime() - frameTimeNanos > lateLimitNs) { if (profile) lateSkips++; }
        else {
            requests.offer(frameTimeNanos);
            if (!swapReady) { if (profile) bootstrapSkips++; }
            else if (requestCredit(System.nanoTime(), lateLimitNs) == FrameRequestGate.BUSY && profile) busySkips++;
        }
        if (profile) report(System.nanoTime());
        postCallback();
    }
    void beforeDraw() { drawn++; drawingTicket = requests.beginDraw(); }
    long presentationTimeNs() {
        return active && surfaceReady ? requests.presentationTimeNs(drawingTicket, presentationPeriodNs) : 0;
    }
    void presentationAvailable(boolean available) { presentationAvailable = available; }
    void presentationResult(boolean accepted) {
        if (!profile) return;
        if (accepted) presentationSet++; else presentationFailed++;
    }
    void presentationUnavailable() { if (profile) presentationUnavailable++; }
    void afterSwap() {
        swapped++;
        if (!requests.complete(drawingTicket)) return;
        if (drawn > bootstrapAfter) swapReady = true;
        if (requestCredit(System.nanoTime(), creditAgeNs) == FrameRequestGate.CLAIMED && profile) drainedCredits++;
    }
    private int requestCredit(long now, long maximumAge) {
        if (!active || !surfaceReady || !swapReady) return FrameRequestGate.EMPTY;
        int claim = requests.claim(now, maximumAge);
        if (claim == FrameRequestGate.EXPIRED && profile) expiredCredits.incrementAndGet();
        if (claim != FrameRequestGate.CLAIMED) return claim;
        if (!active || !surfaceReady) { requests.reset(); return FrameRequestGate.EMPTY; }
        if (profile) requested.incrementAndGet();
        view.requestRender(); return claim;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true; refreshDisplay(); applyFrameRateHint(); postCallback();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshDisplay(); applyFrameRateHint();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false; prepareResume();
        if (callbackPosted) choreographer.removeFrameCallback(this);
        callbackPosted = false;
    }
    @Override public void onDisplayAdded(int displayId) { }
    @Override public void onDisplayRemoved(int displayId) { }
    @Override public void onDisplayChanged(int displayId) {
        Display display = view.getDisplay();
        if (display != null && display.getDisplayId() == displayId) refreshDisplay();
    }
    private void refreshDisplay() {
        Display display = view.getDisplay();
        if (display == null) display = app.getWindowManager().getDefaultDisplay();
        float rate = display == null ? 60 : display.getRefreshRate();
        refreshHz = Float.isFinite(rate) && rate >= 20 && rate <= 1_000 ? rate : 60;
        cadence.configure(refreshHz);
        creditAgeNs = cadence.displayPeriodNs() / 2;
        lateLimitNs = cadence.displayPeriodNs() * 9 / 10;
        presentationPeriodNs = cadence.framePeriodNs();
        if (Build.VERSION.SDK_INT < 30 && display != null) preferCompatibleMode(display);
        if (surfaceReady) applyFrameRateHint();
    }
    private void applyFrameRateHint() {
        if (Build.VERSION.SDK_INT < 30) return;
        Surface surface = view.getHolder().getSurface();
        if (!surface.isValid()) return;
        try {
            if (Build.VERSION.SDK_INT >= 31)
                surface.setFrameRate(cadence.targetFps(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
            else surface.setFrameRate(cadence.targetFps(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            if (profile) android.util.Log.i("FramePacing", "Display frame-rate hint unavailable");
        }
    }
    private void preferCompatibleMode(Display display) {
        Display.Mode current = display.getMode(), best = null;
        int currentMultiple = Math.max(1, Math.round(current.getRefreshRate() / cadence.targetFps()));
        // Keep an already compatible 60/90/120 Hz output; only content production is fixed at 30.
        if (Math.abs(current.getRefreshRate() / currentMultiple - cadence.targetFps()) <= .2f) return;
        float bestRate = Float.MAX_VALUE;
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != current.getPhysicalWidth() || mode.getPhysicalHeight() != current.getPhysicalHeight()) continue;
            float rate = mode.getRefreshRate();
            int multiple = Math.max(1, Math.round(rate / cadence.targetFps()));
            if (Math.abs(rate / multiple - cadence.targetFps()) > .2f || rate >= bestRate) continue;
            best = mode; bestRate = rate;
        }
        if (best != null) {
            WindowManager.LayoutParams attributes = app.getWindow().getAttributes();
            if (attributes.preferredDisplayModeId != best.getModeId()) {
                attributes.preferredDisplayModeId = best.getModeId(); app.getWindow().setAttributes(attributes);
            }
        }
    }
    private void report(long now) {
        if (now - reportStart < 5_000_000_000L) return;
        long requestCount = requested.get(), drawCount = drawn, swapCount = swapped;
        android.util.Log.i("FramePacing", "backend=choreographer enabled=true targetFps=" + cadence.targetFps() + " refreshHz=" + refreshHz
            + " displayPeriodNs=" + cadence.displayPeriodNs() + " windowNs=" + (now - reportStart)
            + " targetPeriodNs=" + cadence.framePeriodNs()
            + " requested=" + (requestCount - lastRequested) + " drawn=" + (drawCount - lastDrawn)
            + " postSwap=" + (swapCount - lastSwapped) + " inFlight=" + requests.inFlight()
            + " skippedBusy=" + busySkips + " skippedCadence=" + cadenceSkips
            + " skippedLate=" + lateSkips + " skippedBootstrap=" + bootstrapSkips
            + " drainedCredits=" + drainedCredits + " expiredCredits=" + expiredCredits.get()
            + " presentationAvailable=" + presentationAvailable + " presentationSet=" + presentationSet
            + " presentationFailed=" + presentationFailed + " presentationUnavailable=" + presentationUnavailable);
        reportStart = now; lastRequested = requestCount; lastDrawn = drawCount; lastSwapped = swapCount;
    }
}
