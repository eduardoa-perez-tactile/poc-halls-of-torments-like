package com.couchcrypt.game;

import com.badlogic.gdx.audio.Sound;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real event-routing checks with a fake native mixer; no GL context or device audio is required. */
class GameAudioTest {
    @Test void eachBossGetsOneArrivalCueEvenWhenSovereignSurvivesIntoFinale() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.sovereignSpawned = true;
            f.audio.update(world, .016f);
            f.audio.update(world, .25f);
            assertEquals(List.of("boss"), mixer.played);
            world.bossSpawned = true;
            f.audio.update(world, .016f);
            f.audio.update(world, .25f);
            assertEquals(List.of("boss", "boss"), mixer.played);
            f.audio.reset(world); f.audio.update(world, .016f);
            assertEquals(2, mixer.played.size(), "Restoring a live encounter must not replay arrival cues");
        }
    }

    @Test void bothWeaponsAreAudibleAndGatedEventsNeverReplayLater() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.swordSwings++; world.arrowsFired++;
            f.audio.update(world, .016f);
            assertEquals(List.of("sword", "arrow"), mixer.played);
            world.swordSwings++; world.arrowsFired++;
            f.audio.update(world, .01f);
            assertEquals(2, mixer.played.size(), "Each weapon has its own gate");
            f.audio.update(world, .1f);
            assertEquals(2, mixer.played.size(), "Dropped repeats must not become stale later sounds");
            world.arrowsFired++; f.audio.update(world, .016f);
            assertEquals("arrow", mixer.played.getLast());
        }
    }

    @Test void weaponContactsAreNotStarvedBySimultaneousCrowdAndAbilityEvents() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.swordHits++; world.boltHits++; world.enemyDeaths++;
            world.stormCasts++; world.auraPulses++; world.pickupsCollected++;
            world.swordSwings++; world.arrowsFired++;
            f.audio.update(world, .016f);
            assertEquals(4, mixer.played.size());
            assertEquals(List.of("sword-hit", "bolt-hit"), mixer.played.subList(0, 2));
            assertFalse(mixer.played.contains("hurt"), "Enemy death must never sound like player damage");
            assertEquals(1, mixer.played.stream().filter("death"::equals).count());
        }
    }

    @Test void committedEnemyAttacksHaveDistinctGatedCuesAndDoNotReplayAfterReset() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.bruteSlams++; world.wraithDashes++; world.hostileShotsFired += 5;
            f.audio.update(world, .016f);
            assertEquals(List.of("brute-slam", "wraith-dash", "magic-cast"), mixer.played);
            world.bruteSlams++; world.wraithDashes++; world.hostileShotsFired++;
            f.audio.update(world, .01f);
            assertEquals(3, mixer.played.size());
            f.audio.update(world, .2f);
            assertEquals(3, mixer.played.size(), "Gated crowded casts must not replay later");
            f.audio.reset(world); f.audio.update(world, .2f);
            assertEquals(3, mixer.played.size());
        }
    }

    @Test void trackedVoiceCapIncludesReplacementsAndPlayerDamageCanPreemptCrowdAudio() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.swordSwings++; world.arrowsFired++; world.pickupsCollected++;
            f.audio.update(world, 0);
            assertEquals(3, mixer.active.size());
            world.stormCasts++; world.auraPulses++; world.enemyDeaths++; world.blockedHits++;
            f.audio.update(world, 0);
            assertEquals(6, mixer.active.size());
            assertEquals(6, mixer.maximumActive);
            world.heroHits++; f.audio.update(world, 0);
            assertEquals("hurt", mixer.played.getLast());
            assertEquals(6, mixer.active.size());
            assertEquals(6, mixer.maximumActive, "A replacement stops its old voice before starting a new one");
            f.audio.update(world, .25f);
            f.audio.update(world, .25f);
            assertTrue(mixer.active.isEmpty());
        }
    }

    @Test void resetMuteAndCeremoniesConsumeCombatEventsWithoutReplayingThem() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.swordSwings++; f.audio.update(world, 0);
            world.swordHits++; world.enemyDeaths++; world.progression.level++;
            f.audio.update(world, .016f);
            assertEquals(List.of("sword", "level"), mixer.played);
            assertEquals(1, mixer.active.size());
            f.audio.reset(world); f.audio.update(world, .2f);
            assertTrue(mixer.active.isEmpty());
            assertEquals(2, mixer.played.size());
            world.outcome = ArenaWorld.Outcome.VICTORY; f.audio.update(world, .016f);
            assertEquals("victory", mixer.played.getLast());
            f.audio.stop(); assertTrue(mixer.active.isEmpty());
        }
        Mixer muted = new Mixer();
        try (Fixture f = new Fixture(muted, world, false)) {
            world.heroHits++; world.dodges++; world.swordHits++; world.criticalHits++;
            f.audio.update(world, .016f);
            assertTrue(muted.played.isEmpty());
        }
    }

    @Test void volumeChangesAffectLiveVoicesAndMuteDoesNotReplayConsumedEvents() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.swordSwings++; f.audio.update(world, 0);
            long voice = mixer.nextId;
            f.audio.setVolume(.5f);
            assertEquals(SoundCue.SWORD.volume * .5f, mixer.volumes.get(voice), .0001f);
            f.audio.setVolume(0);
            assertTrue(mixer.active.isEmpty());
            world.heroHits++; f.audio.update(world, .25f);
            f.audio.setVolume(1); f.audio.update(world, .25f);
            assertEquals(List.of("sword"), mixer.played);
            world.heroHits++; f.audio.update(world, .016f);
            assertEquals("hurt", mixer.played.getLast());
        }
    }

    @Test void menuEventsUseTheSameBoundedMixerAndAdvanceOutsideCombat() {
        Mixer mixer = new Mixer();
        GameAudio audio = new GameAudio(mixer.cues(), true);
        try {
            for (int i = 0; i < 12; i++) audio.play(SoundCue.UI_FOCUS);
            assertEquals(List.of("ui-focus"), mixer.played);
            audio.beginFrame(.2f);
            assertTrue(mixer.active.isEmpty());
            audio.play(SoundCue.UI_FOCUS);
            audio.play(SoundCue.UI_CONFIRM);
            audio.play(SoundCue.UI_BACK);
            assertEquals(4, mixer.played.size());
            audio.setSuspended(true);
            assertTrue(mixer.active.isEmpty());
            audio.beginFrame(.2f); audio.play(SoundCue.UI_CONFIRM);
            assertEquals(4, mixer.played.size());
            audio.setSuspended(false); audio.play(SoundCue.UI_CONFIRM);
            assertEquals(5, mixer.played.size());
        } finally { audio.dispose(); }
    }

    @Test void lootAndRealDownReviveTransitionsHaveDedicatedCuesWithoutFreshJoinNoise() {
        Mixer mixer = new Mixer(); ArenaWorld world = new ArenaWorld();
        world.activatePlayer(0);
        try (Fixture f = new Fixture(mixer, world, true)) {
            world.activatePlayer(1); f.audio.update(world, .016f);
            assertTrue(mixer.played.isEmpty());
            world.players[0].alive = false; f.audio.update(world, .016f);
            assertEquals(List.of("player-down"), mixer.played);
            world.players[0].alive = true; f.audio.update(world, .016f);
            assertEquals("revive", mixer.played.getLast());
            world.equipment.grantReward(); f.audio.update(world, .016f);
            assertEquals("loot", mixer.played.getLast());
            f.audio.reset(world); f.audio.update(world, .25f);
            assertEquals(3, mixer.played.size());
        }
    }

    @Test void missingOptionalCuesRemainSafeWhileOtherEffectsPlay() {
        Mixer mixer = new Mixer(); Sound[] cues = mixer.cues();
        cues[SoundCue.UI_FOCUS.ordinal()] = null;
        GameAudio audio = new GameAudio(cues, true);
        try {
            audio.play(SoundCue.UI_FOCUS); audio.play(SoundCue.SWORD);
            audio.setVolume(.5f); audio.beginFrame(.25f);
            assertEquals(List.of("sword"), mixer.played);
        } finally { audio.dispose(); }
        assertTrue(mixer.active.isEmpty());
    }

    @Test void openingPauseStopsCombatWithoutCuttingOffItsRemoteFeedback() {
        Mixer mixer = new Mixer();
        GameAudio audio = new GameAudio(mixer.cues(), true);
        try {
            audio.play(SoundCue.SWORD); audio.play(SoundCue.UI_BACK);
            audio.stopCombat();
            assertEquals(List.of("ui-back"), new ArrayList<>(mixer.active.values()));
            audio.beginFrame(.25f); audio.beginFrame(.05f);
            assertTrue(mixer.active.isEmpty());
            audio.play(SoundCue.UI_CONFIRM); audio.stopCombat();
            assertEquals(List.of("ui-confirm"), new ArrayList<>(mixer.active.values()));
        } finally { audio.dispose(); }
    }

    @Test void shippedWavDurationsFitTrackedLifetimesWithoutTruncatingAudio() throws Exception {
        Path audio = Path.of("../assets");
        int shipped = 0;
        for (SoundCue cue : SoundCue.values()) {
            Path file = audio.resolve(cue.path);
            if (!Files.exists(file)) continue;
            try (var stream = AudioSystem.getAudioInputStream(file.toFile())) {
                AudioFormat format = stream.getFormat();
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
                assertEquals(1, format.getChannels(), file.toString());
                double seconds = stream.getFrameLength() / format.getFrameRate();
                assertEquals(cue.seconds, seconds, .001, "Update cue lifetime when replacing " + file);
                shipped++;
            }
        }
        assertEquals(SoundCue.values().length, shipped, "Every production cue must ship with its audio file");
    }

    private static final class Fixture implements AutoCloseable {
        final GameAudio audio;
        Fixture(Mixer mixer, ArenaWorld world, boolean enabled) {
            audio = new GameAudio(mixer.cues(), enabled); audio.reset(world);
        }
        @Override public void close() { audio.dispose(); }
    }

    private static final class Mixer {
        final List<String> played = new ArrayList<>();
        final Map<Long, String> active = new HashMap<>();
        final Map<Long, Float> volumes = new HashMap<>();
        int maximumActive; long nextId;
        Sound[] cues() {
            Sound[] result = new Sound[SoundCue.values().length];
            for (int i = 0; i < result.length; i++) {
                String path = SoundCue.values()[i].path;
                String name = path.substring(6, path.length() - 4);
                result[i] = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play" -> {
                            assertEquals(3, args.length);
                            assertTrue((float) args[0] > 0 && (float) args[0] <= .5f);
                            assertTrue((float) args[1] >= .96f && (float) args[1] <= 1.04f);
                            long id = ++nextId; active.put(id, name); volumes.put(id, (float) args[0]); played.add(name);
                            maximumActive = Math.max(maximumActive, active.size());
                            return id;
                        }
                        case "setVolume" -> volumes.put((long) args[0], (float) args[1]);
                        case "stop" -> {
                            if (args == null) active.entrySet().removeIf(entry -> entry.getValue().equals(name));
                            else active.remove((long) args[0]);
                        }
                        default -> { }
                    }
                    return null;
                });
            }
            return result;
        }
    }
}
