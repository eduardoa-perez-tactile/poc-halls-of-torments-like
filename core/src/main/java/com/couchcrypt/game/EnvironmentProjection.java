package com.couchcrypt.game;

/** Ground-root affine correction for the existing painted architecture; uprights never rotate. */
public final class EnvironmentProjection {
    private EnvironmentProjection() {}

    /** Coordinates are relative to the measured ground pivot, after uniform footprint scaling. */
    public static float projectedY(float localX, float localY, GameplayAtlas.Region region) {
        return localY * region.renderScaleY() + localX * region.renderShearY();
    }
}
