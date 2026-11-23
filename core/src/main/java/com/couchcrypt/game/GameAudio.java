package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

/** Event-owned short cues, with independent gates and six tracked voices for crowded co-op combat. */
public final class GameAudio implements Disposable {
    private static final int SWORD = 0, ARROW = 1, PICKUP = 3, LEVEL = 4, BOSS = 5, VICTORY = 6, DEFEAT = 7;
    private static final int SWORD_HIT = 8, BOLT_HIT = 9, CRITICAL = 10, DODGE = 11, HURT = 12,
        DEATH = 13, HEAL = 14, AURA = 15, STORM = 16, BLOCK = 17;
    private static final String[] NAMES = {"sword", "arrow", "hit", "pickup", "level", "boss", "victory", "defeat",
        "sword-hit", "bolt-hit", "critical", "dodge", "hurt", "death", "heal", "aura", "storm", "block"};
    // Keep in sync with the offline mono exports; pitch is included in each tracked voice's lifetime.
    private static final float[] DURATIONS = {.17f, .095f, .10f, .17f, .55f, .65f, .75f, .55f,
        .12f, .09f, .18f, .16f, .16f, .24f, .24f, .24f, .22f, .13f};
    private static final int MAX_VOICES = 6, MAX_STARTS_PER_UPDATE = 4;
    private final Sound[] sounds = new Sound[NAMES.length];
    private final float[] gates = new float[NAMES.length];
    private final long[] voiceIds = new long[MAX_VOICES];
    private final int[] voiceSounds = new int[MAX_VOICES], voicePriorities = new int[MAX_VOICES];
    private final float[] voiceRemaining = new float[MAX_VOICES];
    private boolean enabled;
    private int sword, arrows, deaths, pickups, hits, level, swordHits, boltHits, criticalHits,
        dodges, blocks, heals, auras, storms, starts, variation;
    private boolean boss;
    private ArenaWorld.Outcome outcome;

    public GameAudio() {
        enabled = Gdx.app.getPreferences("couch-crypt").getBoolean("sound", true);
        for (int i = 0; i < sounds.length; i++) {
            try { sounds[i] = Gdx.audio.newSound(Gdx.files.internal("audio/" + NAMES[i] + ".wav")); }
            catch (RuntimeException failure) { Gdx.app.error("Audio", "Cue unavailable: " + NAMES[i], failure); }
        }
    }

    /** Package-scoped injection keeps event routing verifiable without a device audio backend. */
    GameAudio(Sound[] loaded, boolean soundEnabled) {
        if (loaded.length != sounds.length) throw new IllegalArgumentException("Expected every cue slot");
        System.arraycopy(loaded, 0, sounds, 0, loaded.length);
        enabled = soundEnabled;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) stop();
        Gdx.app.getPreferences("couch-crypt").putBoolean("sound", value).flush();
    }

    public void reset(ArenaWorld world) {
        capture(world);
        for (int i = 0; i < gates.length; i++) gates[i] = 0;
        starts = variation = 0;
        stop();
    }

    public void update(ArenaWorld world, float delta) {
        float dt = Math.max(0, Math.min(.25f, delta));
        for (int i = 0; i < gates.length; i++) gates[i] = Math.max(0, gates[i] - dt);
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voiceRemaining[i] <= 0) continue;
            voiceRemaining[i] -= dt;
            if (voiceRemaining[i] <= 0) {
                sounds[voiceSounds[i]].stop(voiceIds[i]);
                voiceRemaining[i] = 0;
            }
        }
        starts = 0;
        // A ceremony gets its own space; consume the simultaneous combat counters without playing them later.
        if (world.outcome != outcome && world.outcome != ArenaWorld.Outcome.RUNNING) {
            stop();
            play(world.outcome == ArenaWorld.Outcome.VICTORY ? VICTORY : DEFEAT, .48f, 5, 0);
        } else if (world.bossSpawned && !boss) {
            stop(); play(BOSS, .48f, 5, 0);
        } else if (world.progression.level != level) {
            stop(); play(LEVEL, .42f, 5, 0);
        } else {
            if (world.heroHits != hits) play(HURT, .46f, 4, .13f);
            if (world.dodges != dodges) play(DODGE, .32f, 3, .12f);
            // Actual weapon contacts get the frame budget before deaths, pickups and background abilities.
            if (world.swordHits != swordHits) play(SWORD_HIT, .44f, 3, .06f);
            if (world.boltHits != boltHits) play(BOLT_HIT, .36f, 3, .045f);
            if (world.criticalHits != criticalHits) play(CRITICAL, .35f, 3, .08f);
            if (world.blockedHits != blocks) play(BLOCK, .33f, 3, .13f);
            if (world.healsCollected != heals) play(HEAL, .30f, 3, .14f);
            if (world.enemyDeaths != deaths) play(DEATH, .20f, 1, .09f);
            if (world.stormCasts != storms) play(STORM, .28f, 2, .15f);
            if (world.auraPulses != auras) play(AURA, .18f, 1, .20f);
            // Both weapons have independent gates, so a sword never consumes the Ranger's firing gate.
            if (world.swordSwings != sword) play(SWORD, .25f, 1, .075f);
            if (world.arrowsFired != arrows) play(ARROW, .24f, 1, .07f);
            if (world.pickupsCollected - pickups > world.healsCollected - heals) play(PICKUP, .20f, 0, .09f);
        }
        capture(world);
    }

    private void capture(ArenaWorld world) {
        sword = world.swordSwings; arrows = world.arrowsFired; deaths = world.enemyDeaths;
        pickups = world.pickupsCollected; hits = world.heroHits; level = world.progression.level;
        swordHits = world.swordHits; boltHits = world.boltHits; criticalHits = world.criticalHits;
        dodges = world.dodges; blocks = world.blockedHits; heals = world.healsCollected;
        auras = world.auraPulses; storms = world.stormCasts;
        boss = world.bossSpawned; outcome = world.outcome;
    }

    private void play(int cue, float volume, int priority, float gate) {
        if (!enabled || sounds[cue] == null || gates[cue] > 0 || starts >= MAX_STARTS_PER_UPDATE) return;
        int slot = -1;
        for (int i = 0; i < MAX_VOICES; i++) if (voiceRemaining[i] <= 0) { slot = i; break; }
        if (slot < 0) {
            // Replace the least important remaining voice, then the one closest to ending.
            for (int i = 0; i < MAX_VOICES; i++) {
                if (voicePriorities[i] > priority) continue;
                if (slot < 0 || voicePriorities[i] < voicePriorities[slot]
                    || (voicePriorities[i] == voicePriorities[slot] && voiceRemaining[i] < voiceRemaining[slot])) slot = i;
            }
            if (slot < 0) return;
            sounds[voiceSounds[slot]].stop(voiceIds[slot]);
            voiceRemaining[slot] = 0;
        }
        // Cosmetic variation never consumes the simulation's random stream. Ceremonies stay in tune.
        float pitch = priority >= 5 ? 1f : .96f + variation * .01f;
        variation = (variation + 5) % 9;
        long id = sounds[cue].play(volume, pitch, 0f);
        gates[cue] = gate;
        if (id < 0) return;
        voiceIds[slot] = id; voiceSounds[slot] = cue; voicePriorities[slot] = priority;
        voiceRemaining[slot] = DURATIONS[cue] / pitch + .03f;
        starts++;
    }

    public void stop() {
        for (Sound sound : sounds) if (sound != null) sound.stop();
        for (int i = 0; i < MAX_VOICES; i++) voiceRemaining[i] = 0;
    }
    @Override public void dispose() { stop(); for (Sound sound : sounds) if (sound != null) sound.dispose(); }
}
