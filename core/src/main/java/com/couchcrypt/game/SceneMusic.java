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
    // 0 unknown, 1 present, -1 missing/failed. Never poll the filesystem in the frame loop.
    private final byte[] availability = new byte[TRACKS.length];
    private Music current;
    private Track currentTrack;
    private float envelope;
    private boolean resumePending, disposed;
    private volatile boolean completed;

    public SceneMusic() {
        this(new Loader() {
            @Override public boolean exists(String path) { return Gdx.files.internal(path).exists(); }
            @Override public Music load(String path) { return Gdx.audio.newMusic(Gdx.files.internal(path)); }
            @Override public void failed(String path, RuntimeException failure) {
                Gdx.app.error("Audio", "Music unavailable: " + path, failure);
            }
        });
    }

    SceneMusic(Loader loader) { this.loader = loader; }

    /** Call once per rendered frame, including menus. Suspended frames freeze transitions. */
    public void update(Scene scene, float volume, boolean suspended, float delta) {
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
                current.setVolume(gain * envelope);
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
        current.setVolume(gain * envelope);
    }

    /** Immediate platform/manual suspension; never marks an already finished one-shot for replay. */
    public void pause() {
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
                candidate.setLooping(wanted.loop);
                candidate.setVolume(0);
                candidate.setOnCompletionListener(this);
                current = candidate; currentTrack = wanted;
                completed = resumePending = false; envelope = 0;
                candidate.play();
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
            loader.failed(track.path, failure);
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
        if (old != null) old.dispose();
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        release();
    }
}
