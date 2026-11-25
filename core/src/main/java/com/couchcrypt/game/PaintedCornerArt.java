package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;

/** Four authored wall wraps borrowing resident pages; every cut and UV is cached at startup. */
public final class PaintedCornerArt {
    public static final int MAX_PIECES = 128, MAX_SCENERY = 384;
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    // Keep an opaque underlap before the generated arm ends, rather than cutting at their alpha edge.
    private static final float HANDOFF = 72f, SPLIT_HEIGHT = 100f;
    private static final float[] HIDDEN = new float[0];
    public final Corner[] corners = new Corner[4];
    private final Texture[] pages;

    public static final class Piece {
        public final int page, atlasX, atlasY, width, height, sourceX, sourceY, coreX, coreY, coreWidth, coreHeight;
        private Piece(JsonValue json, GameplayAtlas atlas, int sourceWidth, int sourceHeight) {
            page = json.getInt("page");
            int[] a = rect(json, "rect"), s = rect(json, "sourceRect"), c = rect(json, "coreRect");
            if (page < 0 || page >= atlas.pages.length || !inside(a, atlas.pages[page].width(), atlas.pages[page].height())
                || !inside(s, sourceWidth, sourceHeight) || !inside(c, sourceWidth, sourceHeight)
                || a[2] != s[2] || a[3] != s[3] || c[0] < s[0] || c[1] < s[1]
                || c[0] + c[2] > s[0] + s[2] || c[1] + c[3] > s[1] + s[3]) throw invalid("piece bounds");
            atlasX = a[0]; atlasY = a[1]; width = a[2]; height = a[3]; sourceX = s[0]; sourceY = s[1];
            coreX = c[0]; coreY = c[1]; coreWidth = c[2]; coreHeight = c[3];
        }
    }

    public static final class Corner {
        public final String key;
        public final int sourceWidth, sourceHeight;
        public final float pivotX, pivotY, scale;
        public final Piece[] pieces;
        private Corner(String key, JsonValue json, GameplayAtlas atlas) {
            this.key = key;
            JsonValue size = json.require("sourceSize"), pivot = json.require("pivot");
            if (size.size != 2 || pivot.size != 2) throw invalid("source geometry");
            sourceWidth = size.getInt(0); sourceHeight = size.getInt(1);
            pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1); scale = json.getFloat("worldScale");
            if (sourceWidth < 1 || sourceWidth > 512 || sourceHeight < 1 || sourceHeight > 512
                || !finite(pivotX, 0, sourceWidth) || !finite(pivotY, 0, sourceHeight)
                || !finite(scale, .5f, 4f)) throw invalid("source geometry bounds");
            JsonValue parts = json.require("pieces");
            if (!parts.isArray() || parts.size < 1 || parts.size > MAX_PIECES) throw invalid("piece count");
            pieces = new Piece[parts.size]; int i = 0;
            for (JsonValue part = parts.child; part != null; part = part.next) {
                Piece piece = new Piece(part, atlas, sourceWidth, sourceHeight);
                for (int j = 0; j < i; j++) {
                    Piece old = pieces[j];
                    if (piece.coreX < old.coreX + old.coreWidth && piece.coreX + piece.coreWidth > old.coreX
                        && piece.coreY < old.coreY + old.coreHeight && piece.coreY + piece.coreHeight > old.coreY)
                        throw invalid("overlapping source cores");
                }
                pieces[i++] = piece;
            }
        }
    }

    public PaintedCornerArt(JsonValue root, GameplayAtlas atlas, Texture[] pages) {
        if (root.getInt("version") != 1 || root.getLong("addedTextureBytes") != 0 || root.getInt("context") != 1)
            throw invalid("version or residency contract");
        JsonValue storedPages = root.require("pages");
        if (!storedPages.isArray() || storedPages.size < 3 || storedPages.size > 4 || storedPages.size > atlas.pages.length)
            throw invalid("borrowed pages");
        for (int i = 0; i < storedPages.size; i++) {
            JsonValue page = storedPages.get(i); GameplayAtlas.Page expected = atlas.pages[i];
            if (!expected.file().equals(page.getString("file")) || page.get("size").getInt(0) != expected.width()
                || page.get("size").getInt(1) != expected.height()) throw invalid("borrowed page identity");
            pageWidths[i] = expected.width(); pageHeights[i] = expected.height();
        }
        String[] keys = {"ne", "nw", "se", "sw"};
        JsonValue paints = root.require("corners");
        if (paints.size != keys.length) throw invalid("four directional joins required");
        int count = 0;
        for (int i = 0; i < keys.length; i++) {
            corners[i] = new Corner(keys[i], paints.require(keys[i]), atlas); count += corners[i].pieces.length;
            for (Piece piece : corners[i].pieces) if (piece.page >= storedPages.size) throw invalid("unlisted borrowed page");
        }
        if (count > MAX_PIECES) throw invalid("combined piece pool");
        this.pages = pages;
    }

    public Scene place(ArenaLayout layout, PaintedBorderArt.Scene walls) { return new Scene(layout, walls); }

    public final class Scene {
        public final Quad[] quads;
        /** null keeps the original wall; empty hides it; otherwise cached affine sections replace it. */
        public final float[][] wallVertices;
        private Scene(ArenaLayout layout, PaintedBorderArt.Scene walls) {
            ArrayList<Quad> output = new ArrayList<>();
            wallVertices = new float[walls.quads.length][];
            for (int i = 0; i < walls.quads.length; i++)
                if (walls.quads[i].painting.name.equals("corner")) wallVertices[i] = HIDDEN;
            int joins = 0;
            for (ArenaLayout.Placement placement : layout.placements) {
                if (!placement.wall || !placement.region.equals("buttress")) continue;
                int cornerIndex = placement.y > 0 ? (placement.x > 0 ? 0 : 1) : (placement.x > 0 ? 2 : 3);
                Corner corner = corners[cornerIndex];
                float rootX = (placement.x - placement.y) * ISO_X, rootY = (placement.x + placement.y) * ISO_Y;
                for (Piece piece : corner.pieces) {
                    float[] vertices = vertices(corner, piece, rootX, rootY);
                    if (cornerIndex == 0 || cornerIndex == 3)
                        output.add(new Quad(corner, piece, vertices, placement.x, placement.y,
                            cornerIndex == 0 ? PaintedBorderArt.BACK_JOIN_LAYER : PaintedBorderArt.FRONT_JOIN_LAYER));
                    else {
                        float slope = cornerIndex == 1 ? -.5f : .5f;
                        addSplit(output, corner, piece, vertices, placement.x, placement.y, rootX, rootY, slope, true);
                        addSplit(output, corner, piece, vertices, placement.x, placement.y, rootX, rootY, slope, false);
                    }
                }
                for (int i = 0; i < walls.quads.length; i++) {
                    PaintedBorderArt.Quad wall = walls.quads[i];
                    boolean alongX = wall.painting.name.equals("x");
                    if (!alongX && !wall.painting.name.equals("y")) continue;
                    if (alongX ? Math.abs(wall.worldY - placement.y) > .01f : Math.abs(wall.worldX - placement.x) > .01f) continue;
                    boolean leftEnd = alongX ? placement.x < 0 : placement.y > 0;
                    float boundary = rootX + (leftEnd ? HANDOFF : -HANDOFF);
                    float[] original = wallVertices[i] == null ? wall.vertices : wallVertices[i];
                    float[] cut = cutWall(original, boundary, !leftEnd);
                    if (cut != original) wallVertices[i] = cut;
                }
                joins++;
            }
            if (joins != 4 || output.size() > MAX_SCENERY) throw invalid("placed join pool");
            output.sort((a, b) -> {
                int layer = Integer.compare(a.renderLayer, b.renderLayer);
                return layer != 0 ? layer : Integer.compare(a.piece.page, b.piece.page);
            });
            quads = output.toArray(new Quad[0]);
        }
        public void draw(SpriteBatch batch, int index) {
            Quad q = quads[index]; batch.draw(pages[q.piece.page], q.vertices, 0, q.vertices.length);
        }
        public boolean visible(OrthographicCamera camera, int index) {
            Quad q = quads[index];
            return q.maxX >= camera.position.x - camera.viewportWidth * .5f && q.minX <= camera.position.x + camera.viewportWidth * .5f
                && q.maxY >= camera.position.y - camera.viewportHeight * .5f && q.minY <= camera.position.y + camera.viewportHeight * .5f;
        }
    }

    public static final class Quad {
        public final Corner corner;
        public final Piece piece;
        public final float[] vertices;
        public final float worldX, worldY, sortDepth, minX, minY, maxX, maxY;
        public final int renderLayer;
        private Quad(Corner corner, Piece piece, float[] vertices, float x, float y, int layer) {
            this.corner = corner; this.piece = piece; this.vertices = vertices;
            worldX = x; worldY = y; sortDepth = 0; renderLayer = layer;
            float loX = Float.POSITIVE_INFINITY, loY = loX, hiX = Float.NEGATIVE_INFINITY, hiY = hiX;
            for (int i = 0; i < vertices.length; i += 5) { loX = Math.min(loX, vertices[i]); hiX = Math.max(hiX, vertices[i]); loY = Math.min(loY, vertices[i + 1]); hiY = Math.max(hiY, vertices[i + 1]); }
            minX = loX; minY = loY; maxX = hiX; maxY = hiY;
        }
    }

    private float[] vertices(Corner corner, Piece piece, float x, float y) {
        float[] out = new float[20];
        for (int v = 0; v < 4; v++) {
            int at = v * 5;
            float sx = piece.coreX + (v >= 2 ? piece.coreWidth : 0), sy = piece.coreY + (v == 0 || v == 3 ? piece.coreHeight : 0);
            out[at] = x + (sx - corner.pivotX) * corner.scale; out[at + 1] = y + (corner.pivotY - sy) * corner.scale;
            out[at + 2] = Color.WHITE_FLOAT_BITS;
            // Pages are borrowed in unchanged gameplay-bank order, including the existing skull page.
            out[at + 3] = (piece.atlasX + sx - piece.sourceX) / pageWidth(piece.page);
            out[at + 4] = (piece.atlasY + sy - piece.sourceY) / pageHeight(piece.page);
        }
        return out;
    }
    private final int[] pageWidths = new int[4], pageHeights = new int[4];
    private float pageWidth(int page) { return pageWidths[page]; }
    private float pageHeight(int page) { return pageHeights[page]; }

    private static void addSplit(ArrayList<Quad> result, Corner corner, Piece piece, float[] input,
                                 float x, float y, float rootX, float rootY, float slope, boolean back) {
        float[] clipped = clip(input, rootX, rootY + SPLIT_HEIGHT, slope, back);
        if (clipped.length == 0) return;
        if (clipped.length == 20) { result.add(new Quad(corner, piece, clipped, x, y,
            back ? PaintedBorderArt.BACK_JOIN_LAYER : PaintedBorderArt.FRONT_JOIN_LAYER)); return; }
        int count = clipped.length / 5;
        for (int i = 1; i < count - 1; i++) {
            float[] triangle = new float[20];
            System.arraycopy(clipped, 0, triangle, 0, 5); System.arraycopy(clipped, i * 5, triangle, 5, 5);
            System.arraycopy(clipped, (i + 1) * 5, triangle, 10, 5); System.arraycopy(triangle, 10, triangle, 15, 5);
            result.add(new Quad(corner, piece, triangle, x, y, back ? PaintedBorderArt.BACK_JOIN_LAYER : PaintedBorderArt.FRONT_JOIN_LAYER));
        }
    }
    private static float[] clip(float[] input, float rootX, float splitY, float slope, boolean above) {
        float[] output = new float[30]; int count = 0, n = input.length / 5;
        for (int i = 0; i < n; i++) {
            int a = i * 5, b = ((i + 1) % n) * 5;
            float da = input[a + 1] - splitY - slope * (input[a] - rootX);
            float db = input[b + 1] - splitY - slope * (input[b] - rootX);
            boolean insideA = above ? da >= 0 : da <= 0, insideB = above ? db >= 0 : db <= 0;
            if (insideA) { System.arraycopy(input, a, output, count * 5, 5); count++; }
            if (insideA != insideB) { float t = da / (da - db); for (int k = 0; k < 5; k++) output[count * 5 + k] = k == 2 ? input[a + k] : input[a + k] + (input[b + k] - input[a + k]) * t; count++; }
        }
        return count < 3 ? HIDDEN : Arrays.copyOf(output, count * 5);
    }
    private static float[] cutWall(float[] original, float boundary, boolean keepLeft) {
        if (original.length == 0) return HIDDEN;
        float left = original[0], right = original[15];
        if (keepLeft ? right <= boundary : left >= boundary) return original;
        if (keepLeft ? left >= boundary : right <= boundary) return HIDDEN;
        float lo = keepLeft ? left : boundary, hi = keepLeft ? boundary : right;
        float[] cut = original.clone();
        for (int v = 0; v < 4; v++) {
            float t = ((v < 2 ? lo : hi) - left) / (right - left);
            int a = v == 0 || v == 3 ? 0 : 5, b = v == 0 || v == 3 ? 15 : 10, at = v * 5;
            for (int k = 0; k < 5; k++) cut[at + k] = k == 2 ? original[a + k] : original[a + k] + (original[b + k] - original[a + k]) * t;
        }
        return cut;
    }
    private static boolean finite(float n, float lo, float hi) { return Float.isFinite(n) && n >= lo && n <= hi; }
    private static int[] rect(JsonValue j, String key) { JsonValue a = j.require(key); if (a.size != 4) throw invalid(key); return new int[]{a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3)}; }
    private static boolean inside(int[] r, int w, int h) { return r[0] >= 0 && r[1] >= 0 && r[2] > 0 && r[3] > 0 && r[0] <= w - r[2] && r[1] <= h - r[3]; }
    private static IllegalArgumentException invalid(String why) { return new IllegalArgumentException("Invalid painted corner: " + why); }
}
