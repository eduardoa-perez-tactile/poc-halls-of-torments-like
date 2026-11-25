package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;

/** Whole painted wall runs, split only for storage in already resident atlas pages. */
public final class PaintedBorderArt {
    public static final int MAX_PIECES = 256, MAX_SCENERY = 512;
    public static final int CORNER_LAYER = 0, BACK_WALL_LAYER = 1, BACK_JOIN_LAYER = 2,
        INTERIOR_LAYER = 3, FRONT_WALL_LAYER = 4, FRONT_JOIN_LAYER = 5;
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    public final Painting x, y, corner;
    private final Texture[] textures;

    public static final class Piece {
        public final int page, atlasX, atlasY, width, height, sourceX, sourceY;
        public final int coreX, coreY, coreWidth, coreHeight;
        private Piece(JsonValue json, GameplayAtlas atlas, int sourceWidth, int sourceHeight) {
            page = json.getInt("page");
            int[] rect = rect(json, "rect"), source = rect(json, "sourceRect"), core = rect(json, "coreRect");
            if (page < 0 || page >= atlas.pages.length) throw invalid("piece page");
            var p = atlas.pages[page];
            if (!inside(rect, p.width(), p.height()) || !inside(source, sourceWidth, sourceHeight)
                || !inside(core, sourceWidth, sourceHeight) || rect[2] != source[2] || rect[3] != source[3]
                || core[0] < source[0] || core[1] < source[1]
                || core[0] + core[2] > source[0] + source[2] || core[1] + core[3] > source[1] + source[3])
                throw invalid("piece rectangles");
            atlasX = rect[0]; atlasY = rect[1]; width = rect[2]; height = rect[3];
            sourceX = source[0]; sourceY = source[1];
            coreX = core[0]; coreY = core[1]; coreWidth = core[2]; coreHeight = core[3];
        }
    }

    public static final class Painting {
        public final String name, file;
        public final int sourceWidth, sourceHeight;
        public final float pivotX, pivotY, worldLength, worldThickness, scale, shear, footprintWidth;
        public final Piece[] pieces;
        private Painting(String name, JsonValue json, GameplayAtlas atlas) {
            this.name = name; file = json.getString("file");
            if (!file.matches("[a-zA-Z0-9_-]+\\.png")) throw invalid("source file");
            JsonValue size = json.require("sourceSize"), pivot = json.require("pivot"), footprint = json.require("footprintWorld");
            if (size.size != 2 || pivot.size != 2 || footprint.size != 2) throw invalid("painting geometry");
            sourceWidth = size.getInt(0); sourceHeight = size.getInt(1);
            pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1);
            worldLength = json.getFloat("worldLength", Math.max(footprint.getFloat(0), footprint.getFloat(1)));
            worldThickness = json.getFloat("worldThickness", Math.min(footprint.getFloat(0), footprint.getFloat(1)));
            float width = json.getFloat("projectedFootprintWidth");
            footprintWidth = width;
            shear = json.getFloat("renderShearY", 0f);
            if (sourceWidth < 1 || sourceWidth > 4096 || sourceHeight < 1 || sourceHeight > 4096
                || !finite(pivotX, 0, sourceWidth) || !finite(pivotY, 0, sourceHeight)
                || !finite(worldLength, 16, 2600) || !finite(worldThickness, 16, 2600)
                || !finite(width, 1, sourceWidth) || !finite(shear, -.15f, .15f)) throw invalid("painting bounds");
            scale = (worldLength + worldThickness) * ISO_X / width;
            JsonValue parts = json.require("pieces");
            if (!parts.isArray() || parts.size < 1 || parts.size > MAX_PIECES) throw invalid("painting piece count");
            pieces = new Piece[parts.size];
            int i = 0;
            for (JsonValue part = parts.child; part != null; part = part.next) {
                Piece piece = new Piece(part, atlas, sourceWidth, sourceHeight);
                for (int j = 0; j < i; j++) {
                    Piece previous = pieces[j];
                    if (piece.coreX < previous.coreX + previous.coreWidth && piece.coreX + piece.coreWidth > previous.coreX
                        && piece.coreY < previous.coreY + previous.coreHeight && piece.coreY + piece.coreHeight > previous.coreY)
                        throw invalid("overlapping painting cores");
                }
                pieces[i++] = piece;
            }
        }
    }

    PaintedBorderArt(Texture[] textures, GameplayAtlas atlas) {
        this(new JsonReader().parse(Gdx.files.internal("art/painted-borders.json")), atlas, textures);
    }

    public PaintedBorderArt(JsonValue root, GameplayAtlas atlas, Texture[] textures) {
        if (root.getInt("version") != 1 || root.getLong("addedTextureBytes") != 0
            || root.getInt("context") != 1) throw invalid("version or residency contract");
        JsonValue pages = root.require("pages");
        // Border storage stays on its original three pages; later actor pages are independent.
        if (pages.size != 3 || atlas.pages.length < 3) throw invalid("shared atlas pages");
        for (int i = 0; i < pages.size; i++) {
            var p = atlas.pages[i]; var s = pages.get(i);
            if (!s.getString("file").equals(p.file()) || s.get("size").getInt(0) != p.width()
                || s.get("size").getInt(1) != p.height()) throw invalid("shared page mismatch");
        }
        JsonValue paintings = root.require("paintings");
        x = new Painting("x", paintings.require("x"), atlas);
        y = new Painting("y", paintings.require("y"), atlas);
        corner = new Painting("corner", paintings.require("corner"), atlas);
        if (x.pieces.length + y.pieces.length + corner.pieces.length > MAX_PIECES) throw invalid("piece pool");
        this.textures = textures;
    }

    public Scene place(ArenaLayout layout, GameplayAtlas atlas) { return new Scene(layout, atlas); }

    public final class Scene {
        public final Quad[] quads;
        private Scene(ArenaLayout layout, GameplayAtlas atlas) {
            ArrayList<Quad> result = new ArrayList<>();
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            float minCenterX = Float.POSITIVE_INFINITY, maxCenterX = Float.NEGATIVE_INFINITY;
            float minCenterY = Float.POSITIVE_INFINITY, maxCenterY = Float.NEGATIVE_INFINITY;
            for (var run : layout.wallRuns) {
                if (run.alongX()) {
                    minCenterY = Math.min(minCenterY, run.y()); maxCenterY = Math.max(maxCenterY, run.y());
                    minY = Math.min(minY, run.y() - run.thickness() * .5f);
                    maxY = Math.max(maxY, run.y() + run.thickness() * .5f);
                } else {
                    minCenterX = Math.min(minCenterX, run.x()); maxCenterX = Math.max(maxCenterX, run.x());
                    minX = Math.min(minX, run.x() - run.thickness() * .5f);
                    maxX = Math.max(maxX, run.x() + run.thickness() * .5f);
                }
            }
            for (var run : layout.wallRuns) {
                Painting painting = run.alongX() ? x : y;
                if (Math.abs(run.length() - painting.worldLength) > .1f
                    || Math.abs(run.thickness() - painting.worldThickness) > .1f) throw invalid("wall footprint differs from painting");
                // The span already includes both adjoining half-thicknesses. Adding this
                // painting's thickness again grows both wall ends past their junctions.
                float span = run.alongX() ? maxX - minX : maxY - minY;
                float drawScale = painting.scale * span
                    / (painting.worldLength + painting.worldThickness);
                float lowDepth = (run.alongX() ? run.y() + minCenterX : run.x() + minCenterY) * ISO_Y;
                float highDepth = (run.alongX() ? run.y() + maxCenterX : run.x() + maxCenterY) * ISO_Y;
                int layer = (run.alongX() ? run.y() : run.x()) > 0 ? BACK_WALL_LAYER : FRONT_WALL_LAYER;
                add(result, painting, run.x(), run.y(), run.alongX(), false, drawScale, lowDepth, highDepth,
                    layer, atlas);
            }
            int corners = 0;
            // The old 50x50 support is backing for a 36x36 junction, not a taller
            // free-standing corner post. Keep its proportions while fitting that footprint.
            float cornerScale = corner.scale * Math.min(x.worldThickness, y.worldThickness) / corner.worldThickness;
            for (var p : layout.placements) if (p.wall && p.region.equals("buttress")) {
                add(result, corner, p.x, p.y, true, true, cornerScale, 0f, 0f, CORNER_LAYER, atlas); corners++;
            }
            if (corners != 4 || result.size() > MAX_SCENERY) throw invalid("corner or scenery pool");
            quads = result.toArray(new Quad[0]);
        }
        private void add(ArrayList<Quad> result, Painting painting, float rootX, float rootY,
                         boolean alongX, boolean fixedDepth, float drawScale, float lowDepth, float highDepth,
                         int layer, GameplayAtlas atlas) {
            for (Piece piece : painting.pieces) {
                result.add(new Quad(painting, piece, rootX, rootY, alongX, fixedDepth, drawScale, lowDepth, highDepth, layer, atlas));
            }
        }
        public void draw(SpriteBatch batch, int index) {
            draw(batch, index, quads[index].vertices);
        }
        public void draw(SpriteBatch batch, int index, float[] cachedVertices) {
            batch.draw(textures[quads[index].piece.page], cachedVertices, 0, cachedVertices.length);
        }
        public boolean visible(OrthographicCamera camera, int index) {
            Quad q = quads[index];
            return q.maxX >= camera.position.x - camera.viewportWidth * .5f
                && q.minX <= camera.position.x + camera.viewportWidth * .5f
                && q.maxY >= camera.position.y - camera.viewportHeight * .5f
                && q.minY <= camera.position.y + camera.viewportHeight * .5f;
        }
    }

    public static final class Quad {
        public final Painting painting;
        public final Piece piece;
        /** Complete boundary planes sort around the interior, never by arbitrary atlas column centres. */
        public final int renderLayer;
        public final float worldX, worldY, sortDepth, drawScale, minX, minY, maxX, maxY;
        public final float sourceLeft, sourceRight;
        public final float[] vertices = new float[20];
        private Quad(Painting painting, Piece piece, float rootX, float rootY, boolean alongX,
                     boolean fixedDepth, float drawScale, float lowDepth, float highDepth,
                     int layer, GameplayAtlas atlas) {
            this.painting = painting; this.piece = piece;
            renderLayer = layer;
            this.drawScale = drawScale;
            sourceLeft = piece.coreX; sourceRight = piece.coreX + piece.coreWidth;
            float sourceCenterX = (sourceLeft + sourceRight) * .5f;
            float along = fixedDepth ? 0 : (sourceCenterX - painting.pivotX) * drawScale / ISO_X;
            worldX = rootX + (alongX ? along : 0); worldY = rootY + (alongX ? 0 : -along);
            // The small corner painting is backing for the meeting wall ends, not a foreground post.
            float depth = (worldX + worldY) * ISO_Y;
            float startAlong = (sourceLeft - painting.pivotX) * drawScale / ISO_X;
            float endAlong = (sourceRight - painting.pivotX) * drawScale / ISO_X;
            float minAlong = alongX ? startAlong : -endAlong;
            float maxAlong = alongX ? endAlong : -startAlong;
            // Assign the complete overlapping end region, not a storage rectangle's
            // centre: a narrower final atlas column must not reverse the wall faces.
            float endRegion = painting.worldLength * .5f - 150f;
            if (!fixedDepth && (minAlong < -endRegion || maxAlong > endRegion))
                depth = (minAlong < -endRegion ? lowDepth : highDepth) + (alongX ? -1f : 1f);
            depth += fixedDepth ? (painting.worldLength + painting.worldThickness) * ISO_Y : 0f;
            float rootScreenX = (rootX - rootY) * ISO_X, rootScreenY = (rootX + rootY) * ISO_Y;
            vertex(0, sourceLeft, piece.coreY + piece.coreHeight, rootScreenX, rootScreenY, atlas);
            vertex(5, sourceLeft, piece.coreY, rootScreenX, rootScreenY, atlas);
            vertex(10, sourceRight, piece.coreY, rootScreenX, rootScreenY, atlas);
            vertex(15, sourceRight, piece.coreY + piece.coreHeight, rootScreenX, rootScreenY, atlas);
            minX = Math.min(vertices[0], vertices[15]); maxX = Math.max(vertices[0], vertices[15]);
            minY = Math.min(vertices[1], vertices[16]); maxY = Math.max(vertices[6], vertices[11]);
            sortDepth = depth;
        }
        private void vertex(int offset, float sx, float sy, float rootX, float rootY, GameplayAtlas atlas) {
            Painting p = painting; Piece s = piece; var page = atlas.pages[s.page];
            float dx = (sx - p.pivotX) * drawScale;
            vertices[offset] = rootX + dx;
            vertices[offset + 1] = rootY + (p.pivotY - sy) * drawScale + dx * p.shear;
            vertices[offset + 2] = Color.WHITE_FLOAT_BITS;
            vertices[offset + 3] = (s.atlasX + sx - s.sourceX) / page.width();
            vertices[offset + 4] = (s.atlasY + sy - s.sourceY) / page.height();
        }
    }

    private static boolean finite(float value, float low, float high) { return Float.isFinite(value) && value >= low && value <= high; }
    private static int[] rect(JsonValue json, String name) {
        JsonValue r = json.require(name);
        if (!r.isArray() || r.size != 4) throw invalid(name);
        int[] a = {r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3)};
        if (a[0] < 0 || a[1] < 0 || a[2] < 1 || a[3] < 1) throw invalid(name);
        return a;
    }
    private static boolean inside(int[] r, int width, int height) { return r[0] <= width - r[2] && r[1] <= height - r[3]; }
    private static IllegalArgumentException invalid(String why) { return new IllegalArgumentException("Invalid painted border: " + why); }
}
