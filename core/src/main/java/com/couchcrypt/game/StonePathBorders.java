package com.couchcrypt.game;

import java.util.ArrayList;

/** Flat decorative paving blocks, prepared once and shared by both arena cameras. */
public final class StonePathBorders {
    public static final float STONE_LENGTH = 38f, STONE_WIDTH = 20f, PERIOD = 40f;
    public static final int MAX_STONES = 1024, MAX_VERTICES = 8;
    public final Block[] blocks;
    /** Each convex polygon packs world X/Y then normalized material U/V per vertex. */
    public final float[][] polygons;

    public static final class Block {
        public final int pathIndex, side;
        public final float start, length;
        public final float[] vertices;
        private Block(int pathIndex, int side, float start, float length, float[] vertices) {
            this.pathIndex = pathIndex; this.side = side; this.start = start; this.length = length;
            this.vertices = vertices;
        }
    }

    public StonePathBorders(ArenaFloorLayout floor) {
        ArrayList<Block> result = new ArrayList<>(512);
        float offset = floor.pathWidth * .5f - STONE_WIDTH * .5f;
        for (int pathIndex = 0; pathIndex < floor.paths.length; pathIndex++) {
            ArenaFloorLayout.Path path = floor.paths[pathIndex];
            for (int side = -1; side <= 1; side += 2) {
                for (float period = 0; period < path.length(); period += PERIOD) {
                    // Centre the small grout gap in each repeat; clipping never enlarges a stone.
                    float start = period + (PERIOD - STONE_LENGTH) * .5f;
                    if (start >= path.length()) continue;
                    float end = Math.min(start + STONE_LENGTH, path.length());
                    float cx = floor.centerX - path.unitY() * offset * side;
                    float cy = floor.centerY + path.unitX() * offset * side;
                    float x0 = cx + path.unitX() * start, y0 = cy + path.unitY() * start;
                    float x1 = cx + path.unitX() * end, y1 = cy + path.unitY() * end;
                    float nx = -path.unitY() * STONE_WIDTH * .5f, ny = path.unitX() * STONE_WIDTH * .5f;
                    float u1 = (end - start) / STONE_LENGTH;
                    float[] vertices = {x0 + nx, y0 + ny, 0, 0, x0 - nx, y0 - ny, 0, 1,
                        x1 - nx, y1 - ny, u1, 1, x1 + nx, y1 + ny, u1, 0};
                    vertices = clip(vertices, 0, -ArenaWorld.HALF_WIDTH, true);
                    vertices = clip(vertices, 0, ArenaWorld.HALF_WIDTH, false);
                    vertices = clip(vertices, 1, -ArenaWorld.HALF_HEIGHT, true);
                    vertices = clip(vertices, 1, ArenaWorld.HALF_HEIGHT, false);
                    if (vertices.length < 12 || area(vertices) < .01f) continue;
                    if (vertices.length / 4 > MAX_VERTICES || result.size() >= MAX_STONES)
                        throw new IllegalArgumentException("Stone path border exceeds its static geometry pool");
                    result.add(new Block(pathIndex, side, start, end - start, vertices));
                }
            }
        }
        blocks = result.toArray(new Block[0]);
        polygons = new float[blocks.length][];
        for (int i = 0; i < blocks.length; i++) polygons[i] = blocks[i].vertices;
    }

    private static float[] clip(float[] polygon, int axis, float boundary, boolean greater) {
        int count = polygon.length / 4;
        if (count == 0) return polygon;
        float[] output = new float[(count + 1) * 4];
        int size = 0, previous = (count - 1) * 4;
        for (int current = 0; current < polygon.length; current += 4) {
            float a = polygon[previous + axis], b = polygon[current + axis];
            boolean ai = greater ? a >= boundary : a <= boundary;
            boolean bi = greater ? b >= boundary : b <= boundary;
            if (ai != bi) {
                float fraction = (boundary - a) / (b - a);
                for (int component = 0; component < 4; component++)
                    output[size + component] = polygon[previous + component]
                        + (polygon[current + component] - polygon[previous + component]) * fraction;
                output[size + axis] = boundary;
                size += 4;
            }
            if (bi) { System.arraycopy(polygon, current, output, size, 4); size += 4; }
            previous = current;
        }
        float[] result = new float[size];
        System.arraycopy(output, 0, result, 0, size);
        return result;
    }

    private static float area(float[] vertices) {
        float sum = 0;
        for (int i = 0, previous = vertices.length - 4; i < vertices.length; previous = i, i += 4)
            sum += vertices[previous] * vertices[i + 1] - vertices[i] * vertices[previous + 1];
        return sum * .5f;
    }
}
