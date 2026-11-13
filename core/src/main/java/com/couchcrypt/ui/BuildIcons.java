package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.game.PartyProgression;

/** One small managed atlas for the twelve upgrade emblems shown in Run Details. */
public final class BuildIcons implements Disposable {
    private static final int WIDTH = 512, HEIGHT = 384, CELL = 128;
    public final TextureRegion[] regions = new TextureRegion[PartyProgression.Upgrade.values().length];
    public final long textureBytes = (long) WIDTH * HEIGHT * 4;
    public final long retainedPixmapBytes = textureBytes;
    private final Texture texture;
    private final Pixmap pixels;
    private boolean disposed;

    public BuildIcons() {
        if (regions.length != 12) throw new IllegalStateException("Skill icon atlas requires twelve upgrades");
        Pixmap source = new Pixmap(Gdx.files.internal("art/skill-icons.png"));
        Pixmap baked = null;
        Texture loaded = null;
        try {
            baked = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
            baked.setBlending(Pixmap.Blending.None);
            baked.setFilter(Pixmap.Filter.BiLinear);
            source.setBlending(Pixmap.Blending.None);
            for (int i = 0; i < regions.length; i++) {
                int x0 = i % 4 * source.getWidth() / 4, x1 = (i % 4 + 1) * source.getWidth() / 4;
                int y0 = i / 4 * source.getHeight() / 3, y1 = (i / 4 + 1) * source.getHeight() / 3;
                int left = x1, right = x0 - 1, top = y1, bottom = y0 - 1;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int alpha = source.getPixel(x, y) & 255;
                    if (alpha < 8) source.drawPixel(x, y, 0);
                    if (alpha < 32) continue;
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
                if (left > right || top > bottom) throw new IllegalStateException("Empty skill icon cell " + i);
                int width = right - left + 1, height = bottom - top + 1;
                float scale = Math.min(112f / width, 112f / height);
                int targetWidth = Math.max(1, Math.round(width * scale)), targetHeight = Math.max(1, Math.round(height * scale));
                baked.drawPixmap(source, left, top, width, height,
                    i % 4 * CELL + (CELL - targetWidth) / 2, i / 4 * CELL + (CELL - targetHeight) / 2, targetWidth, targetHeight);
            }
            loaded = new Texture(new PixmapTextureData(baked, null, false, false, true));
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            for (int i = 0; i < regions.length; i++)
                regions[i] = new TextureRegion(loaded, i % 4 * CELL, i / 4 * CELL, CELL, CELL);
        } catch (RuntimeException | Error failure) {
            if (loaded != null) loaded.dispose();
            if (baked != null) baked.dispose();
            throw failure;
        } finally { source.dispose(); }
        texture = loaded;
        pixels = baked;
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        texture.dispose(); pixels.dispose();
    }
}
