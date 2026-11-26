package com.couchcrypt.android;

import android.opengl.GLSurfaceView;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

/** Keeps LibGDX/platform EGL ownership and its lifecycle synchronization intact. */
final class PacedAndroidGraphics extends AndroidGraphics {
    private volatile TvFramePacer pacer;
    private final boolean profile;
    private EGLDisplay presentationDisplay;
    private EGLSurface presentationSurface;
    private boolean presentationSupported;
    private final Runnable afterSwap = () -> { if (pacer != null) pacer.afterSwap(); };

    PacedAndroidGraphics(AndroidApplication app, AndroidApplicationConfiguration config, boolean enabled, boolean profile) {
        super(app, config, config.resolutionStrategy);
        this.profile = profile;
        if (enabled) {
            pacer = new TvFramePacer(app, (GLSurfaceView) getView(), profile);
            super.setContinuousRendering(false);
        } else if (profile) android.util.Log.i("FramePacing", "backend=platform-continuous enabled=false");
    }
    void prepareResume() { if (pacer != null) pacer.prepareResume(); }
    void activate() { if (pacer != null) pacer.activate(); }
    void deactivate() { if (pacer != null) pacer.deactivate(); }
    void closePacer() { if (pacer != null) pacer.close(); }

    @Override public void requestRendering() {
        // A regular callback is already scheduled; input/runnables join its next frame.
        if (pacer == null || !pacer.active()) super.requestRendering();
    }
    @Override public void onDrawFrame(GL10 gl) {
        TvFramePacer current = pacer;
        if (current != null) current.beforeDraw();
        try {
            super.onDrawFrame(gl);
            if (current != null) applyPresentationTime(current);
        }
        finally {
            // The platform swaps after this callback, then services GL events next iteration.
            // Unlike returning from render(), this completion therefore includes swap waiting.
            if (current != null) ((GLSurfaceView) getView()).queueEvent(afterSwap);
        }
    }
    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config); cachePresentationSurface();
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height); cachePresentationSurface();
    }
    private void cachePresentationSurface() {
        TvFramePacer current = pacer;
        if (current == null) return;
        presentationSupported = false; presentationDisplay = null; presentationSurface = null;
        try {
            EGLDisplay display = EGL14.eglGetCurrentDisplay();
            EGLSurface surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            if (!display.equals(EGL14.EGL_NO_DISPLAY) && !surface.equals(EGL14.EGL_NO_SURFACE)) {
                String extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
                if (extensions != null && (" " + extensions + " ").contains(" EGL_ANDROID_presentation_time ")) {
                    presentationDisplay = display; presentationSurface = surface; presentationSupported = true;
                }
            }
        } catch (RuntimeException ignored) { /* Ordinary cadence remains a safe unsupported-driver fallback. */ }
        current.presentationAvailable(presentationSupported);
        if (profile) android.util.Log.i("FramePacing", "presentationExtension=EGL_ANDROID_presentation_time supported=" + presentationSupported);
    }
    private void applyPresentationTime(TvFramePacer current) {
        long target = current.presentationTimeNs();
        if (target == 0) return;
        if (!presentationSupported) { current.presentationUnavailable(); return; }
        boolean accepted;
        try { accepted = EGLExt.eglPresentationTimeANDROID(presentationDisplay, presentationSurface, target); }
        catch (RuntimeException ignored) { accepted = false; }
        current.presentationResult(accepted);
        if (!accepted) {
            presentationSupported = false; current.presentationAvailable(false);
            if (profile) android.util.Log.i("FramePacing", "presentationTimestampFallback=driver_rejected");
        }
    }
}
