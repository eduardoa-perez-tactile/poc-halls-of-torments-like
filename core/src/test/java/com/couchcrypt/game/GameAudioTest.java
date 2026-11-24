package com.couchcrypt.game;

import com.badlogic.gdx.audio.Sound;
import java.lang.reflect.Proxy;
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

    private static final class Fixture implements AutoCloseable {
        final GameAudio audio;
        Fixture(Mixer mixer, ArenaWorld world, boolean enabled) {
            audio = new GameAudio(mixer.cues(), enabled); audio.reset(world);
        }
        @Override public void close() { audio.dispose(); }
    }

    private static final class Mixer {
        static final String[] NAMES = {"sword", "arrow", "hit", "pickup", "level", "boss", "victory", "defeat",
            "sword-hit", "bolt-hit", "critical", "dodge", "hurt", "death", "heal", "aura", "storm", "block",
            "wraith-dash", "brute-slam", "magic-cast"};
        final List<String> played = new ArrayList<>();
        final Map<Long, String> active = new HashMap<>();
        int maximumActive; long nextId;
        Sound[] cues() {
            Sound[] result = new Sound[NAMES.length];
            for (int i = 0; i < result.length; i++) {
                String name = NAMES[i];
                result[i] = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play" -> {
                            assertEquals(3, args.length);
                            assertTrue((float) args[0] > 0 && (float) args[0] <= .5f);
                            assertTrue((float) args[1] >= .96f && (float) args[1] <= 1.04f);
                            long id = ++nextId; active.put(id, name); played.add(name);
                            maximumActive = Math.max(maximumActive, active.size());
                            return id;
                        }
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
