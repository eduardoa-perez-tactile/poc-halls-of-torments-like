package com.couchcrypt.game;

/** File contract and mixer metadata for short, resident effects. Music belongs to SceneMusic. */
public enum SoundCue {
    SWORD("sword", .28f, .25f, 1, .075f),
    ARROW("arrow", .22f, .24f, 1, .07f),
    HIT("hit", .25f, .36f, 3, .06f), // Retained legacy asset; contacts use the two weapon cues below.
    PICKUP("pickup", .22f, .20f, 0, .09f),
    LEVEL("level", 1.2f, .42f, 5, 0),
    BOSS("boss", 1.4f, .48f, 5, 0),
    VICTORY("victory", 1.4f, .48f, 5, 0),
    DEFEAT("defeat", 1.25f, .48f, 5, 0),
    SWORD_HIT("sword-hit", .28f, .44f, 3, .06f),
    BOLT_HIT("bolt-hit", .23f, .36f, 3, .045f),
    CRITICAL("critical", .36f, .35f, 3, .08f),
    DODGE("dodge", .28f, .32f, 3, .12f),
    HURT("hurt", .32f, .46f, 4, .13f),
    DEATH("death", .38f, .20f, 1, .09f),
    HEAL("heal", .6f, .30f, 3, .14f),
    AURA("aura", .38f, .18f, 1, .20f),
    STORM("storm", .4f, .28f, 2, .15f),
    BLOCK("block", .3f, .33f, 3, .13f),
    WRAITH_DASH("wraith-dash", .38f, .27f, 2, .12f),
    BRUTE_SLAM("brute-slam", .4f, .36f, 3, .14f),
    MAGIC_CAST("magic-cast", .36f, .24f, 2, .12f),
    // Sampled material UI and party cues share the same bounded mixer.
    UI_FOCUS("ui-focus", .11f, .20f, 2, .07f),
    UI_CONFIRM("ui-confirm", .24f, .30f, 3, .07f),
    UI_BACK("ui-back", .22f, .25f, 3, .07f),
    LOOT("loot", 1.05f, .42f, 5, 0),
    PLAYER_DOWN("player-down", .85f, .42f, 4, .15f),
    REVIVE("revive", 1.05f, .42f, 4, .15f);

    public final String path;
    public final float seconds, volume, gate;
    public final int priority;

    SoundCue(String name, float seconds, float volume, int priority, float gate) {
        this.path = "audio/" + name + ".wav";
        this.seconds = seconds;
        this.volume = volume;
        this.priority = priority;
        this.gate = gate;
    }
}
