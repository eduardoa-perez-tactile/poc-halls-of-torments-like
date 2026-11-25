package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Tiny resident-pixel ground accents; clocks and locked attack geometry remain combat-owned. */
final class EnemyGroundVfx {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    // Two thin angled strokes form an open >, without a shaft or filled triangular head.
    private static final float[] CHEVRON = chevron(1.1f);
    private final TextureRegion pixel, glow;
    private final float[] arrowVertices = new float[40];
    private final float[] directions = new float[8 * 4];

    EnemyGroundVfx(TextureRegion pixel, TextureRegion glow) {
        this.pixel = pixel; this.glow = glow;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        for (int at = 0; at < arrowVertices.length; at += 5) {
            arrowVertices[at + 3] = u; arrowVertices[at + 4] = v;
        }
        for (int i = 0; i < 8; i++) {
            float angle = MathUtils.PI2 * (i + .18f) / 8;
            directions[i * 4] = MathUtils.cos(angle);
            directions[i * 4 + 1] = MathUtils.sin(angle);
            directions[i * 4 + 2] = 2.4f + (i % 3) * .65f;
            directions[i * 4 + 3] = i * 43f + 12f;
        }
    }

    /** dx/dy are the normalized committed world-space dash direction, never a live target. */
    void arrow(SpriteBatch batch, float x, float y, float dx, float dy, float alpha, boolean released) {
        float color = released ? Color.toFloatBits(1f, .65f, .39f, alpha)
            : Color.toFloatBits(1f, .94f, .79f, alpha);
        arrowPass(batch, x, y, dx, dy, CHEVRON, color);
    }

    private static float[] chevron(float halfWidth) {
        float[] points = new float[16];
        for (int stroke = 0; stroke < 2; stroke++) {
            float x1 = stroke == 0 ? -8f : 8f, y1 = stroke == 0 ? 9f : 0f;
            float x2 = stroke == 0 ? 8f : -8f, y2 = stroke == 0 ? 0f : -9f;
            float dx = x2 - x1, dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = -dy * halfWidth / length, ny = dx * halfWidth / length;
            int at = stroke * 8;
            points[at] = x1 + nx; points[at + 1] = y1 + ny;
            points[at + 2] = x1 - nx; points[at + 3] = y1 - ny;
            points[at + 4] = x2 - nx; points[at + 5] = y2 - ny;
            points[at + 6] = x2 + nx; points[at + 7] = y2 + ny;
        }
        return points;
    }

    private void arrowPass(SpriteBatch batch, float x, float y, float dx, float dy, float[] shape, float color) {
        for (int vertex = 0; vertex < shape.length / 2; vertex++) {
            float forward = shape[vertex * 2], side = shape[vertex * 2 + 1];
            float wx = x + dx * forward - dy * side, wy = y + dy * forward + dx * side;
            int at = vertex * 5;
            arrowVertices[at] = (wx - wy) * ISO_X;
            arrowVertices[at + 1] = (wx + wy) * ISO_Y;
            arrowVertices[at + 2] = color;
        }
        batch.draw(pixel.getTexture(), arrowVertices, 0, arrowVertices.length);
    }

    /** A .3-second local dust-and-stone burst inside the existing locked slam footprint. */
    void bruteSmash(SpriteBatch batch, float x, float y, float radius, float progress) {
        float fade = 1f - progress;
        float travel = 1f - fade * fade;
        float cx = (x - y) * ISO_X, cy = (x + y) * ISO_Y;
        // One restrained initial contact bloom; no fire, ornate rim or large painted spikes.
        if (progress < .3f) {
            batch.setColor(.91f, .85f, .67f, .36f * (1f - progress / .3f));
            batch.draw(glow, cx - 14f, cy - 7f, 28f, 14f);
        }
        for (int i = 0; i < 8; i++) {
            int at = i * 4;
            float dx = directions[at], dy = directions[at + 1];
            float distance = radius * (.12f + travel * (.44f + (i % 3) * .05f));
            float sx = cx + (dx - dy) * ISO_X * distance;
            float groundY = cy + (dx + dy) * ISO_Y * distance;
            // Eight small, soft dust puffs stay close to the contact and quickly disperse.
            float width = 12f + travel * 15f;
            batch.setColor(.66f, .63f, .56f, .34f * fade);
            batch.draw(glow, sx - width * .5f, groundY - width * .25f, width, width * .5f);
            float lift = (9f + (i % 3) * 3f) * 4f * progress * fade;
            float size = directions[at + 2];
            batch.setColor(.075f, .065f, .055f, .65f * fade);
            batch.draw(pixel, sx - size * .6f, groundY - size * .3f, size * 1.2f, size * .6f);
            batch.setColor(.76f, .73f, .65f, .9f * fade);
            batch.draw(pixel, sx - size * .5f, groundY + lift - size * .5f,
                size * .5f, size * .5f, size, size * .65f, 1f, 1f, directions[at + 3]);
        }
        batch.setColor(Color.WHITE);
    }
}
