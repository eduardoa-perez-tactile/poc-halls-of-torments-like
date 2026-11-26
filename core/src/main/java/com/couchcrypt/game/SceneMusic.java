package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.Disposable;

/** Optional, streamed score. A transition releases its old decoder before opening the next one. */
public final class SceneMusic implements Disposable, Music.OnCompletionListener {
    public enum Scene { MENU, ARENA, SOVEREIGN, FINAL_BOSS, VICTORY, DEFEAT }

    private enum Track {
        MENU("menu", true), ARENA("arena", true), SOVEREIGN("sovereign", true),
        FINAL_BOSS("grave-king", true), VICTORY("victory", false), DEFEAT("defeat", false);
        final String path;
        final boolean loop;
        Track(String name, boolean loop) { this.path = "audio/music/" + name + ".ogg"; this.loop = loop; }
    }

    interface Loader {
        boolean exists(String path);
        Music load(String path);
        default void failed(String path, RuntimeException failure) { }
    }

    private static final Track[] TRACKS = Track.values();
    private static final float FADE_SECONDS = .6f;
    private final Loader loader;
    private final Worker async;
    private Worker startGuard;
    // 0 unknown, 1 present, -1 missing/failed. Never poll the filesystem in the frame loop.
    private final byte[] availability = new byte[TRACKS.length];
    private volatile Music current;
    private volatile Track currentTrack;
    private float envelope;
    private float appliedGain = Float.NaN;
    private boolean resumePending, disposed;
    private volatile boolean completed;

    public SceneMusic() {
        this(productionLoader(), Gdx.audio instanceof AudioWorkerLifecycle);
    }

    private static Loader productionLoader() {
        var audio = Gdx.audio; var files = Gdx.files; var app = Gdx.app;
        return new Loader() {
            @Override public boolean exists(String path) { return files.internal(path).exists(); }
            @Override public Music load(String path) { return audio.newMusic(files.internal(path)); }
            @Override public void failed(String path, RuntimeException failure) {
                app.error("Audio", "Music unavailable: " + path, failure);
            }
        };
    }

    SceneMusic(Loader loader) { this(loader, false); }
    SceneMusic(Loader loader, boolean asynchronous) {
        this.loader = loader;
        async = asynchronous ? new Worker(loader) : null;
    }

    /** Call once per rendered frame, including menus. Suspended frames freeze transitions. */
    public void update(Scene scene, float volume, boolean suspended, float delta) {
        if (async != null) { async.update(scene, volume, suspended, delta); return; }
        if (disposed) return;
        float gain = Float.isFinite(volume) ? Math.max(0, Math.min(1, volume)) : 0;
        if (suspended || gain == 0) { pause(); return; }
        Track wanted = resolve(scene);
        float step = (Float.isFinite(delta) ? Math.max(0, Math.min(.25f, delta)) : 0) / FADE_SECONDS;
        if (current != null && currentTrack != wanted) {
            // A paused/completed old scene has no audible tail to fade.
            if (resumePending || completed) release();
            else {
                envelope = Math.max(0, envelope - step);
                setGain(gain * envelope);
                if (envelope > 0) return;
                release();
            }
        }
        if (current == null) start(scene, wanted);
        if (current == null || completed) return;
        if (resumePending) {
            current.play();
            resumePending = false;
        }
        envelope = Math.min(1, envelope + step);
        setGain(gain * envelope);
    }

    private void setGain(float gain) {
        if (appliedGain == gain) return;
        current.setVolume(gain); appliedGain = gain;
    }

    /** Immediate platform/manual suspension; never marks an already finished one-shot for replay. */
    public void pause() {
        if (async != null) { async.pause(); return; }
        if (current == null || completed || resumePending) return;
        resumePending = current.isPlaying();
        if (resumePending) current.pause();
    }

    private Track resolve(Scene scene) {
        Track track = TRACKS[scene.ordinal()];
        while (track != null) {
            if (available(track)) return track;
            track = fallback(track);
        }
        return null;
    }

    private boolean available(Track track) {
        int index = track.ordinal();
        if (availability[index] == 0) {
            try { availability[index] = (byte) (loader.exists(track.path) ? 1 : -1); }
            catch (RuntimeException failure) { unavailable(track, failure); }
        }
        return availability[index] > 0;
    }

    private static Track fallback(Track track) {
        return switch (track) {
            case SOVEREIGN -> Track.ARENA;
            case FINAL_BOSS -> Track.SOVEREIGN;
            case VICTORY, DEFEAT -> Track.MENU;
            default -> null;
        };
    }

    private void start(Scene scene, Track wanted) {
        while (wanted != null) {
            Music candidate = null;
            try {
                candidate = loader.load(wanted.path);
                if (candidate == null) throw new IllegalStateException("Music loader returned no stream");
                // Preparation can outlive a pause, scene replacement or disposal.
                if (startGuard != null && !startGuard.mayStart(scene)) { candidate.dispose(); return; }
                candidate.setLooping(wanted.loop);
                candidate.setVolume(0);
                candidate.setOnCompletionListener(this);
                current = candidate; currentTrack = wanted;
                appliedGain = 0;
                completed = resumePending = false; envelope = 0;
                candidate.play();
                if (startGuard != null && !startGuard.mayStart(scene)) release();
                return;
            } catch (RuntimeException failure) {
                // Include failures raised while preparing/starting the native decoder.
                if (candidate != null) candidate.dispose();
                current = null; currentTrack = null;
                unavailable(wanted, failure);
                wanted = resolve(scene);
            }
        }
    }

    private void unavailable(Track track, RuntimeException failure) {
        if (availability[track.ordinal()] != -1) {
            availability[track.ordinal()] = -1;
            try { loader.failed(track.path, failure); }
            catch (RuntimeException ignored) { /* Diagnostics must not break optional audio fallback. */ }
        }
    }

    @Override public void onCompletion(Music music) {
        Track track = currentTrack;
        if (music == current && track != null && !track.loop) completed = true;
    }

    private void release() {
        Music old = current;
        current = null; currentTrack = null;
        completed = resumePending = false; envelope = 0;
        appliedGain = Float.NaN;
        if (old != null) old.dispose();
    }

    @Override public void dispose() {
        if (async != null) { async.dispose(); return; }
        if (disposed) return;
        disposed = true;
        release();
    }

    /** One replaceable request and one native owner. No frame loop command allocation/backlog. */
    private static final class Worker {
        private final Object lock = new Object();
        private final SceneMusic controller;
        private final Thread thread;
        private final AudioWorkerLifecycle lifecycle;
        private Scene scene = Scene.MENU;
        private float volume, delta;
        private boolean suspended = true, pauseRequested, closed;
        private boolean failureLogged;
        private long revision;
        private long appliedRevision;

        Worker(Loader loader) {
            controller = new SceneMusic(loader, false); controller.startGuard = this;
            lifecycle = AudioWorkerLifecycle.register();
            thread = new Thread(this::run, "scene-music"); thread.setDaemon(true); thread.start();
        }
        void update(Scene next, float gain, boolean paused, float dt) {
            synchronized (lock) {
                if (closed) return;
                gain = Float.isFinite(gain) ? Math.max(0, Math.min(1, gain)) : 0;
                dt = Float.isFinite(dt) ? Math.max(0, Math.min(.25f, dt)) : 0;
                if (next != scene || paused || gain == 0) delta = 0;
                scene = next; volume = gain; suspended = paused;
                if (paused || gain == 0) pauseRequested = true;
                else delta = Math.min(.25f, delta + dt);
                revision++; lock.notifyAll();
            }
        }
        void pause() {
            synchronized (lock) {
                if (closed) return;
                suspended = true; pauseRequested = true; delta = 0; revision++; lock.notifyAll();
            }
        }
        boolean mayStart(Scene expected) {
            synchronized (lock) { return !closed && !suspended && volume > 0 && scene == expected; }
        }
        void dispose() { synchronized (lock) { closed = true; lock.notifyAll(); } }
        private void run() {
            long seen = 0;
            try {
                while (true) {
                    Scene wanted; float gain, step; boolean paused, mustPause;
                    synchronized (lock) {
                        while (!closed && revision == seen) lock.wait();
                        if (closed) return;
                        seen = revision; wanted = scene; gain = volume; step = delta; paused = suspended;
                        mustPause = pauseRequested; pauseRequested = false; delta = 0;
                    }
                    if (mustPause) controller.pause();
                    controller.update(wanted, gain, paused, step);
                    synchronized (lock) { appliedRevision = seen; lock.notifyAll(); }
                }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (RuntimeException failure) { reportFailure(failure); }
            finally {
                synchronized (lock) { closed = true; lock.notifyAll(); }
                try { controller.dispose(); } catch (RuntimeException failure) { reportFailure(failure); }
                try { if (lifecycle != null) lifecycle.workerFinished(); }
                catch (RuntimeException failure) { reportFailure(failure); }
            }
        }
        private void reportFailure(RuntimeException failure) {
            if (failureLogged) return;
            failureLogged = true;
            Scene failedScene;
            synchronized (lock) { failedScene = scene; }
            try { controller.loader.failed(TRACKS[failedScene.ordinal()].path, failure); }
            catch (RuntimeException ignored) { /* A logging failure must not escape this worker. */ }
        }
    }

    // Test-only bounded wait; render/lifecycle entry points never wait for native work.
    boolean awaitStopped(long milliseconds) throws InterruptedException {
        if (async == null) return true;
        async.thread.join(milliseconds); return !async.thread.isAlive();
    }
    boolean awaitIdle(long milliseconds) throws InterruptedException {
        if (async == null) return true;
        long end = System.nanoTime() + milliseconds * 1_000_000L;
        synchronized (async.lock) {
            while (!async.closed && async.appliedRevision < async.revision) {
                long left = end - System.nanoTime(); if (left <= 0) return false;
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(async.lock, left);
            }
            return true;
        }
    }
}
