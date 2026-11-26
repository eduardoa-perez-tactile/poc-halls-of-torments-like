package com.couchcrypt.android;

import android.opengl.GLSurfaceView;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import javax.microedition.khronos.opengles.GL10;

/** Keeps LibGDX/platform EGL ownership and its lifecycle synchronization intact. */
final class PacedAndroidGraphics extends AndroidGraphics {
    private volatile TvFramePacer pacer;
    private final Runnable afterSwap = () -> { if (pacer != null) pacer.afterSwap(); };

    PacedAndroidGraphics(AndroidApplication app, AndroidApplicationConfiguration config, boolean enabled, boolean profile) {
        super(app, config, config.resolutionStrategy);
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
        try { super.onDrawFrame(gl); }
        finally {
            // The platform swaps after this callback, then services GL events next iteration.
            // Unlike returning from render(), this completion therefore includes swap waiting.
            if (current != null) ((GLSurfaceView) getView()).queueEvent(afterSwap);
        }
    }
}
