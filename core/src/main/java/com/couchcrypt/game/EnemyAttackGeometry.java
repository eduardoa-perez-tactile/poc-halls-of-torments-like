package com.couchcrypt.game;

/** GL-free contact geometry shared by simulation warnings and collision.
 * Measured source anchors are checked against the shipped attack metadata. */
public final class EnemyAttackGeometry {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    private static final float BRUTE_PIXEL_SCALE = 84f / 64f;
    // SE, SW, NE, NW: fist contact relative to the fixed root in normalized sprite pixels, Y up.
    private static final float[] BRUTE_SLAM = {14, 1, -23, -2, 19, 13, -19, 13};

    private EnemyAttackGeometry() {}

    public static float bruteSlamX(ActorAnimation.Facing facing, boolean elite) {
        return bruteSlamX(facing, EnemyVariant.ORIGINAL, elite);
    }

    public static float bruteSlamX(ActorAnimation.Facing facing, EnemyVariant variant, boolean elite) {
        int i = facing.ordinal() * 2;
        float scale = BRUTE_PIXEL_SCALE * EnemySizing.renderScale(ArenaWorld.EnemyKind.BRUTE, variant, elite);
        return .5f * (BRUTE_SLAM[i] / ISO_X + BRUTE_SLAM[i + 1] / ISO_Y) * scale;
    }

    public static float bruteSlamY(ActorAnimation.Facing facing, boolean elite) {
        return bruteSlamY(facing, EnemyVariant.ORIGINAL, elite);
    }

    public static float bruteSlamY(ActorAnimation.Facing facing, EnemyVariant variant, boolean elite) {
        int i = facing.ordinal() * 2;
        float scale = BRUTE_PIXEL_SCALE * EnemySizing.renderScale(ArenaWorld.EnemyKind.BRUTE, variant, elite);
        return .5f * (BRUTE_SLAM[i + 1] / ISO_Y - BRUTE_SLAM[i] / ISO_X) * scale;
    }
}
