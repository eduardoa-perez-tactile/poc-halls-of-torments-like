package com.couchcrypt.game;

/** GL-free contact geometry shared by simulation warnings and collision.
 * Measured source anchors are checked against the shipped attack metadata. */
public final class EnemyAttackGeometry {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    private static final float BRUTE_PIXEL_SCALE = 84f / 64f, BRUTE_SCALE = 1.4f, ELITE_SCALE = 1.18f;
    // SE, SW, NE, NW: fist contact relative to the fixed root in normalized sprite pixels, Y up.
    private static final float[] BRUTE_SLAM = {14, 1, -23, -2, 19, 13, -19, 13};

    private EnemyAttackGeometry() {}

    public static float bruteSlamX(ActorAnimation.Facing facing, boolean elite) {
        int i = facing.ordinal() * 2;
        float scale = BRUTE_PIXEL_SCALE * BRUTE_SCALE * (elite ? ELITE_SCALE : 1f);
        return .5f * (BRUTE_SLAM[i] / ISO_X + BRUTE_SLAM[i + 1] / ISO_Y) * scale;
    }

    public static float bruteSlamY(ActorAnimation.Facing facing, boolean elite) {
        int i = facing.ordinal() * 2;
        float scale = BRUTE_PIXEL_SCALE * BRUTE_SCALE * (elite ? ELITE_SCALE : 1f);
        return .5f * (BRUTE_SLAM[i + 1] / ISO_Y - BRUTE_SLAM[i] / ISO_X) * scale;
    }
}
