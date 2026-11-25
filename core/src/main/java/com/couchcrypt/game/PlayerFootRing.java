package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Approved luminous double rings and short ticks, cached in the 2:1 ground plane. */
final class PlayerFootRing {
    static final float RADIUS = 38f; // Includes the narrow glow and outer tick ends, not a targeting radius.
    private static final int SEGMENTS = 64, TICKS = 8;
    private static final float[] RINGS = {26f, 32f};
    private static final float[] EDGE = {-3f, -.9f, -.5f, .5f, .9f, 3f};
    private static final float[] ALPHA = {0, .22f, 1, 1, .22f, 0};
    private static final float[] TICK_RADII = {30.5f, 31.3f, 35.6f, 36.5f};
    private static final float[] TICK_LENGTH_ALPHA = {0, 1, 1, 0};
    private static final float[] TICK_WIDTH = {-2f, -.5f, .5f, 2f};
    private static final float[] TICK_WIDTH_ALPHA = {0, 1, 1, 0};
    private static final int QUADS = RINGS.length * SEGMENTS * (EDGE.length - 1) + TICKS * 9;
    private final TextureRegion pixel;
    private final float[] offsets = new float[QUADS * 8], vertices = new float[QUADS * 20];
    private final float[] gold = new float[QUADS * 4], teal = new float[QUADS * 4];

    PlayerFootRing(TextureRegion pixel) {
        this.pixel = pixel;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        int quad = 0;
        for (float radius : RINGS) for (int band = 0; band < EDGE.length - 1; band++)
            for (int segment = 0; segment < SEGMENTS; segment++, quad++) {
                float a = MathUtils.PI2 * segment / SEGMENTS;
                float b = MathUtils.PI2 * ((segment + 1) % SEGMENTS) / SEGMENTS;
                radialPoint(quad * 4, a, radius + EDGE[band], ALPHA[band], u, v);
                radialPoint(quad * 4 + 1, a, radius + EDGE[band + 1], ALPHA[band + 1], u, v);
                radialPoint(quad * 4 + 2, b, radius + EDGE[band + 1], ALPHA[band + 1], u, v);
                radialPoint(quad * 4 + 3, b, radius + EDGE[band], ALPHA[band], u, v);
            }
        for (int tick = 0; tick < TICKS; tick++) {
            float angle = MathUtils.PI2 * tick / TICKS, c = MathUtils.cos(angle), s = MathUtils.sin(angle);
            for (int length = 0; length < 3; length++) for (int width = 0; width < 3; width++, quad++) {
                tickPoint(quad * 4, c, s, length, width, u, v);
                tickPoint(quad * 4 + 1, c, s, length + 1, width, u, v);
                tickPoint(quad * 4 + 2, c, s, length + 1, width + 1, u, v);
                tickPoint(quad * 4 + 3, c, s, length, width + 1, u, v);
            }
        }
    }

    private void radialPoint(int vertex, float angle, float radius, float alpha, float u, float v) {
        point(vertex, MathUtils.cos(angle) * radius, MathUtils.sin(angle) * radius, alpha, u, v);
    }

    private void tickPoint(int vertex, float c, float s, int length, int width, float u, float v) {
        float radius = TICK_RADII[length], tangent = TICK_WIDTH[width];
        point(vertex, c * radius - s * tangent, s * radius + c * tangent,
            TICK_LENGTH_ALPHA[length] * TICK_WIDTH_ALPHA[width], u, v);
    }

    private void point(int vertex, float x, float y, float alpha, float u, float v) {
        offsets[vertex * 2] = x;
        offsets[vertex * 2 + 1] = y * .5f;
        gold[vertex] = Color.toFloatBits(1f, .70f + .20f * alpha, .20f + .35f * alpha, alpha);
        teal[vertex] = Color.toFloatBits(.12f + .40f * alpha, .83f + .17f * alpha, .77f + .18f * alpha, alpha);
        // Raw ordinary UVs sample the white texel directly; they carry no enemy palette marker.
        vertices[vertex * 5 + 3] = u; vertices[vertex * 5 + 4] = v;
    }

    void draw(SpriteBatch batch, float x, float y, int slot) {
        float[] colors = slot == 0 ? gold : teal;
        for (int vertex = 0; vertex < colors.length; vertex++) {
            int at = vertex * 5;
            vertices[at] = x + offsets[vertex * 2];
            vertices[at + 1] = y + offsets[vertex * 2 + 1];
            vertices[at + 2] = colors[vertex];
        }
        batch.draw(pixel.getTexture(), vertices, 0, vertices.length);
    }
}
