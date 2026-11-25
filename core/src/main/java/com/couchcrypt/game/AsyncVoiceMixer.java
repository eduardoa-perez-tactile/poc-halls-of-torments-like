package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import java.util.Arrays;

/** Six latest-state mailboxes: no command queue, futures, or native calls on the producer. */
final class AsyncVoiceMixer implements VoiceMixer {
    interface Loader {
        Sound load(int cue);
        default void failed(RuntimeException failure) { }
    }
    private final Object lock = new Object();
    private final int[] desiredCue;
    private final long[] generation, expires;
    private final float[] volume, pitch;
    private final Thread worker;
    private final Loader loader;
    private final AudioWorkerLifecycle lifecycle;
    private volatile long available;
    private long revision;
    private long appliedRevision = -1;
    private boolean closed;
    private boolean failureLogged;

    AsyncVoiceMixer(int slots) {
        this(slots, productionLoader());
    }

    static Loader productionLoader() {
        // Keep an old worker bound to its original backend during Activity recreation.
        var audio = Gdx.audio; var files = Gdx.files; var app = Gdx.app;
        SoundCue[] cues = SoundCue.values();
        return new Loader() {
            @Override public Sound load(int cue) {
                var file = files.internal(cues[cue].path);
                if (!file.exists()) return null;
                try { return audio.newSound(file); }
                catch (RuntimeException failure) {
                    app.error("Audio", "Cue unavailable: " + file.path(), failure); return null;
                }
            }
            @Override public void failed(RuntimeException failure) {
                app.error("Audio", "Optional sound mixer stopped after native audio failure", failure);
            }
        };
    }

    AsyncVoiceMixer(int slots, Loader loader) {
        this.loader = loader;
        desiredCue = new int[slots]; Arrays.fill(desiredCue, -1);
        generation = new long[slots]; expires = new long[slots];
        volume = new float[slots]; pitch = new float[slots];
        lifecycle = AudioWorkerLifecycle.register();
        worker = new Thread(this::run, "sound-mixer"); worker.setDaemon(true); worker.start();
    }

    @Override public boolean available(int cue) { return (available & 1L << cue) != 0; }
    @Override public boolean start(int slot, int cue, float gain, float rate, float seconds) {
        synchronized (lock) {
            if (closed || !available(cue)) return false;
            desiredCue[slot] = cue; volume[slot] = gain; pitch[slot] = rate;
            expires[slot] = System.nanoTime() + (long) (seconds * 1_000_000_000L);
            generation[slot]++; revision++; lock.notifyAll(); return true;
        }
    }
    @Override public void volume(int slot, float gain) {
        synchronized (lock) {
            if (closed || volume[slot] == gain) return;
            volume[slot] = gain; revision++; lock.notifyAll();
        }
    }
    @Override public void stop(int slot) {
        synchronized (lock) {
            if (desiredCue[slot] < 0) return;
            desiredCue[slot] = -1; generation[slot]++; revision++; lock.notifyAll();
        }
    }
    @Override public void stopAll() {
        synchronized (lock) {
            for (int i = 0; i < desiredCue.length; i++) { desiredCue[i] = -1; generation[i]++; }
            revision++; lock.notifyAll();
        }
    }
    @Override public void dispose() {
        synchronized (lock) { closed = true; available = 0; lock.notifyAll(); }
    }

    private boolean current(int slot, long expected) {
        synchronized (lock) { return !closed && generation[slot] == expected && expires[slot] > System.nanoTime(); }
    }

    private void run() {
        Sound[] sounds = new Sound[SoundCue.values().length];
        int count = desiredCue.length;
        int[] cues = new int[count], wanted = new int[count]; Arrays.fill(cues, -1);
        long[] ids = new long[count], applied = new long[count], versions = new long[count], deadlines = new long[count];
        float[] gains = new float[count], rates = new float[count], appliedGain = new float[count];
        long seen = -1;
        try {
            for (int i = 0; i < sounds.length; i++) {
                synchronized (lock) { if (closed) return; }
                sounds[i] = loader.load(i);
                synchronized (lock) { if (!closed && sounds[i] != null) available |= 1L << i; }
            }
            while (true) {
                synchronized (lock) {
                    while (!closed && revision == seen) lock.wait();
                    if (closed) return;
                    seen = revision;
                    System.arraycopy(desiredCue, 0, wanted, 0, count);
                    System.arraycopy(generation, 0, versions, 0, count);
                    System.arraycopy(expires, 0, deadlines, 0, count);
                    System.arraycopy(volume, 0, gains, 0, count);
                    System.arraycopy(pitch, 0, rates, 0, count);
                }
                for (int i = 0; i < count; i++) {
                    if (applied[i] != versions[i]) {
                        if (cues[i] >= 0) sounds[cues[i]].stop(ids[i]);
                        cues[i] = -1; applied[i] = versions[i];
                        if (wanted[i] < 0 || deadlines[i] <= System.nanoTime() || !current(i, versions[i])) continue;
                        long id = sounds[wanted[i]].play(gains[i], rates[i], 0);
                        if (id < 0) continue;
                        if (!current(i, versions[i])) { sounds[wanted[i]].stop(id); continue; }
                        cues[i] = wanted[i]; ids[i] = id; appliedGain[i] = gains[i];
                    } else if (cues[i] >= 0 && appliedGain[i] != gains[i]) {
                        sounds[cues[i]].setVolume(ids[i], gains[i]); appliedGain[i] = gains[i];
                    }
                }
                synchronized (lock) { appliedRevision = seen; lock.notifyAll(); }
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (RuntimeException failure) { reportFailure(failure); }
        finally {
            synchronized (lock) { closed = true; available = 0; lock.notifyAll(); }
            for (Sound sound : sounds) if (sound != null) {
                // A broken stop must not skip disposal of this or later optional cues.
                try { sound.stop(); } catch (RuntimeException failure) { reportFailure(failure); }
                try { sound.dispose(); } catch (RuntimeException failure) { reportFailure(failure); }
            }
            try { if (lifecycle != null) lifecycle.workerFinished(); }
            catch (RuntimeException failure) { reportFailure(failure); }
        }
    }

    private void reportFailure(RuntimeException failure) {
        if (failureLogged) return;
        failureLogged = true;
        try { loader.failed(failure); } catch (RuntimeException ignored) { /* Logging is optional too. */ }
    }

    // Test-only completion observation; production callers never join this worker.
    boolean awaitStopped(long milliseconds) throws InterruptedException { worker.join(milliseconds); return !worker.isAlive(); }
    boolean awaitIdle(long milliseconds) throws InterruptedException {
        long end = System.nanoTime() + milliseconds * 1_000_000L;
        synchronized (lock) {
            while (!closed && appliedRevision < revision) {
                long left = end - System.nanoTime(); if (left <= 0) return false;
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(lock, left);
            }
            return true;
        }
    }
}
