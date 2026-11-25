package com.couchcrypt.game;

/** Startup-only wall cut seated safely inside the existing painted gate's opaque jambs. */
final class GateWallOpening {
    private static final float ISO_X = .70710678f;
    // Measured against the resident gate at its original 250x50 footprint; not a collision opening.
    private static final float HALF_WIDTH = 90f;
    final float[][] vertices;
    final float left, right;
    final int clippedPieces;

    GateWallOpening(PaintedBorderArt.Scene scene, ArenaLayout layout, GameplayAtlas atlas) {
        vertices = new float[scene.quads.length][];
        ArenaLayout.Placement gate = null;
        for (ArenaLayout.Placement placement : layout.placements)
            if (placement.region.equals("sealed-gate")) { gate = placement; break; }
        if (gate == null) { left = right = 0; clippedPieces = 0; return; }
        GameplayAtlas.Region source = atlas.regions.get(gate.region);
        float nominalScale = (source.footprintWorldX() + source.footprintWorldY()) * ISO_X / source.footprintWidth();
        float halfWidth = HALF_WIDTH * ISO_X * gate.scale / nominalScale;
        float center = (gate.x - gate.y) * ISO_X;
        left = center - halfWidth; right = center + halfWidth;
        int count = 0;
        for (int index = 0; index < scene.quads.length; index++) {
            PaintedBorderArt.Quad quad = scene.quads[index];
            if (!quad.painting.name.equals("x") || quad.renderLayer != PaintedBorderArt.BACK_WALL_LAYER
                || Math.abs(quad.worldY - gate.y) > .01f) continue;
            float minX = quad.vertices[0], maxX = quad.vertices[10];
            if (maxX <= left || minX >= right) continue;
            int quads = (minX < left ? 1 : 0) + (maxX > right ? 1 : 0);
            float[] cut = new float[quads * 20];
            int offset = 0;
            if (minX < left) { section(quad.vertices, minX, left, cut, offset); offset += 20; }
            if (maxX > right) section(quad.vertices, right, maxX, cut, offset);
            vertices[index] = cut;
            count++;
        }
        clippedPieces = count;
    }

    private static void section(float[] original, float left, float right, float[] target, int offset) {
        float width = original[10] - original[0];
        float a = (left - original[0]) / width, b = (right - original[0]) / width;
        vertex(original, 0, 15, a, target, offset);
        vertex(original, 5, 10, a, target, offset + 5);
        vertex(original, 5, 10, b, target, offset + 10);
        vertex(original, 0, 15, b, target, offset + 15);
    }

    private static void vertex(float[] source, int first, int last, float progress, float[] target, int offset) {
        for (int component = 0; component < 5; component++) {
            if (component == 2) target[offset + component] = source[first + component];
            else target[offset + component] = source[first + component]
                + (source[last + component] - source[first + component]) * progress;
        }
    }
}
