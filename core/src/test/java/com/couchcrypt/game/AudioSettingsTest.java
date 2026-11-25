package com.couchcrypt.game;

import com.badlogic.gdx.Preferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioSettingsTest {
    @Test void legacyMuteSurvivesMigrationAndSavedMixSurvivesRestart() {
        Map<String, Object> values = new HashMap<>(); values.put("sound", false);
        Preferences preferences = preferences(values);
        AudioSettings settings = new AudioSettings(preferences);
        assertEquals(0, settings.effectsVolume()); assertEquals(0, settings.musicVolume());
        settings.setLevel(AudioSettings.Channel.MASTER, .5f);
        settings.setLevel(AudioSettings.Channel.EFFECTS, .25f);
        settings.setLevel(AudioSettings.Channel.MUSIC, .75f);
        AudioSettings restored = new AudioSettings(preferences);
        assertEquals(.125f, restored.effectsVolume()); assertEquals(.375f, restored.musicVolume());
        restored.setLevel(AudioSettings.Channel.MUSIC, 0);
        assertEquals(.125f, restored.effectsVolume()); assertEquals(0, restored.musicVolume());
    }

    @Test void eachChannelCyclesThroughMuteAndQuarterStepsIndependently() {
        AudioSettings settings = new AudioSettings(preferences(new HashMap<>()));
        for (float expected : new float[]{0, .25f, .5f, .75f, 1f}) {
            settings.cycle(AudioSettings.Channel.MASTER);
            assertEquals(expected, settings.level(AudioSettings.Channel.MASTER));
            assertEquals(1, settings.level(AudioSettings.Channel.EFFECTS));
        }
    }

    @Test void damagedPreferencesCannotProduceInvalidMixerVolumes() {
        Map<String, Object> values = new HashMap<>();
        values.put("audio.master", Float.NaN); values.put("audio.music", -3f); values.put("audio.effects", 9f);
        AudioSettings settings = new AudioSettings(preferences(values));
        assertEquals(1, settings.effectsVolume()); assertEquals(0, settings.musicVolume());
        settings.setLevel(AudioSettings.Channel.MASTER, Float.POSITIVE_INFINITY);
        assertEquals(1, settings.effectsVolume());
    }

    private static Preferences preferences(Map<String, Object> values) {
        return (Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(), new Class<?>[]{Preferences.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "contains" -> values.containsKey((String) args[0]);
                case "getFloat", "getBoolean" -> values.getOrDefault((String) args[0], args[1]);
                case "putFloat" -> { values.put((String) args[0], args[1]); yield proxy; }
                case "flush" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
