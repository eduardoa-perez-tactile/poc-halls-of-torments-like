package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** A crisp, static focus rim over the painted skin; no extra framebuffer or animation. */
final class FocusDrawable extends BaseDrawable {
    private final Drawable surface;
    private final Drawable rim;
    FocusDrawable(Drawable surface, Drawable rim) {
        super(surface); this.surface = surface; this.rim = rim;
    }
    @Override public void draw(Batch batch, float x, float y, float width, float height) {
        surface.draw(batch, x, y, width, height);
        rim.draw(batch, x - 3, y - 3, width + 6, height + 6);
    }
}
