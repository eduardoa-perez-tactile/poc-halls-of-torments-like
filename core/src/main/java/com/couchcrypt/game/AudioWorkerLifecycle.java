package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;

/** Android backend waits for owners off-thread before releasing the shared SoundPool. */
public interface AudioWorkerLifecycle {
    void workerStarted();
    void workerFinished();
    default int diagnosticWorkers() { return 0; }
    default int diagnosticMusicHandles() { return 0; }

    static AudioWorkerLifecycle register() {
        if (!(Gdx.audio instanceof AudioWorkerLifecycle lifecycle)) return null;
        lifecycle.workerStarted(); return lifecycle;
    }
}
