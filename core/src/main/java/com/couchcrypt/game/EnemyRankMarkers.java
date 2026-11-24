package com.couchcrypt.game;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Compact rank cues from the resident white texel; no texture or frame-loop allocation. */
final class EnemyRankMarkers {
    private static final float UNIT = 1100f / 1920f;
    private final TextureRegion pixel;

    EnemyRankMarkers(TextureRegion pixel) { this.pixel = pixel; }

    void draw(SpriteBatch batch, float x, float bodyTop, boolean elite, float healthFraction) {
        if (!elite) {
            float y = bodyTop + 20 * UNIT;
            outlinedChevron(batch, x, y, false);
        } else {
            float y = bodyTop + 39 * UNIT;
            outlinedChevron(batch, x, y + 7 * UNIT, true);
            outlinedChevron(batch, x, y - 7 * UNIT, true);
            batch.setColor(.125f, .106f, .125f, 1);
            batch.draw(pixel, x - 43 * UNIT, y - 28 * UNIT, 86 * UNIT, 9 * UNIT);
            batch.setColor(.725f, .302f, .278f, 1);
            batch.draw(pixel, x - 40 * UNIT, y - 25 * UNIT,
                80 * UNIT * MathUtils.clamp(healthFraction, 0, 1), 3 * UNIT);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void outlinedChevron(SpriteBatch batch, float x, float y, boolean elite) {
        batch.setColor(.145f, .125f, .149f, 1);
        chevron(batch, x, y, 10 * UNIT);
        if (elite) batch.setColor(.84f, .34f, .30f, 1);
        else batch.setColor(.835f, .776f, .671f, 1);
        chevron(batch, x, y, 4 * UNIT);
    }

    private void chevron(SpriteBatch batch, float x, float y, float thickness) {
        line(batch, x - 12 * UNIT, y + 4 * UNIT, x, y - 4 * UNIT, thickness);
        line(batch, x, y - 4 * UNIT, x + 12 * UNIT, y + 4 * UNIT, thickness);
    }

    private void line(SpriteBatch batch, float x1, float y1, float x2, float y2, float thickness) {
        float dx = x2 - x1, dy = y2 - y1;
        batch.draw(pixel, x1, y1 - thickness * .5f, 0, thickness * .5f,
            (float) Math.sqrt(dx * dx + dy * dy), thickness, 1, 1,
            MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

}
