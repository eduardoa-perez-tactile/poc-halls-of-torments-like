package com.couchcrypt.game;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Gdx;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Slow fake native calls exercise producer nonblocking behavior and stale request cleanup. */
class AsyncAudioTest {
    @Test void failedNativeSoundOperationsSilenceMixerAndAttemptEveryCleanupBeforeReleasingOwnership() throws Exception {
        Audio previous = Gdx.audio;
        AtomicInteger owners = new AtomicInteger();
        Gdx.audio = lifecycleBackend(owners);
        try {
            for (String broken : List.of("play", "stop", "setVolume")) {
                AtomicInteger logged = new AtomicInteger(), stopped = new AtomicInteger(), disposed = new AtomicInteger();
                AsyncVoiceMixer mixer = new AsyncVoiceMixer(6, new AsyncVoiceMixer.Loader() {
                    public Sound load(int cue) {
                        return (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                            String operation = method.getName();
                            if (operation.equals("stop") && args == null) {
                                stopped.incrementAndGet(); if (cue == 1) throw new IllegalStateException("Cleanup stop failed");
                            } else if (operation.equals("dispose")) {
                                disposed.incrementAndGet(); if (cue == 0) throw new IllegalStateException("Cleanup dispose failed");
                            } else if (operation.equals(broken) && cue == 0) throw new IllegalStateException("Native " + broken + " failed");
                            return operation.equals("play") ? 1L : null;
                        });
                    }
                    public void failed(RuntimeException failure) {
                        logged.incrementAndGet(); throw new IllegalStateException("Logging unavailable");
                    }
                });
                try {
                    assertTrue(mixer.awaitIdle(1_000));
                    assertTrue(mixer.start(0, 0, .4f, 1, 1));
                    if (!broken.equals("play")) {
                        assertTrue(mixer.awaitIdle(1_000));
                        if (broken.equals("stop")) mixer.stop(0); else mixer.volume(0, .2f);
                    }
                    assertTrue(mixer.awaitStopped(1_000));
                    assertFalse(mixer.start(0, 0, .4f, 1, 1));
                    assertEquals(1, logged.get());
                    assertEquals(SoundCue.values().length, stopped.get());
                    assertEquals(SoundCue.values().length, disposed.get());
                    assertEquals(0, owners.get(), "Cleanup failures must not retain backend ownership");
                } finally { mixer.dispose(); assertTrue(mixer.awaitStopped(1_000)); }
            }
        } finally { Gdx.audio = previous; }
    }

    @Test void failedNativeMusicUpdatesAndCleanupDoNotEscapeOrRetainBackendOwnership() throws Exception {
        Audio previous = Gdx.audio;
        AtomicInteger owners = new AtomicInteger();
        Gdx.audio = lifecycleBackend(owners);
        try {
            for (String broken : List.of("setVolume", "isPlaying", "pause", "dispose")) {
                AtomicInteger logged = new AtomicInteger(), disposed = new AtomicInteger(), volumes = new AtomicInteger();
                SceneMusic music = new SceneMusic(new SceneMusic.Loader() {
                    public boolean exists(String path) { return path.endsWith("/menu.ogg"); }
                    public Music load(String path) {
                        return (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class}, (proxy, method, args) -> {
                            String operation = method.getName();
                            if (operation.equals("dispose")) { disposed.incrementAndGet(); throw new IllegalStateException("Cleanup failed"); }
                            if (operation.equals("setVolume")) {
                                if (volumes.incrementAndGet() > 1 && broken.equals(operation)) throw new IllegalStateException("Volume failed");
                            } else if (operation.equals(broken)) throw new IllegalStateException("Native " + broken + " failed");
                            return operation.equals("isPlaying") ? true : null;
                        });
                    }
                    public void failed(String path, RuntimeException failure) {
                        logged.incrementAndGet(); throw new IllegalStateException("Logging unavailable");
                    }
                }, true);
                try {
                    music.update(SceneMusic.Scene.MENU, .5f, false, .1f);
                    if (!broken.equals("setVolume")) {
                        assertTrue(music.awaitIdle(1_000));
                        if (broken.equals("dispose")) music.dispose(); else music.pause();
                    }
                    assertTrue(music.awaitStopped(1_000));
                    assertEquals(1, logged.get());
                    assertEquals(1, disposed.get());
                    assertEquals(0, owners.get());
                } finally { music.dispose(); assertTrue(music.awaitStopped(1_000)); }
            }
        } finally { Gdx.audio = previous; }
    }

    private static Audio lifecycleBackend(AtomicInteger owners) {
        return (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[]{Audio.class, AudioWorkerLifecycle.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "workerStarted" -> owners.incrementAndGet();
                case "workerFinished" -> owners.decrementAndGet();
                default -> fail(method.getName());
            }
            return null;
        });
    }

    @Test void blockedSoundStartDoesNotBlockStopReplacementOrShutdownAndStaleIdIsStopped() throws Exception {
        SlowSounds nativeAudio = new SlowSounds();
        AsyncVoiceMixer mixer = new AsyncVoiceMixer(6, nativeAudio::load);
        try {
            assertTrue(mixer.awaitIdle(1_000));
            nativeAudio.blockNext = true;
            assertTrue(mixer.start(0, 0, .4f, 1, 1));
            assertTrue(nativeAudio.entered.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(200), () -> {
                for (int i = 0; i < 10_000; i++) {
                    mixer.start(0, 1, .3f, 1, 1); mixer.volume(0, .2f);
                }
                mixer.stopAll();
            });
            nativeAudio.release.countDown();
            assertTrue(mixer.awaitIdle(1_000));
            assertEquals(List.of(0), nativeAudio.played);
            assertTrue(nativeAudio.active.isEmpty(), "The in-flight native ID must be stopped after cancellation");
            for (int i = 0; i < 6; i++) assertTrue(mixer.start(i, i, .4f, 1, 1));
            assertTrue(mixer.awaitIdle(1_000));
            assertEquals(6, nativeAudio.active.size());
            assertTrue(mixer.start(0, 7, .3f, 1, 1));
            assertTrue(mixer.awaitIdle(1_000));
            assertEquals(6, nativeAudio.active.size());
            assertEquals(6, nativeAudio.maximum);
            mixer.volume(0, .15f);
            assertTrue(mixer.awaitIdle(1_000));
            assertEquals(.15f, nativeAudio.gains.get(nativeAudio.lastId), .0001f);
        } finally {
            nativeAudio.release.countDown(); mixer.dispose(); assertTrue(mixer.awaitStopped(1_000));
        }
        assertTrue(nativeAudio.active.isEmpty());
        assertEquals(SoundCue.values().length, nativeAudio.disposed);
        assertFalse(nativeAudio.calledByProducer);
    }

    @Test void delayedSoundRequestExpiresAndDisposeDuringLoadNeverPublishesOrPlaysIt() throws Exception {
        SlowSounds nativeAudio = new SlowSounds();
        AsyncVoiceMixer mixer = new AsyncVoiceMixer(6, nativeAudio::load);
        try {
            assertTrue(mixer.awaitIdle(1_000));
            nativeAudio.blockNext = true;
            mixer.start(0, 0, .4f, 1, 1);
            assertTrue(nativeAudio.entered.await(1, TimeUnit.SECONDS));
            mixer.start(1, 1, .4f, 1, .001f);
            Thread.sleep(10);
            nativeAudio.release.countDown();
            assertTrue(mixer.awaitIdle(1_000));
            assertEquals(List.of(0), nativeAudio.played);
        } finally { nativeAudio.release.countDown(); mixer.dispose(); assertTrue(mixer.awaitStopped(1_000)); }

        CountDownLatch loading = new CountDownLatch(1), finish = new CountDownLatch(1);
        SlowSounds delayed = new SlowSounds();
        AsyncVoiceMixer loadingMixer = new AsyncVoiceMixer(6, cue -> {
            loading.countDown(); await(finish); return delayed.load(cue);
        });
        try {
            assertTrue(loading.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(200), loadingMixer::dispose);
            assertFalse(loadingMixer.start(0, 0, 1, 1, 1));
        } finally { finish.countDown(); loadingMixer.dispose(); assertTrue(loadingMixer.awaitStopped(1_000)); }
        assertEquals(1, delayed.disposed);
        assertFalse(loadingMixer.available(0));
    }

    @Test void musicPreparationCoalescesSceneRequestsWithoutPlayingStaleStreamOrOverlappingDecoders() throws Exception {
        SlowMusic nativeAudio = new SlowMusic();
        SceneMusic music = new SceneMusic(nativeAudio, true);
        try {
            music.update(SceneMusic.Scene.MENU, 1, false, .1f);
            assertTrue(nativeAudio.entered.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(200), () -> {
                for (int i = 0; i < 10_000; i++) music.update(SceneMusic.Scene.FINAL_BOSS, .5f, false, .01f);
            });
            nativeAudio.release.countDown();
            assertTrue(music.awaitIdle(1_000));
            assertEquals(List.of("menu", "grave-king"), nativeAudio.opened);
            assertEquals(List.of("grave-king"), nativeAudio.played);
            assertEquals(1, nativeAudio.live);
            assertEquals(1, nativeAudio.maximum);
            music.pause(); assertTrue(music.awaitIdle(1_000));
            assertFalse(nativeAudio.playing);
            music.update(SceneMusic.Scene.FINAL_BOSS, .5f, false, 0);
            assertTrue(music.awaitIdle(1_000));
            assertTrue(nativeAudio.playing);
            assertEquals(2, nativeAudio.plays);
            assertEquals(2, nativeAudio.opened.size());
        } finally { nativeAudio.release.countDown(); music.dispose(); assertTrue(music.awaitStopped(1_000)); }
        assertEquals(0, nativeAudio.live);
        assertFalse(nativeAudio.calledByProducer);
    }

    @Test void pauseAndDisposalDuringNativePreparationReturnImmediatelyAndPreventPlayback() throws Exception {
        for (boolean shutdown : new boolean[]{false, true}) {
            SlowMusic nativeAudio = new SlowMusic(); SceneMusic music = new SceneMusic(nativeAudio, true);
            try {
                music.update(SceneMusic.Scene.MENU, 1, false, .1f);
                assertTrue(nativeAudio.entered.await(1, TimeUnit.SECONDS));
                assertTimeout(Duration.ofMillis(200), () -> { if (shutdown) music.dispose(); else music.pause(); });
                nativeAudio.release.countDown();
                if (shutdown) assertTrue(music.awaitStopped(1_000));
                else assertTrue(music.awaitIdle(1_000));
                assertTrue(nativeAudio.played.isEmpty());
                assertEquals(0, nativeAudio.live);
            } finally { nativeAudio.release.countDown(); music.dispose(); assertTrue(music.awaitStopped(1_000)); }
        }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Native fake was never released"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private static final class SlowSounds {
        final Thread producer = Thread.currentThread();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final List<Integer> played = new ArrayList<>();
        final Map<Long, Integer> active = new HashMap<>();
        final Map<Long, Float> gains = new HashMap<>();
        volatile boolean blockNext;
        boolean calledByProducer;
        long lastId; int maximum, disposed;
        Sound load(int cue) {
            calledByProducer |= Thread.currentThread() == producer;
            return (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                calledByProducer |= Thread.currentThread() == producer;
                switch (method.getName()) {
                    case "play" -> {
                        if (blockNext) { blockNext = false; entered.countDown(); await(release); }
                        long id = ++lastId; played.add(cue); active.put(id, cue); gains.put(id, (float) args[0]);
                        maximum = Math.max(maximum, active.size()); return id;
                    }
                    case "stop" -> {
                        if (args == null) active.entrySet().removeIf(e -> e.getValue() == cue);
                        else active.remove((long) args[0]);
                    }
                    case "setVolume" -> gains.put((long) args[0], (float) args[1]);
                    case "dispose" -> disposed++;
                    default -> fail(method.getName());
                }
                return null;
            });
        }
    }

    private static final class SlowMusic implements SceneMusic.Loader {
        final Thread producer = Thread.currentThread();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final List<String> opened = new ArrayList<>(), played = new ArrayList<>();
        int live, maximum, plays;
        boolean calledByProducer, playing;
        public boolean exists(String path) { calledByProducer |= Thread.currentThread() == producer; return true; }
        public Music load(String path) {
            calledByProducer |= Thread.currentThread() == producer;
            String name = path.substring("audio/music/".length(), path.length() - 4);
            opened.add(name); maximum = Math.max(maximum, ++live);
            if (opened.size() == 1) { entered.countDown(); await(release); }
            return (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class}, (proxy, method, args) -> {
                calledByProducer |= Thread.currentThread() == producer;
                switch (method.getName()) {
                    case "play" -> { played.add(name); playing = true; plays++; }
                    case "pause" -> playing = false;
                    case "isPlaying" -> { return playing; }
                    case "dispose" -> { live--; playing = false; }
                    case "setLooping", "setVolume", "setOnCompletionListener" -> { }
                    default -> fail(method.getName());
                }
                return null;
            });
        }
    }
}
