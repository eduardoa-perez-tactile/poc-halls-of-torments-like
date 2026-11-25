package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import static com.couchcrypt.game.SoundCue.*;

/** Event-owned short cues, with independent gates and six tracked voices for crowded co-op combat. */
public final class GameAudio implements Disposable {
    private static final SoundCue[] CUES = SoundCue.values();
    private static final int MAX_VOICES = 6, MAX_STARTS_PER_UPDATE = 4;
    private final VoiceMixer mixer;
    private final float[] gates = new float[CUES.length];
    private final int[] voiceSounds = new int[MAX_VOICES], voicePriorities = new int[MAX_VOICES];
    private final float[] voiceRemaining = new float[MAX_VOICES], voiceVolumes = new float[MAX_VOICES];
    private final boolean[] activePlayers = new boolean[2], alivePlayers = new boolean[2];
    private float volume = 1f;
    private boolean suspended;
    private int sword, arrows, deaths, pickups, hits, level, swordHits, boltHits, criticalHits,
        dodges, blocks, heals, auras, storms, wraithDashes, bruteSlams, hostileShots, starts, variation;
    private int loot, relics, bellLunges, bellTolls;
    private boolean boss, sovereign, bell;
    private ArenaWorld.Outcome outcome;

    public GameAudio() {
        if (Gdx.audio instanceof AudioWorkerLifecycle) mixer = new AsyncVoiceMixer(MAX_VOICES);
        else {
            // Desktop OpenAL owns its stream updates on the render thread.
            var loader = AsyncVoiceMixer.productionLoader();
            Sound[] loaded = new Sound[CUES.length];
            for (int i = 0; i < loaded.length; i++) loaded[i] = loader.load(i);
            mixer = new VoiceMixer.Direct(loaded, MAX_VOICES);
        }
    }

    /** Package-scoped injection keeps event routing verifiable without a device audio backend. */
    GameAudio(Sound[] loaded, boolean soundEnabled) {
        if (loaded.length != CUES.length) throw new IllegalArgumentException("Expected every cue slot");
        mixer = new VoiceMixer.Direct(loaded, MAX_VOICES);
        volume = soundEnabled ? 1f : 0f;
    }

    public void setVolume(float value) {
        float next = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        if (volume == next) return;
        volume = next;
        if (volume == 0) { stop(); return; }
        for (int i = 0; i < MAX_VOICES; i++) if (voiceRemaining[i] > 0)
            mixer.volume(i, voiceVolumes[i] * volume);
    }

    public void setSuspended(boolean value) {
        suspended = value;
        if (value) stop();
    }

    public void reset(ArenaWorld world) {
        capture(world);
        for (int i = 0; i < gates.length; i++) gates[i] = 0;
        starts = variation = 0;
        stop();
    }

    /** Advance once per rendered frame, including menus and results, before submitting events. */
    public void beginFrame(float delta) {
        float dt = Float.isFinite(delta) ? Math.max(0, Math.min(.25f, delta)) : 0;
        for (int i = 0; i < gates.length; i++) gates[i] = Math.max(0, gates[i] - dt);
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voiceRemaining[i] <= 0) continue;
            voiceRemaining[i] -= dt;
            if (voiceRemaining[i] <= 0) {
                mixer.stop(i);
                voiceRemaining[i] = 0;
            }
        }
        starts = 0;
    }

    public void update(ArenaWorld world, float delta) { beginFrame(delta); update(world); }

    public void update(ArenaWorld world) {
        // A ceremony gets its own space; consume the simultaneous combat counters without playing them later.
        if (world.outcome != outcome && world.outcome != ArenaWorld.Outcome.RUNNING) {
            stop();
            play(world.outcome == ArenaWorld.Outcome.VICTORY ? VICTORY : DEFEAT);
        } else if ((world.bossSpawned && !boss) || (world.sovereignSpawned && !sovereign) || (world.bellSpawned && !bell)) {
            stop(); play(BOSS);
        } else if (world.progression.level != level) {
            stop(); play(LEVEL);
        } else if (world.equipment.rewardsEarned() != loot) {
            stop(); play(LOOT);
        } else {
            for (int i = 0; i < 2; i++) if (world.players[i].active && activePlayers[i]) {
                if (alivePlayers[i] && !world.players[i].alive) play(PLAYER_DOWN);
                else if (!alivePlayers[i] && world.players[i].alive) play(REVIVE);
            }
            if (world.heroHits != hits) play(HURT);
            if (world.dodges != dodges) play(DODGE);
            if (world.bellTolls != bellTolls) play(BELL_TOLL);
            if (world.bellLunges != bellLunges) play(WRAITH_DASH);
            // Actual weapon contacts get the frame budget before deaths, pickups and background abilities.
            if (world.swordHits != swordHits) play(SWORD_HIT);
            if (world.boltHits != boltHits) play(BOLT_HIT);
            if (world.criticalHits != criticalHits) play(CRITICAL);
            if (world.blockedHits != blocks) play(BLOCK);
            if (world.bruteSlams != bruteSlams) play(BRUTE_SLAM);
            if (world.wraithDashes != wraithDashes) play(WRAITH_DASH);
            if (world.hostileShotsFired != hostileShots) play(MAGIC_CAST);
            if (world.healsCollected != heals) play(HEAL);
            if (world.enemyDeaths != deaths) play(DEATH);
            if (world.stormCasts != storms) play(STORM);
            if (world.auraPulses != auras) play(AURA);
            // Both weapons have independent gates, so a sword never consumes the Ranger's firing gate.
            if (world.swordSwings != sword) play(SWORD);
            if (world.arrowsFired != arrows) play(ARROW);
            if (world.pickupsCollected - pickups > world.healsCollected - heals) play(PICKUP);
            if (world.skills.relicsCollected != relics)
                play(world.skills.lastRelicKind == SkillExpansion.RelicKind.STASIS ? AURA : PICKUP);
        }
        capture(world);
    }

    private void capture(ArenaWorld world) {
        relics = world.skills.relicsCollected;
        loot = world.equipment.rewardsEarned();
        for (int i = 0; i < 2; i++) {
            activePlayers[i] = world.players[i].active;
            alivePlayers[i] = world.players[i].alive;
        }
        sword = world.swordSwings; arrows = world.arrowsFired; deaths = world.enemyDeaths;
        pickups = world.pickupsCollected; hits = world.heroHits; level = world.progression.level;
        swordHits = world.swordHits; boltHits = world.boltHits; criticalHits = world.criticalHits;
        dodges = world.dodges; blocks = world.blockedHits; heals = world.healsCollected;
        auras = world.auraPulses; storms = world.stormCasts;
        wraithDashes = world.wraithDashes; bruteSlams = world.bruteSlams; hostileShots = world.hostileShotsFired;
        bellLunges = world.bellLunges; bellTolls = world.bellTolls;
        boss = world.bossSpawned; sovereign = world.sovereignSpawned; bell = world.bellSpawned; outcome = world.outcome;
    }

    /** Presentation events use the same bounded mixer as combat. Missing optional files are silent. */
    public void play(SoundCue effect) {
        int cue = effect.ordinal(), priority = effect.priority;
        if (suspended || volume <= 0 || !mixer.available(cue) || gates[cue] > 0 || starts >= MAX_STARTS_PER_UPDATE) return;
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
            mixer.stop(slot);
            voiceRemaining[slot] = 0;
        }
        // Cosmetic variation never consumes the simulation's random stream. Ceremonies stay in tune.
        float pitch = priority >= 5 || isUi(effect) ? 1f : .96f + variation * .01f;
        variation = (variation + 5) % 9;
        float lifetime = effect.seconds / pitch + .03f;
        boolean accepted = mixer.start(slot, cue, effect.volume * volume, pitch, lifetime);
        gates[cue] = effect.gate;
        if (!accepted) return;
        voiceSounds[slot] = cue; voicePriorities[slot] = priority;
        voiceVolumes[slot] = effect.volume;
        voiceRemaining[slot] = lifetime;
        starts++;
    }

    private static boolean isUi(SoundCue cue) {
        return cue == UI_FOCUS || cue == UI_CONFIRM || cue == UI_BACK;
    }

    /** Pausing combat leaves the remote action's short feedback audible. */
    public void stopCombat() {
        for (int i = 0; i < MAX_VOICES; i++) if (voiceRemaining[i] > 0 && !isUi(CUES[voiceSounds[i]])) {
            mixer.stop(i);
            voiceRemaining[i] = 0;
        }
    }

    public void stop() {
        mixer.stopAll();
        for (int i = 0; i < MAX_VOICES; i++) voiceRemaining[i] = 0;
    }
    @Override public void dispose() { stop(); mixer.dispose(); }
}
