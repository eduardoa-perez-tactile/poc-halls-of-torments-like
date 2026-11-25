package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Borrowed resident font digits, laid out once and transformed through a reusable five-glyph buffer. */
final class DamageNumberArt {
    private static final float NORMAL_SCALE = .64f, CRITICAL_SCALE = .78f;
    private final Texture texture;
    private final float[][] digits = new float[10][20];
    private final float[] vertices = new float[DamageNumbers.MAX_DIGITS * 20];
    private final float cellWidth;

    DamageNumberArt(BitmapFont font) {
        BitmapFontCache cache = new BitmapFontCache(font, false);
        float width = 0;
        int page = font.getData().getGlyph('0').page;
        texture = font.getRegion(page).getTexture();
        for (int digit = 0; digit < digits.length; digit++) {
            if (font.getData().getGlyph((char) ('0' + digit)).page != page)
                throw new IllegalArgumentException("Damage digits must share the resident font page");
            cache.setText(Integer.toString(digit), 0, 0);
            if (cache.getVertexCount(page) != 20)
                throw new IllegalArgumentException("Damage digit requires one cached glyph");
            System.arraycopy(cache.getVertices(page), 0, digits[digit], 0, 20);
            float left = digits[digit][0], right = digits[digit][10], bottom = digits[digit][1];
            width = Math.max(width, right - left + 1f);
            for (int vertex = 0; vertex < 20; vertex += 5) {
                digits[digit][vertex] -= (left + right) * .5f;
                digits[digit][vertex + 1] -= bottom;
            }
        }
        cellWidth = width;
    }

    void draw(SpriteBatch batch, DamageNumbers.Entry entry, float x, float y) {
        float scale = entry.critical ? CRITICAL_SCALE : NORMAL_SCALE;
        float alpha = entry.alpha();
        // One quiet dark shadow preserves contrast without an extra texture or outline font.
        drawPass(batch, entry, x + .8f, y - 1f, scale, Color.toFloatBits(.025f, .02f, .015f, alpha * .8f));
        float color = entry.critical ? Color.toFloatBits(1f, .81f, .35f, alpha)
            : Color.toFloatBits(.94f, .92f, .85f, alpha);
        drawPass(batch, entry, x, y, scale, color);
    }

    private void drawPass(SpriteBatch batch, DamageNumbers.Entry entry, float x, float y, float scale, float color) {
        float advance = cellWidth * scale;
        float start = x - (entry.digitCount - 1) * advance * .5f;
        for (int glyph = 0; glyph < entry.digitCount; glyph++) {
            float[] source = digits[entry.digits[entry.digitCount - 1 - glyph]];
            int offset = glyph * 20;
            for (int vertex = 0; vertex < 20; vertex += 5) {
                vertices[offset + vertex] = start + glyph * advance + source[vertex] * scale;
                vertices[offset + vertex + 1] = y + source[vertex + 1] * scale;
                vertices[offset + vertex + 2] = color;
                vertices[offset + vertex + 3] = source[vertex + 3];
                vertices[offset + vertex + 4] = source[vertex + 4];
            }
        }
        batch.draw(texture, vertices, 0, entry.digitCount * 20);
    }
}
