package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Layout semantics survive offline data loading without GL or native FreeType. */
class BakedUiFontsTest {
    @Test void preservesScaledMetricsSignedKerningAndSyntheticSpaceInActualLayout() {
        BitmapFontData data = BakedUiFonts.readData(definition());
        assertEquals("fixture", data.name);
        assertEquals(42.25f, data.lineHeight);
        assertEquals(-42.25f, data.down);
        assertEquals(-5.5f, data.descent);
        assertEquals(1.5f, data.scaleX);
        assertEquals(2f, data.scaleY);
        assertEquals(1.25f, data.padLeft);
        assertEquals(.75f, data.blankLineScale);
        assertEquals(-2, data.getGlyph('A').getKerning('V'));
        assertEquals(1, data.getGlyph('V').getKerning('A'));
        assertEquals(0, data.getGlyph(' ').height);
        assertEquals(8, data.getGlyph(' ').width);
        assertEquals(2, data.getGlyph('V').page, "Global shared-atlas page indices are retained");
        assertNull(data.missingGlyph);
        assertNull(data.breakChars);
        assertArrayEquals(new char[]{'x', 'e'}, data.xChars);
        assertArrayEquals(new char[]{'M', 'N'}, data.capChars);

        GlyphRun run = new GlyphRun();
        data.getGlyphs(run, "AV A?", 0, 5, null);
        assertEquals(4, run.glyphs.size, "Unsupported characters stay absent when the original has no missing glyph");
        assertEquals(.25f, run.xAdvances.get(0));
        assertEquals(18f, run.xAdvances.get(1), "Kerning is signed and applied at the preserved scale");
        assertEquals(19.5f, run.xAdvances.get(2));
        assertEquals(12f, run.xAdvances.get(3), "Zero-height space still advances the next character");
        assertEquals(11f, run.xAdvances.get(4));
    }

    @Test void missingGlyphReferencesExistingGlyphAndWrapCharactersRemainPersonalData() {
        JsonValue font = definition();
        font.get("missingGlyph").set(65L, null);
        font.get("metrics").get("breakChars").set("-");
        font.get("metrics").get("flipped").set(true);
        BitmapFontData data = BakedUiFonts.readData(font);
        assertSame(data.getGlyph('A'), data.missingGlyph);
        assertTrue(data.flipped);
        assertTrue(data.isBreakChar('-'));
        GlyphRun run = new GlyphRun();
        data.getGlyphs(run, "?", 0, 1, null);
        assertEquals(1, run.glyphs.size);
        assertSame(data.missingGlyph, run.glyphs.first());
    }

    @Test void rejectsMissingGlyphReferencesAndKerningThatWouldSilentlyOverflow() {
        JsonValue missing = definition();
        missing.get("missingGlyph").set(1000L, null);
        assertThrows(IllegalArgumentException.class, () -> BakedUiFonts.readData(missing));
        JsonValue kerning = definition();
        kerning.get("glyphs").get(0).get("kerning").get(0).get(1).set(128L, null);
        assertThrows(IllegalArgumentException.class, () -> BakedUiFonts.readData(kerning));
    }

    private static JsonValue definition() {
        return new JsonReader().parse("""
            {"metrics":{"name":"fixture","flipped":false,
              "padTop":0.5,"padRight":2.5,"padBottom":0.75,"padLeft":1.25,
              "lineHeight":42.25,"capHeight":30.5,"ascent":6.25,"descent":-5.5,"down":-42.25,
              "blankLineScale":0.75,"scaleX":1.5,"scaleY":2,"markupEnabled":false,"cursorX":-0.5,
              "spaceXadvance":12,"xHeight":20.25,"breakChars":null,"xChars":"xe","capChars":"MN"},
             "missingGlyph":null,"glyphs":[
              {"id":65,"srcX":4,"srcY":8,"width":10,"height":20,"xoffset":-1,"yoffset":-19,
               "xadvance":14,"fixedWidth":false,"page":0,"kerning":[[86,-2]]},
              {"id":86,"srcX":40,"srcY":8,"width":9,"height":19,"xoffset":0,"yoffset":-18,
               "xadvance":13,"fixedWidth":false,"page":2,"kerning":[[65,1]]},
              {"id":32,"srcX":0,"srcY":0,"width":8,"height":0,"xoffset":0,"yoffset":0,
               "xadvance":8,"fixedWidth":false,"page":0,"kerning":[]}]}
            """);
    }
}
