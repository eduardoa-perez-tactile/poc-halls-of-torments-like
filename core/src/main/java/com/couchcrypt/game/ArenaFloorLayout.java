package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.HashSet;

/** Authored, render-only ground geometry. Construct once; it never changes movement or collision. */
public final class ArenaFloorLayout {
    public static final int MAX_PATHS = 12, MAX_CELLS = 128, MAX_CELL_VERTICES = 8;
    public final float centerX, centerY, medallionDiameter, pathWidth, materialRepeat;
    public final Path[] paths;
    public final Cell[] pathCells;
    private final Wear[] wear;

    public record Path(String id, float endX, float endY, float unitX, float unitY, float length) {}

    /** Convex clipped polygon, counter-clockwise; each vertex is world X/Y then normalized material U/V. */
    public static final class Cell {
        public final int pathIndex;
        public final float repeatStart;
        public final float[] vertices;
        public final int vertexCount;
        private Cell(int pathIndex, float repeatStart, float[] vertices) {
            this.pathIndex = pathIndex; this.repeatStart = repeatStart;
            this.vertices = vertices; vertexCount = vertices.length / 4;
        }
    }

    private record Wear(float x, float y, float radius, float delta) {}

    public ArenaFloorLayout(JsonValue root) {
        if (root.getInt("version") != 1 || root.getBoolean("solid", false))
            throw invalid("floor artwork must remain render-only");
        float[] world = pair(root, "worldSize"), center = pair(root, "center");
        if (world[0] != ArenaWorld.HALF_WIDTH * 2 || world[1] != ArenaWorld.HALF_HEIGHT * 2)
            throw invalid("floor artwork may not change the playable bounds");
        centerX = center[0]; centerY = center[1];
        medallionDiameter = number(root, "medallionDiameter", 300, 1000);
        pathWidth = number(root, "pathWidth", 100, 300);
        materialRepeat = number(root, "materialRepeat", 100, 400);
        if (Math.abs(centerX) + medallionDiameter * .5f > ArenaWorld.HALF_WIDTH
            || Math.abs(centerY) + medallionDiameter * .5f > ArenaWorld.HALF_HEIGHT)
            throw invalid("medallion exceeds the floor");

        JsonValue authoredPaths = root.require("paths");
        if (!authoredPaths.isArray() || authoredPaths.size < 1 || authoredPaths.size > MAX_PATHS)
            throw invalid("bounded path count required");
        paths = new Path[authoredPaths.size];
        ArrayList<Cell> cells = new ArrayList<>(80);
        HashSet<String> ids = new HashSet<>();
        int index = 0;
        for (JsonValue entry = authoredPaths.child; entry != null; entry = entry.next) {
            String id = entry.getString("id");
            float[] end = pair(entry, "to");
            float dx = end[0] - centerX, dy = end[1] - centerY;
            float length = (float) Math.hypot(dx, dy);
            if (id.isBlank() || !ids.add(id) || entry.getBoolean("solid", false)
                || Math.abs(end[0]) > ArenaWorld.HALF_WIDTH || Math.abs(end[1]) > ArenaWorld.HALF_HEIGHT
                || length <= medallionDiameter * .5f || length > 3000)
                throw invalid("path id, endpoint or movement override");
            Path path = new Path(id, end[0], end[1], dx / length, dy / length, length);
            paths[index] = path;
            buildCells(cells, index++, path);
        }
        pathCells = cells.toArray(new Cell[0]);

        JsonValue authoredWear = root.require("wear");
        if (!authoredWear.isArray() || authoredWear.size > 16) throw invalid("bounded wear count required");
        wear = new Wear[authoredWear.size];
        index = 0;
        for (JsonValue entry = authoredWear.child; entry != null; entry = entry.next) {
            float[] at = pair(entry, "at");
            if (Math.abs(at[0]) > ArenaWorld.HALF_WIDTH || Math.abs(at[1]) > ArenaWorld.HALF_HEIGHT)
                throw invalid("wear centre outside the floor");
            wear[index++] = new Wear(at[0], at[1], number(entry, "radius", 180, 900),
                number(entry, "delta", -.06f, .04f));
        }
    }

    /** Quiet authored patches, continuous across cell boundaries; called only while building the floor cache. */
    public float shade(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw invalid("non-finite shade sample");
        float shade = 1f;
        for (Wear patch : wear) {
            float distance = (float) Math.hypot(x - patch.x, y - patch.y) / patch.radius;
            if (distance >= 1) continue;
            float smooth = distance * distance * (3 - 2 * distance);
            shade += patch.delta * (1 - smooth);
        }
        return Math.max(.94f, Math.min(1.03f, shade));
    }

    private void buildCells(ArrayList<Cell> result, int pathIndex, Path path) {
        float nx = -path.unitY * pathWidth * .5f, ny = path.unitX * pathWidth * .5f;
        int count = (int) Math.ceil(path.length / materialRepeat);
        for (int i = 0; i < count; i++) {
            float start = i * materialRepeat, end = Math.min(path.length, start + materialRepeat);
            float x0 = centerX + path.unitX * start, y0 = centerY + path.unitY * start;
            float x1 = centerX + path.unitX * end, y1 = centerY + path.unitY * end;
            float v1 = (end - start) / materialRepeat;
            float[] vertices = { x0 + nx, y0 + ny, 0, 0, x0 - nx, y0 - ny, 1, 0,
                x1 - nx, y1 - ny, 1, v1, x1 + nx, y1 + ny, 0, v1 };
            vertices = clip(vertices, 0, -ArenaWorld.HALF_WIDTH, true);
            vertices = clip(vertices, 0, ArenaWorld.HALF_WIDTH, false);
            vertices = clip(vertices, 1, -ArenaWorld.HALF_HEIGHT, true);
            vertices = clip(vertices, 1, ArenaWorld.HALF_HEIGHT, false);
            if (vertices.length < 12 || Math.abs(area(vertices)) < .01f) continue;
            if (vertices.length / 4 > MAX_CELL_VERTICES || result.size() >= MAX_CELLS)
                throw invalid("clipped path cell pool");
            result.add(new Cell(pathIndex, start, vertices));
        }
    }

    private static float[] clip(float[] polygon, int axis, float boundary, boolean keepGreater) {
        int count = polygon.length / 4;
        if (count == 0) return polygon;
        float[] output = new float[(count + 1) * 4];
        int size = 0, previous = (count - 1) * 4;
        for (int current = 0; current < polygon.length; current += 4) {
            float a = polygon[previous + axis], b = polygon[current + axis];
            boolean aInside = keepGreater ? a >= boundary : a <= boundary;
            boolean bInside = keepGreater ? b >= boundary : b <= boundary;
            if (aInside != bInside) {
                float fraction = (boundary - a) / (b - a);
                for (int component = 0; component < 4; component++)
                    output[size + component] = polygon[previous + component]
                        + (polygon[current + component] - polygon[previous + component]) * fraction;
                output[size + axis] = boundary;
                size += 4;
            }
            if (bInside) {
                System.arraycopy(polygon, current, output, size, 4);
                size += 4;
            }
            previous = current;
        }
        float[] trimmed = new float[size];
        System.arraycopy(output, 0, trimmed, 0, size);
        return trimmed;
    }

    private static float area(float[] polygon) {
        float area = 0;
        for (int i = 0, previous = polygon.length - 4; i < polygon.length; previous = i, i += 4)
            area += polygon[previous] * polygon[i + 1] - polygon[i] * polygon[previous + 1];
        return area * .5f;
    }

    private static float[] pair(JsonValue root, String key) {
        JsonValue values = root.require(key);
        if (!values.isArray() || values.size != 2) throw invalid(key);
        float x = values.getFloat(0), y = values.getFloat(1);
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw invalid(key);
        return new float[]{x, y};
    }

    private static float number(JsonValue root, String key, float minimum, float maximum) {
        float value = root.getFloat(key);
        if (!Float.isFinite(value) || value < minimum || value > maximum) throw invalid(key);
        return value;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Arena floor: " + reason);
    }
}
