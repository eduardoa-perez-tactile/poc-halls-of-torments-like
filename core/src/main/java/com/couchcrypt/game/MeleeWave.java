package com.couchcrypt.game;

/** One bounded, combat-owned melee wave. Rendering and collision share this exact clock. */
public final class MeleeWave {
    public static final int PER_PLAYER = 4;
    public static final float EXPAND_SECONDS = .24f, TOTAL_SECONDS = .38f;
    public static final float HALF_ARC_COS = .5735764f, ARC_RADIANS = 1.9198622f;
    public boolean active;
    public int owner;
    public float x, y, dirX, dirY, angle, reach, age;
    float damage, critChance, critMultiplier, frostAmount, frostDuration;
    final int[] hitGenerations = new int[ArenaWorld.MAX_ENEMIES];

    /** The bright leading rim reaches the exact snapshotted primary radius at 240 ms. */
    public static float radiusFraction(float age) { return Math.max(0, Math.min(1, age / EXPAND_SECONDS)); }
    public static float radiusAtAge(float age, float reach) { return reach * radiusFraction(age); }
    public float radius() { return radiusAtAge(age, reach); }
    public static float fadeAlpha(float age) {
        return Math.max(0, Math.min(1, (TOTAL_SECONDS - age) / (TOTAL_SECONDS - EXPAND_SECONDS)));
    }
}
