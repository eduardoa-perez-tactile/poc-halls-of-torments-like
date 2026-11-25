package com.couchcrypt.game;

/** File contract and mixer metadata for short, resident effects. Music belongs to SceneMusic. */
public enum SoundCue {
    SWORD("sword", .17f, .25f, 1, .075f),
    ARROW("arrow", .095f, .24f, 1, .07f),
    HIT("hit", .10f, .36f, 3, .06f), // Retained legacy asset; contacts use the two weapon cues below.
    PICKUP("pickup", .17f, .20f, 0, .09f),
    LEVEL("level", .55f, .42f, 5, 0),
    BOSS("boss", .65f, .48f, 5, 0),
    VICTORY("victory", .75f, .48f, 5, 0),
    DEFEAT("defeat", .55f, .48f, 5, 0),
    SWORD_HIT("sword-hit", .12f, .44f, 3, .06f),
    BOLT_HIT("bolt-hit", .09f, .36f, 3, .045f),
    CRITICAL("critical", .18f, .35f, 3, .08f),
    DODGE("dodge", .16f, .32f, 3, .12f),
    HURT("hurt", .16f, .46f, 4, .13f),
    DEATH("death", .24f, .20f, 1, .09f),
    HEAL("heal", .24f, .30f, 3, .14f),
    AURA("aura", .24f, .18f, 1, .20f),
    STORM("storm", .22f, .28f, 2, .15f),
    BLOCK("block", .13f, .33f, 3, .13f),
    WRAITH_DASH("wraith-dash", .18f, .27f, 2, .12f),
    BRUTE_SLAM("brute-slam", .20f, .36f, 3, .14f),
    MAGIC_CAST("magic-cast", .16f, .24f, 2, .12f),
    // Optional production assets: the events are wired even before these files are supplied.
    UI_FOCUS("ui-focus", .08f, .20f, 2, .07f),
    UI_CONFIRM("ui-confirm", .14f, .30f, 3, .07f),
    UI_BACK("ui-back", .12f, .25f, 3, .07f),
    LOOT("loot", .55f, .42f, 5, 0),
    PLAYER_DOWN("player-down", .45f, .42f, 4, .15f),
    REVIVE("revive", .55f, .42f, 4, .15f);

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
