package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Small contact accents over resident paint; every age comes from the existing combat effect. */
final class CombatContactArt {
    private static final int SEGMENTS = 32;
    private final TextureRegion glow, pixel;
    private final SpriteEffectArt paint;
    private final float[] unit = new float[(SEGMENTS + 1) * 2];
    private final float[] rim = new float[SEGMENTS * 2 * 20];
    private final float[] quad = new float[20];

    CombatContactArt(TextureRegion glow, TextureRegion pixel, SpriteEffectArt paint) {
        this.glow = glow; this.pixel = pixel; this.paint = paint;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = MathUtils.PI2 * i / SEGMENTS;
            unit[i * 2] = MathUtils.cos(angle); unit[i * 2 + 1] = MathUtils.sin(angle);
        }
        for (int at = 0; at < rim.length; at += 5) { rim[at + 3] = u; rim[at + 4] = v; }
    }

    /** A bright point at the original painted head pivot, never an extra damaging projectile. */
    void boltHead(SpriteBatch batch, float x, float y, float scale, boolean critical) {
        light(batch, x, y, 7f * scale, 7f * scale, .58f, critical ? .92f : 1f);
        batch.setColor(1f, 1f, .93f, .94f);
        batch.draw(glow, x - 2.1f * scale, y - 2.1f * scale, 4.2f * scale, 4.2f * scale);
        batch.setColor(Color.WHITE);
    }

    /** Compact emitter flash; it uses the existing 100 ms release/emergence window. */
    void release(SpriteBatch batch, float x, float y, float age, float planeAngle) {
        float life = MathUtils.clamp(1f - age / .1f, 0f, 1f);
        float size = ProjectilePresentation.spawnScale(age);
        light(batch, x, y, 18f * size, 15f * size, life * .58f, 1f);
        batch.setColor(1f, 1f, .87f, life * .95f);
        streak(batch, x, y, 11f * size, 3f * size, planeAngle);
        streak(batch, x, y, 6f * size, 2f * size, planeAngle + MathUtils.PI * .5f);
        batch.setColor(Color.WHITE);
    }

    /** A radial puncture bloom stays on the actual bolt contact, with no directional fan. */
    void boltImpact(SpriteBatch batch, float x, float y, float progress, boolean critical) {
        float life = 1f - progress, size = critical ? 1.22f : 1f;
        batch.setColor(critical ? 1f : .74f, critical ? .94f : 1f, critical ? .70f : 1f, .85f);
        paint.draw(batch, SpriteEffectArt.Type.CONTACT, progress, x, y,
            .17f * size * paint.referenceScale(SpriteEffectArt.Type.CONTACT), 0, SpriteEffectArt.Layer.ALL);
        light(batch, x, y, (19f + progress * 7f) * size, (19f + progress * 7f) * size,
            .65f * life * life, critical ? .86f : 1f);
        batch.setColor(1f, 1f, .92f, .95f * life * life);
        batch.draw(glow, x - 4.5f * size, y - 4.5f * size, 9f * size, 9f * size);
        // The small circular bloom is elevated contact feedback, not a ground-area warning.
        radialRim(batch, x, y, (5f + progress * 10f) * size, .42f * life, critical);
        for (int i = 0; i < 5; i++) {
            int direction = ((i * 7 + 2) % SEGMENTS) * 2;
            float distance = (3f + progress * (10f + i * 1.3f)) * size;
            float px = x + unit[direction] * distance, py = y + unit[direction + 1] * distance;
            batch.setColor(critical ? 1f : .71f, 1f, critical ? .64f : .97f, life * .72f);
            batch.draw(glow, px - 1.5f, py - 1.5f, 3f, 3f);
        }
        batch.setColor(Color.WHITE);
    }

    /** Ivory cutting core and five short gold sparks, projected at the existing target hit height. */
    void swordImpact(SpriteBatch batch, float x, float y, float progress, float planeAngle, boolean critical) {
        float life = 1f - progress, size = critical ? 1.22f : 1f;
        batch.setColor(1f, .81f, .40f, .38f * life);
        streak(batch, x, y, 25f * size, 10f * size, planeAngle);
        batch.setColor(1f, 1f, .86f, .92f * life * life);
        streak(batch, x, y, 15f * size, 4f * size, planeAngle);
        for (int i = 0; i < 5; i++) {
            float angle = planeAngle + (i - 2) * .49f;
            float distance = (3f + progress * (13f + i * 1.8f)) * size;
            float px = x + MathUtils.cos(angle) * distance;
            float py = y + .5f * MathUtils.sin(angle) * distance;
            batch.setColor(1f, .87f + .02f * (i % 2), .49f, .82f * life);
            streak(batch, px, py, (4f + life * 5f) * size, 1.8f * size, angle);
        }
        batch.setColor(Color.WHITE);
    }

    private void light(SpriteBatch batch, float x, float y, float width, float height, float alpha, float blue) {
        batch.setColor(.79f, 1f, blue, alpha);
        batch.draw(glow, x - width * .5f, y - height * .5f, width, height);
    }

    /** Rotate in the uncompressed weapon plane, then apply the arena's 2:1 projection. */
    private void streak(SpriteBatch batch, float x, float y, float length, float width, float angle) {
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle), color = batch.getPackedColor();
        float a = length * .5f, b = width * .5f;
        point(0, x, y, -a, -b, c, s, color, glow.getU(), glow.getV2());
        point(5, x, y, -a, b, c, s, color, glow.getU(), glow.getV());
        point(10, x, y, a, b, c, s, color, glow.getU2(), glow.getV());
        point(15, x, y, a, -b, c, s, color, glow.getU2(), glow.getV2());
        batch.draw(glow.getTexture(), quad, 0, quad.length);
    }

    private void point(int at, float x, float y, float u, float v, float c, float s,
                       float color, float textureU, float textureV) {
        quad[at] = x + u * c - v * s; quad[at + 1] = y + .5f * (u * s + v * c);
        quad[at + 2] = color; quad[at + 3] = textureU; quad[at + 4] = textureV;
    }

    private void radialRim(SpriteBatch batch, float x, float y, float radius, float alpha, boolean critical) {
        float clear = Color.toFloatBits(1f, 1f, 1f, 0);
        float core = Color.toFloatBits(critical ? 1f : .70f, .96f, critical ? .54f : 1f, alpha);
        int at = 0;
        for (int band = 0; band < 2; band++) for (int segment = 0; segment < SEGMENTS; segment++) {
            float inner = radius + (band == 0 ? -1.2f : 0f), outer = radius + (band == 0 ? 0f : 1.2f);
            float ci = band == 0 ? clear : core, co = band == 0 ? core : clear;
            at = rimPoint(at, segment, inner, x, y, ci);
            at = rimPoint(at, segment, outer, x, y, co);
            at = rimPoint(at, segment + 1, outer, x, y, co);
            at = rimPoint(at, segment + 1, inner, x, y, ci);
        }
        batch.draw(pixel.getTexture(), rim, 0, rim.length);
    }

    private int rimPoint(int at, int direction, float radius, float x, float y, float color) {
        rim[at] = x + unit[direction * 2] * radius;
        rim[at + 1] = y + unit[direction * 2 + 1] * radius;
        rim[at + 2] = color;
        return at + 5;
    }
}
