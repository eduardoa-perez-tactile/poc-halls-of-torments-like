package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

/** Short, rate-limited mono cues; no music streaming, render-thread generation, or tick allocations. */
public final class GameAudio implements Disposable {
    private final Sound[] sounds = new Sound[8];
    private boolean enabled;
    private int sword, arrows, deaths, pickups, hits, level;
    private boolean boss;
    private ArenaWorld.Outcome outcome;
    private float attackGate, hitGate, pickupGate;

    public GameAudio() {
        enabled = Gdx.app.getPreferences("couch-crypt").getBoolean("sound", true);
        String[] names = {"sword", "arrow", "hit", "pickup", "level", "boss", "victory", "defeat"};
        for (int i = 0; i < sounds.length; i++) {
            try { sounds[i] = Gdx.audio.newSound(Gdx.files.internal("audio/" + names[i] + ".wav")); }
            catch (RuntimeException failure) { Gdx.app.error("Audio", "Cue unavailable: " + names[i], failure); }
        }
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) stop();
        Gdx.app.getPreferences("couch-crypt").putBoolean("sound", value).flush();
    }

    public void reset(ArenaWorld world) {
        sword = world.swordSwings; arrows = world.arrowsFired; deaths = world.enemyDeaths;
        pickups = world.pickupsCollected; hits = world.heroHits; level = world.progression.level;
        boss = world.bossSpawned; outcome = world.outcome;
        attackGate = hitGate = pickupGate = 0;
        stop();
    }

    public void update(ArenaWorld world, float delta) {
        attackGate = Math.max(0, attackGate - delta);
        hitGate = Math.max(0, hitGate - delta);
        pickupGate = Math.max(0, pickupGate - delta);
        if (world.swordSwings != sword && attackGate <= 0) { play(0, .32f); attackGate = .12f; }
        else if (world.arrowsFired != arrows && attackGate <= 0) { play(1, .28f); attackGate = .12f; }
        if ((world.enemyDeaths != deaths || world.heroHits != hits) && hitGate <= 0) { play(2, .22f); hitGate = .12f; }
        if (world.pickupsCollected != pickups && pickupGate <= 0) { play(3, .25f); pickupGate = .1f; }
        if (world.progression.level != level) play(4, .42f);
        if (world.bossSpawned && !boss) play(5, .5f);
        if (world.outcome != outcome && world.outcome != ArenaWorld.Outcome.RUNNING)
            play(world.outcome == ArenaWorld.Outcome.VICTORY ? 6 : 7, .48f);
        sword = world.swordSwings; arrows = world.arrowsFired; deaths = world.enemyDeaths;
        pickups = world.pickupsCollected; hits = world.heroHits; level = world.progression.level;
        boss = world.bossSpawned; outcome = world.outcome;
    }

    private void play(int index, float volume) { if (enabled && sounds[index] != null) sounds[index].play(volume); }
    public void stop() { for (Sound sound : sounds) if (sound != null) sound.stop(); }
    @Override public void dispose() { for (Sound sound : sounds) if (sound != null) sound.dispose(); }
}
