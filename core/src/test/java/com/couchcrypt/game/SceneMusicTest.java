package com.couchcrypt.game;

import com.badlogic.gdx.audio.Music;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneMusicTest {
    @Test void settledMusicGainDoesNotRepeatNativeVolumeCallsUntilTheMixChanges() {
        Streams streams = new Streams("menu"); SceneMusic music = new SceneMusic(streams);
        try {
            settle(music, SceneMusic.Scene.MENU, .8f);
            Stream menu = streams.last(); int before = menu.volumeWrites;
            for (int i = 0; i < 120; i++) music.update(SceneMusic.Scene.MENU, .8f, false, .016f);
            assertEquals(before, menu.volumeWrites);
            music.update(SceneMusic.Scene.MENU, .4f, false, .016f);
            assertEquals(before + 1, menu.volumeWrites);
            assertEquals(.4f, menu.volume, .001f);
        } finally { music.dispose(); }
    }
    @Test void transitionsFadeAndReleaseDecoderBeforeOpeningNext() {
        Streams streams = new Streams("menu", "arena");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.MENU, .8f);
        Stream menu = streams.last();
        assertEquals(.8f, menu.volume, .001f);
        music.update(SceneMusic.Scene.ARENA, .8f, false, .1f);
        assertTrue(menu.volume > 0 && menu.volume < .8f);
        assertEquals(1, streams.opened.size());
        settle(music, SceneMusic.Scene.ARENA, .8f);
        assertTrue(menu.disposed);
        assertEquals("arena", streams.last().name);
        assertEquals(.8f, streams.last().volume, .001f);
        assertEquals(1, streams.maximumLive);
        assertEquals(1, streams.last().plays);
        settle(music, SceneMusic.Scene.ARENA, .4f);
        assertEquals(.4f, streams.last().volume, .001f);
        assertEquals(2, streams.opened.size());
        music.dispose(); music.dispose();
        assertEquals(0, streams.live);
        assertEquals(1, streams.last().disposals);
    }

    @Test void suspensionAndMutePauseWithoutRestartingPositionOrAdvancingFade() {
        Streams streams = new Streams("arena");
        SceneMusic music = new SceneMusic(streams);
        music.update(SceneMusic.Scene.ARENA, .6f, false, .1f);
        Stream arena = streams.last();
        float volumeBeforePause = arena.volume;
        arena.position = 24;
        music.pause();
        music.update(SceneMusic.Scene.ARENA, .6f, true, .25f);
        assertFalse(arena.playing);
        assertEquals(1, arena.pauses);
        assertEquals(volumeBeforePause, arena.volume);
        music.update(SceneMusic.Scene.ARENA, .6f, false, 0);
        assertTrue(arena.playing);
        assertEquals(24, arena.position);
        assertEquals(2, arena.plays);
        assertEquals(volumeBeforePause, arena.volume);
        music.update(SceneMusic.Scene.ARENA, 0, false, .25f);
        assertFalse(arena.playing);
        music.update(SceneMusic.Scene.ARENA, .6f, false, .1f);
        assertTrue(arena.playing);
        assertEquals(3, arena.plays);
        assertEquals(1, streams.opened.size());
        music.dispose();
    }

    @Test void completedOutcomeStaysEndedThroughBackgroundingAndUnmute() {
        Streams streams = new Streams("victory", "menu");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.VICTORY, 1);
        Stream victory = streams.last();
        assertFalse(victory.loop);
        victory.finish();
        music.pause();
        music.update(SceneMusic.Scene.VICTORY, 0, false, .25f);
        music.update(SceneMusic.Scene.VICTORY, 1, true, .25f);
        settle(music, SceneMusic.Scene.VICTORY, 1);
        assertEquals(1, victory.plays);
        assertFalse(victory.playing);
        settle(music, SceneMusic.Scene.MENU, 1);
        assertTrue(streams.last().loop);
        settle(music, SceneMusic.Scene.VICTORY, 1);
        assertEquals("victory", streams.last().name);
        assertTrue(streams.last().playing);
        assertEquals(3, streams.opened.size());
        music.dispose();
    }

    @Test void completedStreamWithoutDeliveredCallbackIsNotRestartedOnResume() {
        Streams streams = new Streams("defeat");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.DEFEAT, 1);
        Stream defeat = streams.last();
        defeat.playing = false;
        music.pause();
        music.update(SceneMusic.Scene.DEFEAT, 1, false, .1f);
        assertEquals(1, defeat.plays);
        music.dispose();
    }

    @Test void missingBossTracksKeepArenaStreamAndMissingOutcomesKeepLoopingMenu() {
        Streams streams = new Streams("menu", "arena");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.ARENA, .6f);
        Stream arena = streams.last();
        settle(music, SceneMusic.Scene.SOVEREIGN, .6f);
        settle(music, SceneMusic.Scene.FINAL_BOSS, .6f);
        assertSame(arena, streams.last());
        assertEquals(1, arena.plays);
        assertEquals(1, streams.checks.get("sovereign"));
        assertEquals(1, streams.checks.get("grave-king"));
        settle(music, SceneMusic.Scene.MENU, .6f);
        Stream menu = streams.last();
        settle(music, SceneMusic.Scene.VICTORY, .6f);
        settle(music, SceneMusic.Scene.DEFEAT, .6f);
        assertSame(menu, streams.last());
        assertTrue(menu.loop);
        assertEquals(1, menu.plays);
        music.dispose();
    }

    @Test void absentFilesAreCheckedOnceAndDoNotCreateStreams() {
        Streams streams = new Streams();
        SceneMusic music = new SceneMusic(streams);
        for (SceneMusic.Scene scene : SceneMusic.Scene.values()) settle(music, scene, 1);
        for (SceneMusic.Scene scene : SceneMusic.Scene.values()) settle(music, scene, 1);
        assertEquals(6, streams.checks.size());
        assertTrue(streams.checks.values().stream().allMatch(count -> count == 1));
        assertTrue(streams.opened.isEmpty());
        assertTrue(streams.errors.isEmpty());
        music.dispose();
    }

    @Test void brokenNativeDecoderFallsBackOnceAndNeverOverlapsStreams() {
        Streams streams = new Streams("arena", "sovereign", "grave-king");
        streams.broken.add("sovereign");
        streams.broken.add("grave-king");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.FINAL_BOSS, 1);
        assertEquals("arena", streams.last().name);
        assertEquals(List.of("grave-king", "sovereign"), streams.errors);
        settle(music, SceneMusic.Scene.ARENA, 1);
        settle(music, SceneMusic.Scene.FINAL_BOSS, 1);
        assertEquals(1, streams.last().plays);
        assertEquals(2, streams.errors.size());
        assertEquals(1, streams.maximumLive);
        music.dispose();
    }

    @Test void sceneChangedWhileMutedDoesNotResumeOldTrackAndDisposedSystemStaysSilent() {
        Streams streams = new Streams("menu", "arena");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.MENU, 1);
        Stream menu = streams.last();
        music.update(SceneMusic.Scene.ARENA, 0, false, .25f);
        assertEquals(1, streams.opened.size());
        music.update(SceneMusic.Scene.ARENA, 1, false, .1f);
        assertTrue(menu.disposed);
        assertEquals(1, menu.plays);
        assertEquals("arena", streams.last().name);
        music.dispose();
        music.update(SceneMusic.Scene.MENU, 1, false, .25f);
        assertEquals(2, streams.opened.size());
        assertEquals(0, streams.live);
    }

    @Test void lateCompletionFromReleasedTrackCannotEndItsReplacement() {
        Streams streams = new Streams("victory", "arena");
        SceneMusic music = new SceneMusic(streams);
        settle(music, SceneMusic.Scene.VICTORY, 1);
        Stream victory = streams.last();
        settle(music, SceneMusic.Scene.ARENA, 1);
        Stream arena = streams.last();
        victory.completion.onCompletion(victory.music);
        music.pause();
        music.update(SceneMusic.Scene.ARENA, 1, false, .1f);
        assertEquals(2, arena.plays);
        assertTrue(arena.playing);
        music.dispose();
        arena.completion.onCompletion(arena.music);
    }

    private static void settle(SceneMusic music, SceneMusic.Scene scene, float volume) {
        for (int i = 0; i < 8; i++) music.update(scene, volume, false, .25f);
    }

    private static final class Streams implements SceneMusic.Loader {
        final Set<String> available = new HashSet<>(), broken = new HashSet<>();
        final Map<String, Integer> checks = new HashMap<>();
        final List<Stream> opened = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        int live, maximumLive;
        Streams(String... names) { available.addAll(List.of(names)); }
        @Override public boolean exists(String path) {
            String name = name(path);
            checks.merge(name, 1, Integer::sum);
            return available.contains(name);
        }
        @Override public Music load(String path) {
            String name = name(path);
            if (broken.contains(name)) throw new IllegalStateException("Bad decoder");
            Stream stream = new Stream(this, name); opened.add(stream);
            maximumLive = Math.max(maximumLive, ++live);
            return stream.music;
        }
        @Override public void failed(String path, RuntimeException failure) { errors.add(name(path)); }
        Stream last() { return opened.getLast(); }
        private static String name(String path) {
            assertTrue(path.startsWith("audio/music/") && path.endsWith(".ogg"));
            return path.substring("audio/music/".length(), path.length() - ".ogg".length());
        }
    }

    private static final class Stream {
        final Music music;
        final String name;
        boolean playing, loop, disposed;
        float volume, position;
        int plays, pauses, disposals, volumeWrites;
        Music.OnCompletionListener completion;
        Stream(Streams owner, String name) {
            this.name = name;
            music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[]{Music.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "play" -> { assertFalse(disposed); playing = true; plays++; }
                    case "pause" -> { playing = false; pauses++; }
                    case "isPlaying" -> { return playing; }
                    case "setLooping" -> loop = (boolean) args[0];
                    case "setVolume" -> {
                        volumeWrites++;
                        volume = (float) args[0];
                        assertTrue(volume >= 0 && volume <= 1);
                    }
                    case "setOnCompletionListener" -> completion = (Music.OnCompletionListener) args[0];
                    case "dispose" -> { assertFalse(disposed); disposed = true; playing = false; disposals++; owner.live--; }
                    default -> fail("Unexpected Music operation: " + method.getName());
                }
                return null;
            });
        }
        void finish() { playing = false; completion.onCompletion(music); }
    }
}
