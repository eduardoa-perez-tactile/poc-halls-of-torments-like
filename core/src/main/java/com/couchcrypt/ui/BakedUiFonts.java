package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Exact offline FreeType metrics over shared file-backed managed atlas pages. */
public final class BakedUiFonts implements Disposable {
    private static final String DIRECTORY = "art/runtime-ui/";
    private static final String[] NAMES = {"title", "heading", "section", "body", "small"};
    public final BitmapFont title, heading, section, body, small;
    public final long textureBytes;
    public final long retainedPixmapBytes = 0;
    private final Array<Texture> pages = new Array<>();

    public BakedUiFonts() {
        BitmapFont[] fonts = new BitmapFont[NAMES.length];
        long bytes = 0;
        try {
            JsonValue root = new JsonReader().parse(Gdx.files.internal(DIRECTORY + "fonts.json"));
            JsonValue pageFiles = root.require("pages");
            if (!pageFiles.isArray() || pageFiles.size == 0)
                throw new IllegalArgumentException("Baked UI fonts require atlas pages");
            Array<TextureRegion> regions = new Array<>(pageFiles.size);
            for (JsonValue file = pageFiles.child; file != null; file = file.next) {
                Texture texture = new Texture(Gdx.files.internal(DIRECTORY + file.asString()), false);
                pages.add(texture);
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                regions.add(new TextureRegion(texture));
                bytes += (long) texture.getWidth() * texture.getHeight() * 4;
            }
            JsonValue definitions = root.require("fonts");
            for (int i = 0; i < NAMES.length; i++) {
                BitmapFontData data = readData(definitions.require(NAMES[i]));
                for (Glyph[] glyphPage : data.glyphs) {
                    if (glyphPage == null) continue;
                    for (Glyph glyph : glyphPage)
                        if (glyph != null && glyph.page >= pages.size)
                            throw new IllegalArgumentException("Glyph page outside baked UI atlas: " + glyph.id);
                }
                // Full untrimmed page regions let BitmapFont calculate UVs without
                // clipping metrics. The same ordered texture pages serve all fonts.
                fonts[i] = new BitmapFont(data, regions, false);
                fonts[i].setOwnsTexture(false);
            }
        } catch (RuntimeException | Error failure) {
            for (BitmapFont font : fonts) if (font != null) font.dispose();
            for (Texture page : pages) page.dispose();
            pages.clear();
            throw failure;
        }
        title = fonts[0]; heading = fonts[1]; section = fonts[2]; body = fonts[3]; small = fonts[4];
        textureBytes = bytes;
    }

    /** Parses layout data without needing a GL context or native font rasterizer. */
    static BitmapFontData readData(JsonValue font) {
        JsonValue metrics = font.require("metrics");
        BitmapFontData data = new BitmapFontData();
        data.name = metrics.getString("name", null);
        data.flipped = metrics.getBoolean("flipped");
        data.padTop = metrics.getFloat("padTop");
        data.padRight = metrics.getFloat("padRight");
        data.padBottom = metrics.getFloat("padBottom");
        data.padLeft = metrics.getFloat("padLeft");
        data.lineHeight = metrics.getFloat("lineHeight");
        data.capHeight = metrics.getFloat("capHeight");
        data.ascent = metrics.getFloat("ascent");
        data.descent = metrics.getFloat("descent");
        data.down = metrics.getFloat("down");
        data.blankLineScale = metrics.getFloat("blankLineScale");
        // Values already include the original scale. Calling setScale here would
        // scale these metrics a second time.
        data.scaleX = metrics.getFloat("scaleX");
        data.scaleY = metrics.getFloat("scaleY");
        data.markupEnabled = metrics.getBoolean("markupEnabled");
        data.cursorX = metrics.getFloat("cursorX");
        data.spaceXadvance = metrics.getFloat("spaceXadvance");
        data.xHeight = metrics.getFloat("xHeight");
        data.breakChars = chars(metrics.require("breakChars"));
        data.xChars = chars(metrics.require("xChars"));
        data.capChars = chars(metrics.require("capChars"));
        for (JsonValue entry = font.require("glyphs").child; entry != null; entry = entry.next) {
            Glyph glyph = new Glyph();
            glyph.id = characterCode(entry.getInt("id"));
            if (data.getGlyph((char) glyph.id) != null)
                throw new IllegalArgumentException("Duplicate baked glyph: " + glyph.id);
            glyph.srcX = entry.getInt("srcX"); glyph.srcY = entry.getInt("srcY");
            glyph.width = entry.getInt("width"); glyph.height = entry.getInt("height");
            glyph.xoffset = entry.getInt("xoffset"); glyph.yoffset = entry.getInt("yoffset");
            glyph.xadvance = entry.getInt("xadvance");
            glyph.fixedWidth = entry.getBoolean("fixedWidth");
            glyph.page = entry.getInt("page");
            if (glyph.page < 0) throw new IllegalArgumentException("Negative baked glyph page");
            for (JsonValue pair = entry.require("kerning").child; pair != null; pair = pair.next) {
                int next = characterCode(pair.getInt(0)), amount = pair.getInt(1);
                if (amount < Byte.MIN_VALUE || amount > Byte.MAX_VALUE)
                    throw new IllegalArgumentException("Kerning outside signed-byte range");
                glyph.setKerning(next, amount);
            }
            // Include zero-height glyphs such as the space, whose advance and
            // synthetic width still contribute to layout and wrapping.
            data.setGlyph(glyph.id, glyph);
        }
        JsonValue missing = font.require("missingGlyph");
        if (!missing.isNull()) {
            int id = characterCode(missing.asInt());
            data.missingGlyph = data.getGlyph((char) id);
            if (data.missingGlyph == null)
                throw new IllegalArgumentException("Missing-glyph reference is not defined: " + id);
        }
        return data;
    }

    private static char[] chars(JsonValue value) {
        return value.isNull() ? null : value.asString().toCharArray();
    }

    private static int characterCode(int value) {
        if (value < Character.MIN_VALUE || value > Character.MAX_VALUE)
            throw new IllegalArgumentException("Baked glyph outside UTF-16 range: " + value);
        return value;
    }

    /** Render-thread diagnostic: reload file pixels into existing GL handles. */
    public void reloadFileTextures() {
        for (Texture page : pages) page.load(page.getTextureData());
    }

    @Override public void dispose() {
        title.dispose(); heading.dispose(); section.dispose(); body.dispose(); small.dispose();
        for (Texture page : pages) page.dispose();
        pages.clear();
    }
}
