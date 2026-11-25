package com.couchcrypt.game;

import com.badlogic.gdx.audio.Sound;

/** Slot ownership keeps asynchronous native IDs private to the audio worker. */
interface VoiceMixer {
    boolean available(int cue);
    boolean start(int slot, int cue, float volume, float pitch, float seconds);
    void volume(int slot, float volume);
    void stop(int slot);
    void stopAll();
    void dispose();

    /** Deterministic injected backend for routing tests, never used by production. */
    final class Direct implements VoiceMixer {
        private final Sound[] sounds;
        private final int[] cues;
        private final long[] ids;
        Direct(Sound[] sounds, int slots) {
            this.sounds = sounds.clone(); cues = new int[slots]; ids = new long[slots];
            java.util.Arrays.fill(cues, -1);
        }
        public boolean available(int cue) { return sounds[cue] != null; }
        public boolean start(int slot, int cue, float volume, float pitch, float seconds) {
            long id = sounds[cue].play(volume, pitch, 0);
            if (id < 0) return false;
            cues[slot] = cue; ids[slot] = id; return true;
        }
        public void volume(int slot, float volume) {
            if (cues[slot] >= 0) sounds[cues[slot]].setVolume(ids[slot], volume);
        }
        public void stop(int slot) {
            if (cues[slot] >= 0) sounds[cues[slot]].stop(ids[slot]);
            cues[slot] = -1;
        }
        public void stopAll() { for (int i = 0; i < cues.length; i++) stop(i); }
        public void dispose() { stopAll(); for (Sound sound : sounds) if (sound != null) sound.dispose(); }
    }
}
