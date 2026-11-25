package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Compact reward light, using resident pixels and the shared combat clock only. */
final class LootChestVfx {
    private static final int SEGMENTS = 48, GLINTS = 5;
    private static final float RADIUS = 40f;
    private static final float[] EDGE = {-1f, -.4f, .4f, 1f};
    private static final float[] ALPHA = {0, .22f, .22f, 0};
    private final TextureRegion pixel, glow;
    private final float[] rim = new float[36 * 3 * 20];
    private final float[] vertices = new float[rim.length];

    LootChestVfx(TextureRegion pixel, TextureRegion glow) {
        this.pixel = pixel; this.glow = glow;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        int at = 0;
        for (int edge = 0; edge < EDGE.length - 1; edge++) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                if (segment % 12 < 3) continue; // Four small gaps keep the rim quiet and incomplete.
                at = point(at, segment, edge, u, v);
                at = point(at, segment, edge + 1, u, v);
                at = point(at, segment + 1, edge + 1, u, v);
                at = point(at, segment + 1, edge, u, v);
            }
        }
        System.arraycopy(rim, 0, vertices, 0, rim.length);
    }

    private int point(int at, int segment, int edge, float u, float v) {
        float angle = MathUtils.PI2 * segment / SEGMENTS;
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        float normal = (float) Math.sqrt(c * c + 4f * s * s);
        rim[at++] = c * RADIUS + c / normal * EDGE[edge];
        rim[at++] = s * RADIUS * .5f + 2f * s / normal * EDGE[edge];
        rim[at++] = Color.toFloatBits(1f, .70f, .24f, ALPHA[edge]);
        rim[at++] = u; rim[at++] = v;
        return at;
    }

    /** Runs before hazards and the complete actor pass; the light stays flat on the ground. */
    void groundLight(SpriteBatch batch, float x, float y) {
        batch.setColor(1f, .61f, .13f, .42f);
        batch.draw(glow, x - 51f, y - 25.5f, 102f, 51f);
        batch.setColor(1f, .78f, .32f, .20f);
        batch.draw(glow, x - 35f, y - 17.5f, 70f, 35f);
        batch.setColor(Color.WHITE);
    }

    void groundRim(SpriteBatch batch, float x, float y) {
        for (int at = 0; at < vertices.length; at += 5) {
            vertices[at] = rim[at] + x; vertices[at + 1] = rim[at + 1] + y;
        }
        batch.draw(pixel.getTexture(), vertices, 0, vertices.length);
        batch.setColor(Color.WHITE);
    }

    /** Drawn at the chest's own depth, so nearer heroes/enemies cover its short wisps. */
    void wisps(SpriteBatch batch, float x, float y, float combatTime, int index) {
        for (int i = 0; i < 2; i++) {
            float phase = (combatTime / 3.8f + i * .5f + index * .17f) % 1f;
            float sway = MathUtils.sin(phase * MathUtils.PI2) * 2f;
            float alpha = MathUtils.sin(phase * MathUtils.PI) * .27f;
            batch.setColor(1f, .73f, .29f, alpha);
            batch.draw(glow, x - 8f + i * 15f + sway, y + 24f + phase * 19f, 4f, 25f);
        }
        batch.setColor(Color.WHITE);
    }

    void glints(SpriteBatch batch, float x, float y, float combatTime, int index) {
        for (int i = 0; i < GLINTS; i++) {
            float phase = (combatTime / 3.6f + i * .2f + index * .13f) % 1f;
            float fade = MathUtils.sin(phase * MathUtils.PI);
            float sx = x + MathUtils.sin(i * 2.4f + phase * 1.6f) * (21f + i * 2f);
            float sy = y + 10f + phase * 48f;
            batch.setColor(1f, .73f, .26f, fade * .25f);
            batch.draw(glow, sx - 4f, sy - 4f, 8f, 8f);
            batch.setColor(1f, .92f, .66f, fade * .85f);
            float size = 1f + fade * .7f;
            batch.draw(pixel, sx - size * .5f, sy - size * .5f, size * .5f, size * .5f,
                size, size, 1, 1, 45f);
        }
        batch.setColor(Color.WHITE);
    }
}
