package com.couchcrypt.android;

import android.content.Context;
import android.media.MediaPlayer;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidFileHandle;
import com.badlogic.gdx.backends.android.DefaultAndroidAudio;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.couchcrypt.game.AudioWorkerLifecycle;
import java.io.FileDescriptor;
import java.io.IOException;

/** Application audio workers own native calls, including lifecycle, instead of the GL thread. */
final class WorkerOwnedAndroidAudio extends DefaultAndroidAudio implements AudioWorkerLifecycle {
    private final Object ownership = new Object();
    private int workers;
    private boolean disposeRequested, releasing, musicOpening;
    private final Application application;
    private OwnedMusic activeMusic;

    WorkerOwnedAndroidAudio(Context context, AndroidApplicationConfiguration config) {
        super(context, config); application = (Application) context;
    }

    // CryptLobby.pause/resume already publishes suspension to both audio owners.
    @Override public void pause() { }
    @Override public void resume() { }

    /** LibGDX 1.14.2's private music list removes the backend instead of the disposed wrapper.
     * Never enter that list: this game has exactly one worker-owned stream. */
    @Override public Music newMusic(FileHandle file) {
        MediaPlayer player = createOwnedPlayer();
        try {
            if (file.type() == Files.FileType.Internal) {
                try (var descriptor = ((AndroidFileHandle) file).getAssetFileDescriptor()) {
                    player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                }
            } else player.setDataSource(file.file().getPath());
            return prepareOwnedMusic(player);
        } catch (IOException | RuntimeException failure) {
            releaseFailedPlayer(player);
            throw new GdxRuntimeException("Cannot prepare optional music: " + file, failure);
        }
    }
    @Override public Music newMusic(FileDescriptor descriptor) {
        MediaPlayer player = createOwnedPlayer();
        try {
            player.setDataSource(descriptor);
            return prepareOwnedMusic(player);
        } catch (IOException | RuntimeException failure) {
            releaseFailedPlayer(player);
            throw new GdxRuntimeException("Cannot prepare optional music descriptor", failure);
        }
    }
    private MediaPlayer createOwnedPlayer() {
        synchronized (ownership) {
            if (disposeRequested || activeMusic != null || musicOpening) throw new IllegalStateException("Music owner unavailable");
            musicOpening = true;
        }
        try { return createMediaPlayer(); }
        catch (RuntimeException failure) {
            synchronized (ownership) { musicOpening = false; }
            throw failure;
        }
    }
    private Music prepareOwnedMusic(MediaPlayer player) throws IOException {
        player.prepare();
        OwnedMusic music = new OwnedMusic(player);
        synchronized (ownership) {
            if (disposeRequested || activeMusic != null) throw new IllegalStateException("Music owner replaced during preparation");
            activeMusic = music; musicOpening = false;
        }
        return music;
    }
    private void musicDisposed(OwnedMusic music) {
        synchronized (ownership) { if (activeMusic == music) activeMusic = null; }
    }
    @Override public int diagnosticWorkers() { synchronized (ownership) { return workers; } }
    @Override public int diagnosticMusicHandles() {
        synchronized (ownership) { return activeMusic == null && !musicOpening ? 0 : 1; }
    }
    private void releaseFailedPlayer(MediaPlayer player) {
        try { player.release(); } catch (RuntimeException ignored) { /* Preserve preparation's first failure. */ }
        finally { synchronized (ownership) { musicOpening = false; } }
    }

    @Override public void workerStarted() {
        synchronized (ownership) {
            if (disposeRequested) throw new IllegalStateException("Audio backend already closing");
            workers++;
        }
    }
    @Override public void workerFinished() {
        boolean release;
        synchronized (ownership) {
            workers--;
            release = disposeRequested && workers == 0 && !releasing;
            if (release) releasing = true;
        }
        if (release) releaseBackend();
    }
    @Override public void dispose() {
        boolean release;
        synchronized (ownership) {
            disposeRequested = true;
            release = workers == 0 && !releasing;
            if (release) releasing = true;
        }
        if (release) {
            Thread cleanup = new Thread(this::releaseBackend, "audio-backend-release");
            cleanup.setDaemon(true); cleanup.start();
        }
    }
    private void releaseBackend() {
        OwnedMusic remaining;
        synchronized (ownership) { remaining = activeMusic; activeMusic = null; }
        if (remaining != null) {
            try { remaining.dispose(); }
            catch (RuntimeException failure) { android.util.Log.e("CouchCryptAudio", "Optional music release failed", failure); }
        }
        try { super.dispose(); }
        catch (RuntimeException failure) {
            android.util.Log.e("CouchCryptAudio", "Optional audio backend release failed", failure);
        }
    }

    /** No registry/list: release clears the backend's sole reference even if the platform throws. */
    private final class OwnedMusic implements Music, MediaPlayer.OnCompletionListener {
        private final MediaPlayer player;
        private volatile OnCompletionListener completion;
        private volatile boolean disposed;
        private float volume = 1;

        OwnedMusic(MediaPlayer player) { this.player = player; player.setOnCompletionListener(this); }
        @Override public void play() { if (!disposed) player.start(); }
        @Override public void pause() { if (!disposed && player.isPlaying()) player.pause(); }
        @Override public void stop() { if (!disposed) { pause(); player.seekTo(0); } }
        @Override public boolean isPlaying() { return !disposed && player.isPlaying(); }
        @Override public void setLooping(boolean looping) { if (!disposed) player.setLooping(looping); }
        @Override public boolean isLooping() { return !disposed && player.isLooping(); }
        @Override public void setVolume(float gain) {
            volume = gain; if (!disposed) player.setVolume(gain, gain);
        }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float pan, float gain) {
            volume = gain;
            if (!disposed) player.setVolume(gain * (pan > 0 ? 1 - pan : 1), gain * (pan < 0 ? 1 + pan : 1));
        }
        @Override public void setPosition(float position) { if (!disposed) player.seekTo((int) (position * 1000)); }
        @Override public float getPosition() { return disposed ? 0 : player.getCurrentPosition() / 1000f; }
        @Override public void setOnCompletionListener(OnCompletionListener listener) { completion = listener; }
        @Override public void onCompletion(MediaPlayer completed) {
            if (!disposed && completion != null) application.postRunnable(() -> {
                OnCompletionListener listener = completion;
                if (!disposed && listener != null) listener.onCompletion(this);
            });
        }
        @Override public void dispose() {
            if (disposed) return;
            disposed = true; completion = null;
            try {
                try { player.setOnCompletionListener(null); }
                finally { player.release(); }
            } finally { musicDisposed(this); }
        }
    }
}
