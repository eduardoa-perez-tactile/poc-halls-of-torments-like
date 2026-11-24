package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** A stationary, plain 2:1 foot ring using the resident white texel and cached ellipse geometry. */
final class PlayerFootRing {
    static final float RADIUS = 26f;
    private static final int SEGMENTS = 64, BANDS = 3, QUADS = SEGMENTS * BANDS;
    private static final float[] RADII = {24.8f, 25.05f, 25.75f, RADIUS};
    private static final float[] COLORS = {
        Color.toFloatBits(.95f, .72f, .28f, 0), Color.toFloatBits(.95f, .72f, .28f, 1),
        Color.toFloatBits(.20f, .80f, .76f, 0), Color.toFloatBits(.20f, .80f, .76f, 1)
    };
    private final TextureRegion pixel;
    private final float[] offsets = new float[QUADS * 8], vertices = new float[QUADS * 20];
    private final byte[] opaque = new byte[QUADS * 4];

    PlayerFootRing(TextureRegion pixel) {
        this.pixel = pixel;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        for (int band = 0; band < BANDS; band++) for (int segment = 0; segment < SEGMENTS; segment++) {
            float a = MathUtils.PI2 * segment / SEGMENTS;
            float b = MathUtils.PI2 * ((segment + 1) % SEGMENTS) / SEGMENTS;
            int quad = band * SEGMENTS + segment;
            point(quad * 4, a, RADII[band], band == 0 ? 0 : 1, u, v);
            point(quad * 4 + 1, a, RADII[band + 1], band == 2 ? 0 : 1, u, v);
            point(quad * 4 + 2, b, RADII[band + 1], band == 2 ? 0 : 1, u, v);
            point(quad * 4 + 3, b, RADII[band], band == 0 ? 0 : 1, u, v);
        }
    }

    private void point(int vertex, float angle, float radius, int alpha, float u, float v) {
        offsets[vertex * 2] = MathUtils.cos(angle) * radius;
        offsets[vertex * 2 + 1] = MathUtils.sin(angle) * radius * .5f;
        opaque[vertex] = (byte) alpha;
        // Raw ordinary UVs sample the white texel directly; they carry no enemy palette marker.
        vertices[vertex * 5 + 3] = u; vertices[vertex * 5 + 4] = v;
    }

    void draw(SpriteBatch batch, float x, float y, int slot) {
        int color = slot == 0 ? 0 : 2;
        for (int vertex = 0; vertex < opaque.length; vertex++) {
            int at = vertex * 5;
            vertices[at] = x + offsets[vertex * 2];
            vertices[at + 1] = y + offsets[vertex * 2 + 1];
            vertices[at + 2] = COLORS[color + opaque[vertex]];
        }
        batch.draw(pixel.getTexture(), vertices, 0, vertices.length);
    }
}
