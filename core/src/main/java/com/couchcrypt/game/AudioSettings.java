package com.couchcrypt.game;

import com.badlogic.gdx.Preferences;

/** Local TV mix preferences. The old Sound toggle migrates to master mute on first use. */
public final class AudioSettings {
    public enum Channel { MASTER, MUSIC, EFFECTS }
    private static final Channel[] CHANNELS = Channel.values();
    private static final String[] KEYS = {"audio.master", "audio.music", "audio.effects"};
    private static final float[] DEFAULTS = {1f, .65f, 1f};
    private final Preferences preferences;
    private final float[] levels = new float[KEYS.length];

    public AudioSettings(Preferences preferences) {
        this.preferences = preferences;
        for (int i = 0; i < levels.length; i++)
            levels[i] = bounded(preferences.getFloat(KEYS[i], DEFAULTS[i]), DEFAULTS[i]);
        if (!preferences.contains(KEYS[0]) && !preferences.getBoolean("sound", true)) levels[0] = 0;
    }

    public float level(Channel channel) { return levels[channel.ordinal()]; }
    public float musicVolume() { return levels[0] * levels[1]; }
    public float effectsVolume() { return levels[0] * levels[2]; }

    public void setLevel(Channel channel, float value) {
        levels[channel.ordinal()] = bounded(value, DEFAULTS[channel.ordinal()]);
        for (Channel entry : CHANNELS) preferences.putFloat(KEYS[entry.ordinal()], levels[entry.ordinal()]);
        preferences.flush();
    }

    /** Remote-friendly quarter steps, wrapping from full volume to mute. */
    public void cycle(Channel channel) {
        int next = (int) Math.floor(level(channel) * 4f + .001f) + 1;
        setLevel(channel, next > 4 ? 0 : next * .25f);
    }

    private static float bounded(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
}
