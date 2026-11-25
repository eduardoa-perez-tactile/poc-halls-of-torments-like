package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;

/** One authored fixed north-wall gate, sliced solely for storage in existing managed gameplay pages. */
public final class PaintedGateArt {
    public static final int MAX_PIECES = 128, MAX_SCENERY = MAX_PIECES * 3;
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    // Stop inside the painted wing endcaps; the unchanged wall continues beneath this handoff.
    private static final float DRAW_LEFT = 36f, DRAW_RIGHT = 220f;
    private static final float FEATHER_WIDTH = 8f;
    public final String file;
    public final int sourceWidth, sourceHeight;
    public final float pivotX, pivotY, worldScale, renderShearY, worldX, worldY;
    public final Piece[] pieces;
    private final Texture[] pages;
    private final int[] pageWidths, pageHeights;

    public static final class Piece {
        public final int page, atlasX, atlasY, width, height, sourceX, sourceY, coreX, coreY, coreWidth, coreHeight;
        private Piece(JsonValue json, PaintedGateArt art) {
            page = json.getInt("page");
            int[] a = rect(json, "rect"), s = rect(json, "sourceRect"), c = rect(json, "coreRect");
            if (page < 0 || page >= art.pageWidths.length || !inside(a, art.pageWidths[page], art.pageHeights[page])
                || !inside(s, art.sourceWidth, art.sourceHeight) || !inside(c, art.sourceWidth, art.sourceHeight)
                || a[2] != s[2] || a[3] != s[3] || c[0] < s[0] || c[1] < s[1]
                || c[0] + c[2] > s[0] + s[2] || c[1] + c[3] > s[1] + s[3]) throw invalid("piece bounds");
            atlasX = a[0]; atlasY = a[1]; width = a[2]; height = a[3]; sourceX = s[0]; sourceY = s[1];
            coreX = c[0]; coreY = c[1]; coreWidth = c[2]; coreHeight = c[3];
        }
    }

    public PaintedGateArt(JsonValue root, GameplayAtlas atlas, Texture[] pages) {
        if (root.getInt("version") != 1 || root.getLong("addedTextureBytes") != 0 || root.getInt("context") != 1)
            throw invalid("version or residency contract");
        JsonValue stored = root.require("pages");
        if (!stored.isArray() || stored.size < 3 || stored.size > 4 || stored.size > atlas.pages.length) throw invalid("borrowed pages");
        pageWidths = new int[stored.size]; pageHeights = new int[stored.size];
        for (int i = 0; i < stored.size; i++) {
            JsonValue p = stored.get(i); GameplayAtlas.Page expected = atlas.pages[i];
            if (!expected.file().equals(p.getString("file")) || expected.width() != p.get("size").getInt(0)
                || expected.height() != p.get("size").getInt(1)) throw invalid("borrowed page identity");
            pageWidths[i] = expected.width(); pageHeights[i] = expected.height();
        }
        JsonValue gate = root.require("gate"), size = gate.require("sourceSize"), pivot = gate.require("pivot");
        if (size.size != 2 || pivot.size != 2) throw invalid("source geometry");
        file = gate.getString("file"); sourceWidth = size.getInt(0); sourceHeight = size.getInt(1);
        pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1); worldScale = gate.getFloat("worldScale");
        renderShearY = gate.getFloat("renderShearY", 0);
        JsonValue at = gate.require("worldPosition");
        if (at.size != 2) throw invalid("fixed ground root");
        worldX = at.getFloat(0); worldY = at.getFloat(1);
        if (!file.matches("[a-zA-Z0-9_-]+\\.png") || sourceWidth < 1 || sourceWidth > 512 || sourceHeight < 1 || sourceHeight > 512
            || !finite(pivotX, 0, sourceWidth) || !finite(pivotY, 0, sourceHeight) || !finite(worldScale, .5f, 4f)
            || !finite(renderShearY, -.15f, .15f) || worldX != 0 || worldY != 912
            || !gate.getString("orientation", "north-wall-x").equals("north-wall-x")) throw invalid("fixed gate geometry");
        JsonValue parts = gate.require("pieces");
        if (!parts.isArray() || parts.size < 1 || parts.size > MAX_PIECES) throw invalid("piece pool");
        pieces = new Piece[parts.size]; int index = 0;
        for (JsonValue part = parts.child; part != null; part = part.next) {
            Piece piece = new Piece(part, this);
            for (int j = 0; j < index; j++) {
                Piece old = pieces[j];
                if (piece.coreX < old.coreX + old.coreWidth && piece.coreX + piece.coreWidth > old.coreX
                    && piece.coreY < old.coreY + old.coreHeight && piece.coreY + piece.coreHeight > old.coreY)
                    throw invalid("overlapping source cores");
            }
            pieces[index++] = piece;
        }
        this.pages = pages;
    }

    public static boolean matchesGateProp(ArenaLayout.Placement placement) { return placement.region.equals("sealed-gate"); }
    public Scene place(ArenaLayout layout) { return new Scene(layout); }

    public final class Scene {
        public final Quad[] quads;
        private Scene(ArenaLayout layout) {
            int found = 0;
            for (ArenaLayout.Placement p : layout.placements) if (matchesGateProp(p)) {
                if (Math.abs(p.x - worldX) > .01f || Math.abs(p.y - worldY) > .01f) throw invalid("layout moved fixed gate");
                found++;
            }
            if (found != 1) throw invalid("one fixed gate required");
            ArrayList<Quad> output = new ArrayList<>();
            for (Piece piece : pieces) {
                float left = Math.max(piece.coreX, DRAW_LEFT), right = Math.min(piece.coreX + piece.coreWidth, DRAW_RIGHT);
                if (right <= left) continue;
                // Isolate the two fade bands so interpolation never dims the central arch.
                if (left < DRAW_LEFT + FEATHER_WIDTH && right > DRAW_LEFT + FEATHER_WIDTH) {
                    output.add(new Quad(piece, left, DRAW_LEFT + FEATHER_WIDTH)); left = DRAW_LEFT + FEATHER_WIDTH;
                }
                if (left < DRAW_RIGHT - FEATHER_WIDTH && right > DRAW_RIGHT - FEATHER_WIDTH) {
                    output.add(new Quad(piece, left, DRAW_RIGHT - FEATHER_WIDTH)); left = DRAW_RIGHT - FEATHER_WIDTH;
                }
                output.add(new Quad(piece, left, right));
            }
            if (output.size() > MAX_SCENERY) throw invalid("placed gate pool");
            quads = output.toArray(new Quad[0]);
            Arrays.sort(quads, (a, b) -> Integer.compare(a.piece.page, b.piece.page));
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

    public final class Quad {
        public final Piece piece;
        public final float[] vertices = new float[20];
        public final float worldX = PaintedGateArt.this.worldX, worldY = PaintedGateArt.this.worldY, sortDepth = 0;
        public final int renderLayer = PaintedBorderArt.BACK_JOIN_LAYER;
        public final float sourceLeft, sourceRight;
        public final float minX, minY, maxX, maxY;
        private Quad(Piece piece, float sourceLeft, float sourceRight) {
            this.piece = piece;
            this.sourceLeft = sourceLeft; this.sourceRight = sourceRight;
            float rootX = (worldX - worldY) * ISO_X, rootY = (worldX + worldY) * ISO_Y;
            float loX = Float.POSITIVE_INFINITY, loY = loX, hiX = Float.NEGATIVE_INFINITY, hiY = hiX;
            for (int v = 0; v < 4; v++) {
                int at = v * 5;
                float sx = v >= 2 ? sourceRight : sourceLeft, sy = piece.coreY + (v == 0 || v == 3 ? piece.coreHeight : 0);
                float dx = (sx - pivotX) * worldScale;
                vertices[at] = rootX + dx; vertices[at + 1] = rootY + (pivotY - sy) * worldScale + dx * renderShearY;
                float alpha = Math.min(1f, Math.min((sx - DRAW_LEFT) / FEATHER_WIDTH, (DRAW_RIGHT - sx) / FEATHER_WIDTH));
                vertices[at + 2] = Color.toFloatBits(1, 1, 1, alpha);
                vertices[at + 3] = (piece.atlasX + sx - piece.sourceX) / pageWidths[piece.page];
                vertices[at + 4] = (piece.atlasY + sy - piece.sourceY) / pageHeights[piece.page];
                loX = Math.min(loX, vertices[at]); hiX = Math.max(hiX, vertices[at]); loY = Math.min(loY, vertices[at + 1]); hiY = Math.max(hiY, vertices[at + 1]);
            }
            minX = loX; minY = loY; maxX = hiX; maxY = hiY;
        }
    }
    private static boolean finite(float n, float lo, float hi) { return Float.isFinite(n) && n >= lo && n <= hi; }
    private static int[] rect(JsonValue j, String key) { JsonValue a = j.require(key); if (a.size != 4) throw invalid(key); return new int[]{a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3)}; }
    private static boolean inside(int[] r, int w, int h) { return r[0] >= 0 && r[1] >= 0 && r[2] > 0 && r[3] > 0 && r[0] <= w - r[2] && r[1] <= h - r[3]; }
    private static IllegalArgumentException invalid(String why) { return new IllegalArgumentException("Invalid painted gate: " + why); }
}
