package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteCache;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Comparator;

/** Painted ground materials borrow already resident pages. Geometry is assembled only at startup. */
public final class EngravedFloorArt {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    public static final int MAX_PIECES = 512, MAX_FLOOR_QUADS = 8000;
    public final Painting field, path, medallion;
    private final Texture[] textures;
    private final int[] pageWidths, pageHeights;

    public static final class Piece {
        public final int page, x, y, width, height, sourceX, sourceY, coreX, coreY, coreWidth, coreHeight;
        Piece(JsonValue json, EngravedFloorArt art, int sw, int sh) {
            page = json.getInt("page");
            if (page < 0 || page >= art.pageWidths.length) throw invalid("piece page");
            int[] r = rectangle(json, "rect"), s = rectangle(json, "sourceRect"), c = rectangle(json, "coreRect");
            if (!inside(r, art.pageWidths[page], art.pageHeights[page]) || !inside(s, sw, sh)
                || !inside(c, sw, sh) || r[2] != s[2] || r[3] != s[3]
                || c[0] < s[0] || c[1] < s[1] || c[0] + c[2] > s[0] + s[2] || c[1] + c[3] > s[1] + s[3])
                throw invalid("piece rectangle");
            x = r[0]; y = r[1]; width = r[2]; height = r[3]; sourceX = s[0]; sourceY = s[1];
            coreX = c[0]; coreY = c[1]; coreWidth = c[2]; coreHeight = c[3];
        }
    }

    public static final class Painting {
        public final String name, file;
        public final int width, height, contentX, contentY, contentWidth, contentHeight;
        public final Piece[] pieces;
        Painting(String name, JsonValue json, EngravedFloorArt art) {
            this.name = name; file = json.getString("file");
            int[] size = array(json, "sourceSize", 2);
            width = size[0]; height = size[1];
            int[] content = json.has("contentRect") ? rectangle(json, "contentRect") : new int[]{0, 0, width, height};
            if (!file.matches("[A-Za-z0-9_-]+\\.png") || width > 1024 || height > 1024 || !inside(content, width, height))
                throw invalid("painting dimensions");
            contentX = content[0]; contentY = content[1]; contentWidth = content[2]; contentHeight = content[3];
            JsonValue list = json.require("pieces");
            if (!list.isArray() || list.size < 1 || list.size > MAX_PIECES) throw invalid("painting pieces");
            pieces = new Piece[list.size]; int n = 0;
            for (JsonValue entry = list.child; entry != null; entry = entry.next) {
                Piece p = new Piece(entry, art, width, height);
                for (int i = 0; i < n; i++) {
                    Piece q = pieces[i];
                    if (p.coreX < q.coreX + q.coreWidth && p.coreX + p.coreWidth > q.coreX
                        && p.coreY < q.coreY + q.coreHeight && p.coreY + p.coreHeight > q.coreY)
                        throw invalid("overlapping cores");
                }
                pieces[n++] = p;
            }
        }
    }

    public EngravedFloorArt(JsonValue root, GameplayAtlas atlas, Texture[] textures) {
        if (root.getInt("version") != 1 || root.getLong("addedTextureBytes") != 0 || root.getInt("context") != 1)
            throw invalid("shared residency contract");
        JsonValue pages = root.require("pages");
        if (pages.size != atlas.pages.length + 1) throw invalid("page count");
        pageWidths = new int[pages.size]; pageHeights = new int[pages.size];
        for (int i = 0; i < pages.size; i++) {
            JsonValue p = pages.get(i); int[] size = array(p, "size", 2);
            String file = i < atlas.pages.length ? atlas.pages[i].file() : "arena-border.png";
            int w = i < atlas.pages.length ? atlas.pages[i].width() : 512;
            int h = i < atlas.pages.length ? atlas.pages[i].height() : 256;
            if (!p.getString("file").equals(file) || size[0] != w || size[1] != h) throw invalid("borrowed page");
            pageWidths[i] = w; pageHeights[i] = h;
        }
        this.textures = textures;
        JsonValue paints = root.require("paintings");
        field = new Painting("field", paints.require("field"), this);
        path = new Painting("path", paints.require("path"), this);
        medallion = new Painting("medallion", paints.require("medallion"), this);
        if (field.pieces.length + path.pieces.length + medallion.pieces.length > MAX_PIECES) throw invalid("piece pool");
    }

    /** Packed vertex payloads contain the final projected coordinates and source-neighbour UVs. */
    public record Quad(int page, float[] vertices) {}
    private record Vertex(float x, float y, float u, float v) {}

    public Quad[] geometry(Painting painting, float[] polygon, float shade) {
        if (polygon.length < 12 || polygon.length % 4 != 0 || polygon.length > 32 || !Float.isFinite(shade))
            throw invalid("ground polygon");
        ArrayList<Vertex> original = new ArrayList<>();
        for (int i = 0; i < polygon.length; i += 4) {
            for (int j = i; j < i + 4; j++) if (!Float.isFinite(polygon[j])) throw invalid("ground vertex");
            original.add(new Vertex(polygon[i], polygon[i + 1], polygon[i + 2], polygon[i + 3]));
        }
        ArrayList<Quad> result = new ArrayList<>();
        for (Piece p : painting.pieces) {
            float u0 = (float)(p.coreX - painting.contentX) / painting.contentWidth;
            float v0 = (float)(p.coreY - painting.contentY) / painting.contentHeight;
            float u1 = (float)(p.coreX + p.coreWidth - painting.contentX) / painting.contentWidth;
            float v1 = (float)(p.coreY + p.coreHeight - painting.contentY) / painting.contentHeight;
            if (u1 <= u0) continue;
            var clipped = clip(clip(clip(clip(original, true, u0, true), true, u1, false), false, v0, true), false, v1, false);
            if (clipped.size() < 3 || Math.abs(area(clipped)) < .0001f) continue;
            if (clipped.size() == 4) result.add(quad(painting, p, shade, clipped.get(0), clipped.get(1), clipped.get(2), clipped.get(3)));
            else for (int i = 1; i < clipped.size() - 1; i++)
                result.add(quad(painting, p, shade, clipped.get(0), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1)));
        }
        result.sort(Comparator.comparingInt(Quad::page));
        return result.toArray(new Quad[0]);
    }

    private Quad quad(Painting painting, Piece piece, float shade, Vertex a, Vertex b, Vertex c, Vertex d) {
        float[] vertices = new float[20];
        shade = Math.max(0, Math.min(1, shade));
        float color = Color.toFloatBits(shade, shade, shade, 1f);
        vertex(vertices, 0, a, painting, piece, color); vertex(vertices, 5, b, painting, piece, color);
        vertex(vertices, 10, c, painting, piece, color); vertex(vertices, 15, d, painting, piece, color);
        return new Quad(piece.page, vertices);
    }
    private void vertex(float[] out, int at, Vertex vertex, Painting painting, Piece piece, float color) {
        out[at] = (vertex.x - vertex.y) * ISO_X;
        out[at + 1] = (vertex.x + vertex.y) * ISO_Y; out[at + 2] = color;
        out[at + 3] = (piece.x + painting.contentX + vertex.u * painting.contentWidth - piece.sourceX) / pageWidths[piece.page];
        out[at + 4] = (piece.y + painting.contentY + vertex.v * painting.contentHeight - piece.sourceY) / pageHeights[piece.page];
    }
    public void cache(SpriteCache cache, Quad[] quads) {
        for (Quad q : quads) cache.add(textures[q.page], q.vertices, 0, q.vertices.length);
    }
    public void draw(SpriteBatch batch, Quad[] quads) {
        for (Quad q : quads) batch.draw(textures[q.page], q.vertices, 0, q.vertices.length);
    }

    /** Source rows point down; world Y points up. */
    public static float[] rectangle(float minX, float minY, float maxX, float maxY) {
        return new float[]{minX, maxY, 0, 0, minX, minY, 0, 1, maxX, minY, 1, 1, maxX, maxY, 1, 0};
    }
    private static ArrayList<Vertex> clip(ArrayList<Vertex> input, boolean u, float edge, boolean greater) {
        ArrayList<Vertex> output = new ArrayList<>();
        if (input.isEmpty()) return output;
        Vertex previous = input.get(input.size() - 1);
        for (Vertex current : input) {
            float pv = u ? previous.u : previous.v, cv = u ? current.u : current.v;
            boolean pi = greater ? pv >= edge : pv <= edge, ci = greater ? cv >= edge : cv <= edge;
            if (pi != ci) {
                float t = (edge - pv) / (cv - pv);
                output.add(new Vertex(previous.x + (current.x - previous.x) * t, previous.y + (current.y - previous.y) * t,
                    previous.u + (current.u - previous.u) * t, previous.v + (current.v - previous.v) * t));
            }
            if (ci) output.add(current);
            previous = current;
        }
        return output;
    }
    private static float area(ArrayList<Vertex> vertices) {
        float result = 0;
        for (int i = 0; i < vertices.size(); i++) {
            Vertex a = vertices.get(i), b = vertices.get((i + 1) % vertices.size());
            result += a.x * b.y - b.x * a.y;
        }
        return result * .5f;
    }
    private static int[] array(JsonValue root, String key, int count) {
        JsonValue a = root.require(key);
        if (!a.isArray() || a.size != count) throw invalid(key);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            double value = a.getDouble(i);
            if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value > 4096) throw invalid(key);
            result[i] = (int)value;
        }
        return result;
    }
    private static int[] rectangle(JsonValue root, String key) { return array(root, key, 4); }
    private static boolean inside(int[] r, int w, int h) {
        return r[2] > 0 && r[3] > 0 && r[0] <= w - r[2] && r[1] <= h - r[3];
    }
    private static IllegalArgumentException invalid(String why) { return new IllegalArgumentException("Invalid engraved floor: " + why); }
}
