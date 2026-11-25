package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** One plain attack-range outline, projected onto the 2:1 ground plane. */
final class PlayerFootRing {
    static final float STROKE_MARGIN = 1.3f;
    private static final int SEGMENTS = 192;
    // A 1.2-logical-unit core with small antialiasing fringes; no halo or filled centre.
    private static final float[] EDGE = {-STROKE_MARGIN, -.6f, .6f, STROKE_MARGIN};
    private static final float[] ALPHA = {0, .88f, .88f, 0};
    private final TextureRegion pixel;
    // Unit ellipse positions and outward screen-space normals, calculated only at startup.
    private final float[] unit = new float[SEGMENTS * 4];
    private final float[] vertices = new float[SEGMENTS * (EDGE.length - 1) * 20];
    private final float[] gold = new float[EDGE.length], teal = new float[EDGE.length];

    PlayerFootRing(TextureRegion pixel) {
        this.pixel = pixel;
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float angle = MathUtils.PI2 * segment / SEGMENTS;
            float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
            float normalLength = (float) Math.sqrt(c * c + 4f * s * s);
            int at = segment * 4;
            unit[at] = c; unit[at + 1] = s * .5f;
            unit[at + 2] = c / normalLength; unit[at + 3] = 2f * s / normalLength;
        }
        for (int edge = 0; edge < EDGE.length; edge++) {
            gold[edge] = Color.toFloatBits(1f, .82f, .42f, ALPHA[edge]);
            teal[edge] = Color.toFloatBits(.36f, .95f, .90f, ALPHA[edge]);
        }
        for (int at = 0; at < vertices.length; at += 5) {
            // Ordinary UVs sample the resident white texel without an enemy palette marker.
            vertices[at + 3] = u; vertices[at + 4] = v;
        }
    }

    void draw(SpriteBatch batch, float x, float y, float radius, int slot) {
        if (!Float.isFinite(radius) || radius <= 0) return;
        float[] colors = slot == 0 ? gold : teal;
        int vertex = 0;
        for (int edge = 0; edge < EDGE.length - 1; edge++) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                point(vertex++, segment, edge, x, y, radius, colors);
                point(vertex++, segment, edge + 1, x, y, radius, colors);
                point(vertex++, next, edge + 1, x, y, radius, colors);
                point(vertex++, next, edge, x, y, radius, colors);
            }
        }
        batch.draw(pixel.getTexture(), vertices, 0, vertices.length);
    }

    private void point(int vertex, int segment, int edge, float x, float y, float radius, float[] colors) {
        int at = vertex * 5, direction = segment * 4;
        // Keep the stroke centred on the exact range while its thickness stays constant as range changes.
        vertices[at] = x + unit[direction] * radius + unit[direction + 2] * EDGE[edge];
        vertices[at + 1] = y + unit[direction + 1] * radius + unit[direction + 3] * EDGE[edge];
        vertices[at + 2] = colors[edge];
    }
}
