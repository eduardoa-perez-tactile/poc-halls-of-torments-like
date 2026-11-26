package com.couchcrypt.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.graphics.Color;

/** Exact former three-band AA outline: static large mesh and batched small warnings. */
final class RingGeometry {
    static final int SEGMENTS = 192, STRIDE = 6;
    static final int FULL_INDICES = SEGMENTS * 3 * 6;
    static final float STROKE_MARGIN = 1.3f;
    static final float[] EDGES = {-STROKE_MARGIN, -.6f, .6f, STROKE_MARGIN};
    final float[] vertices = new float[SEGMENTS * EDGES.length * STRIDE];
    final short[] indices = new short[FULL_INDICES];

    RingGeometry() {
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float angle = MathUtils.PI2 * segment / SEGMENTS;
            float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
            float normalLength = (float) Math.sqrt(c * c + 4f * s * s);
            for (int edge = 0; edge < EDGES.length; edge++) {
                int at = (edge * SEGMENTS + segment) * STRIDE;
                vertices[at] = c; vertices[at + 1] = s * .5f;
                vertices[at + 2] = c / normalLength; vertices[at + 3] = 2f * s / normalLength;
                vertices[at + 4] = EDGES[edge];
                vertices[at + 5] = edge == 0 || edge == EDGES.length - 1 ? 0 : 1;
            }
        }
        indices(1, 0);
    }

    private void indices(int step, int at) {
        for (int edge = 0; edge < EDGES.length - 1; edge++) {
            for (int segment = 0; segment < SEGMENTS; segment += step) {
                int next = (segment + step) % SEGMENTS;
                short a = (short) (edge * SEGMENTS + segment);
                short b = (short) ((edge + 1) * SEGMENTS + segment);
                short c = (short) ((edge + 1) * SEGMENTS + next);
                short d = (short) (edge * SEGMENTS + next);
                indices[at++] = a; indices[at++] = b; indices[at++] = c;
                indices[at++] = c; indices[at++] = d; indices[at++] = a;
            }
        }
    }

    // Match SpriteBatch's packed byte colour, including its shader's 255/254 alpha correction.
    static float channel(float value) { return (int) (value * 255f) / 255f; }
    static float alpha(float value) { return ((int) (value * 255f) & 254) / 254f; }

    /** Fixed 144-quad scratch buffer; ordinary small warnings can share the existing SpriteBatch. */
    static final class Small {
        private static final int SMALL_SEGMENTS = SEGMENTS / 4;
        private final float[] unit = new float[SMALL_SEGMENTS * 4];
        final float[] vertices = new float[SMALL_SEGMENTS * 3 * 20];
        private final float[] colors = new float[EDGES.length];

        Small(float u, float v) {
            for (int segment = 0; segment < SMALL_SEGMENTS; segment++) {
                // Keep the original angle operation order, including its step of four.
                float angle = MathUtils.PI2 * (segment * 4) / SEGMENTS;
                float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
                float length = (float) Math.sqrt(c * c + 4f * s * s);
                int at = segment * 4;
                unit[at] = c; unit[at + 1] = s * .5f;
                unit[at + 2] = c / length; unit[at + 3] = 2f * s / length;
            }
            for (int at = 0; at < vertices.length; at += 5) {
                vertices[at + 3] = u; vertices[at + 4] = v;
            }
        }

        void update(float x, float y, float radius, float red, float green, float blue, float alpha) {
            for (int edge = 0; edge < EDGES.length; edge++)
                colors[edge] = Color.toFloatBits(red, green, blue, edge == 0 || edge == EDGES.length - 1 ? 0 : alpha);
            int vertex = 0;
            for (int edge = 0; edge < EDGES.length - 1; edge++) {
                for (int segment = 0; segment < SMALL_SEGMENTS; segment++) {
                    int next = (segment + 1) % SMALL_SEGMENTS;
                    point(vertex++, segment, edge, x, y, radius);
                    point(vertex++, segment, edge + 1, x, y, radius);
                    point(vertex++, next, edge + 1, x, y, radius);
                    point(vertex++, next, edge, x, y, radius);
                }
            }
        }

        private void point(int vertex, int segment, int edge, float x, float y, float radius) {
            int at = vertex * 5, direction = segment * 4;
            vertices[at] = x + unit[direction] * radius + unit[direction + 2] * EDGES[edge];
            vertices[at + 1] = y + unit[direction + 1] * radius + unit[direction + 3] * EDGES[edge];
            vertices[at + 2] = colors[edge];
        }
    }
}
