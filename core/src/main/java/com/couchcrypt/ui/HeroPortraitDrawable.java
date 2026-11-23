package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/** Cropped portrait with a short bottom alpha fade, borrowing its managed atlas. */
public final class HeroPortraitDrawable extends BaseDrawable {
    private final TextureRegion region;
    private final float[] vertices = new float[40];

    public HeroPortraitDrawable(TextureRegion source, float visibleHeight) {
        region = new TextureRegion(source, 0, 0, source.getRegionWidth(),
            Math.round(source.getRegionHeight() * visibleHeight));
        setMinWidth(region.getRegionWidth()); setMinHeight(region.getRegionHeight());
    }

    @Override public void draw(Batch batch, float x, float y, float width, float height) {
        float solid = batch.getPackedColor();
        Color tint = batch.getColor();
        float clear = Color.toFloatBits(tint.r, tint.g, tint.b, 0);
        float seam = region.getV2() - (region.getV2() - region.getV()) * .20f;
        quad(0, x, y, width, height * .20f, region.getU(), seam, region.getU2(), region.getV2(), clear, solid);
        quad(20, x, y + height * .20f, width, height * .80f, region.getU(), region.getV(), region.getU2(), seam, solid, solid);
        batch.draw(region.getTexture(), vertices, 0, vertices.length);
    }

    private void quad(int offset, float x, float y, float width, float height,
        float u, float v, float u2, float v2, float bottom, float top) {
        vertex(offset, x, y, bottom, u, v2);
        vertex(offset + 5, x, y + height, top, u, v);
        vertex(offset + 10, x + width, y + height, top, u2, v);
        vertex(offset + 15, x + width, y, bottom, u2, v2);
    }

    private void vertex(int offset, float x, float y, float color, float u, float v) {
        vertices[offset] = x; vertices[offset + 1] = y; vertices[offset + 2] = color;
        vertices[offset + 3] = u; vertices[offset + 4] = v;
    }
}
