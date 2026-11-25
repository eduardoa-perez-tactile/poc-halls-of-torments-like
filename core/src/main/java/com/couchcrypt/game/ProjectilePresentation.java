package com.couchcrypt.game;

/** Pure, combat-age-driven missile emergence; never changes a projectile's simulation radius. */
final class ProjectilePresentation {
    static final float EMERGE_SECONDS = .1f;
    private ProjectilePresentation() { }

    static float spawnScale(float age) {
        if (Float.isNaN(age) || age < 0) return 0;
        if (age >= EMERGE_SECONDS) return 1;
        float t = age / EMERGE_SECONDS;
        return .2f + .8f * t * t * (3f - 2f * t);
    }
}
