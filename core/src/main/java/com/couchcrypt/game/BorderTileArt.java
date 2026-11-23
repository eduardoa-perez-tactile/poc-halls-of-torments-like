package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.Locale;

/** Nine integrated floor/border cells on one file-backed managed half-MiB page. */
public final class BorderTileArt implements Disposable {
    public enum Tile { INTERIOR, EDGE_NW, EDGE_NE, EDGE_SW, EDGE_SE, CORNER_N, CORNER_W, CORNER_E, CORNER_S }
    /** Trimmed rectangle relative to the far footprint vertex; renderer Y points upward. */
    public record Region(TextureRegion texture, float left, float bottom, float width, float height) {}
    public final float tileWidth, tileHeight;
    public final long textureBytes;
    public final long retainedPixmapBytes = 0;
    private final Texture texture;
    private final Region[] regions = new Region[Tile.values().length];

    public BorderTileArt() {
        Metadata metadata = new Metadata(new JsonReader().parse(Gdx.files.internal("art/arena-border.json")));
        tileWidth = metadata.tileWidth; tileHeight = metadata.tileHeight; textureBytes = metadata.textureBytes;
        Texture loaded = new Texture(Gdx.files.internal("art/" + metadata.file));
        try {
            if (!loaded.isManaged() || loaded.getWidth() != metadata.width || loaded.getHeight() != metadata.height)
                throw invalid("managed page dimensions disagree with metadata");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            for (int i = 0; i < regions.length; i++) {
                Frame frame = metadata.frames[i];
                regions[i] = new Region(new TextureRegion(loaded, frame.x, frame.y, frame.width, frame.height),
                    frame.left, frame.bottom, frame.width, frame.height);
            }
            texture = loaded;
        } catch (RuntimeException | Error failure) { loaded.dispose(); throw failure; }
    }

    public Region region(Tile tile) { return regions[tile.ordinal()]; }
    @Override public void dispose() { texture.dispose(); }

    static final class Frame {
        final int x, y, width, height;
        final float left, bottom;
        Frame(JsonValue json, Metadata page) {
            int[] r = integers(json, "rect", 4), source = integers(json, "sourceSize", 2);
            int[] trim = integers(json, "trimOffset", 2);
            float[] anchor = floats(json, "anchor", 2);
            x = r[0]; y = r[1]; width = r[2]; height = r[3];
            if (x < page.padding || y < page.padding || width <= 0 || height <= 0
                || width > page.width - page.padding - x || height > page.height - page.padding - y
                || source[0] != 152 || source[1] != 84 || trim[0] < 0 || trim[1] < 0
                || width > source[0] - trim[0] || height > source[1] - trim[1]
                || anchor[0] != page.anchorX || anchor[1] != page.anchorY)
                throw invalid("frame bounds or shared anchor");
            left = trim[0] - anchor[0];
            bottom = anchor[1] - trim[1] - height;
        }
    }

    /** Validate the shipped atlas without loading GL textures, before allocating the page. */
    static final class Metadata {
        final String file;
        final int width, height, padding;
        final float tileWidth, tileHeight, anchorX, anchorY;
        final long textureBytes;
        final Frame[] frames = new Frame[Tile.values().length];
        Metadata(JsonValue json) {
            if (json.getInt("version") != 1) throw invalid("version");
            file = json.getString("file");
            int[] size = integers(json, "size", 2);
            width = size[0]; height = size[1];
            if (!file.matches("[A-Za-z0-9_-]+\\.png") || width != 512 || height != 256)
                throw invalid("page must remain bounded to 512x256");
            textureBytes = (long) width * height * 4;
            tileWidth = json.getFloat("tileWidth"); tileHeight = json.getFloat("tileHeight");
            float[] anchor = floats(json, "anchor", 2); anchorX = anchor[0]; anchorY = anchor[1];
            padding = json.getInt("padding");
            if (tileWidth != 120 || tileHeight != 60 || anchorX != 76 || anchorY != 1
                || padding != 2 || json.getInt("edgeExtrusion") != 1)
                throw invalid("footprint, anchor or sampling gutter");
            JsonValue list = json.require("tiles");
            if (!list.isArray() || list.size != frames.length) throw invalid("nine distinct tiles required");
            for (JsonValue entry = list.child; entry != null; entry = entry.next) {
                Tile tile;
                try { tile = Tile.valueOf(entry.getString("id").replace('-', '_').toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException failure) { throw invalid("unknown tile id"); }
                if (frames[tile.ordinal()] != null) throw invalid("duplicate tile id");
                Frame frame = new Frame(entry, this);
                for (Frame other : frames) if (other != null
                    && frame.x - padding < other.x + other.width + padding
                    && frame.x + frame.width + padding > other.x - padding
                    && frame.y - padding < other.y + other.height + padding
                    && frame.y + frame.height + padding > other.y - padding)
                    throw invalid("overlapping tile sampling gutters");
                frames[tile.ordinal()] = frame;
            }
        }
    }

    private static JsonValue array(JsonValue json, String key, int length) {
        JsonValue value = json.require(key);
        if (!value.isArray() || value.size != length) throw invalid(key);
        return value;
    }
    private static int[] integers(JsonValue json, String key, int length) {
        JsonValue values = array(json, key, length);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            double number = values.getDouble(i);
            if (!Double.isFinite(number) || number != Math.rint(number) || number < 0 || number > 4096)
                throw invalid(key);
            result[i] = (int) number;
        }
        return result;
    }
    private static float[] floats(JsonValue json, String key, int length) {
        JsonValue values = array(json, key, length);
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = values.getFloat(i);
            if (!Float.isFinite(result[i])) throw invalid(key);
        }
        return result;
    }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid border tile atlas: " + detail);
    }
}
