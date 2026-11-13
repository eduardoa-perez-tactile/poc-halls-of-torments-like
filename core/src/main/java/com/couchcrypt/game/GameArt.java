package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;

/** Small managed runtime atlas baked once from original painted masters. */
public final class GameArt implements Disposable {
    public final Texture floor, atlas;
    public final TextureRegion[][] players = new TextureRegion[2][4];
    public final TextureRegion[] enemies = new TextureRegion[4], props = new TextureRegion[4];
    public final TextureRegion shadow, glow, pixel, soul;
    public final long textureBytes = (1024L * 512 + 512L * 512) * 4;
    public final long retainedPixmapBytes = textureBytes;
    private final Pixmap atlasPixels, floorPixels;

    public GameArt() {
        floorPixels = new Pixmap(512, 512, Pixmap.Format.RGBA8888);
        Pixmap source = new Pixmap(Gdx.files.internal("art/game-floor.png"));
        floorPixels.setFilter(Pixmap.Filter.BiLinear);
        floorPixels.drawPixmap(source, 0, 0, source.getWidth(), source.getHeight(), 0, 0, 512, 512);
        source.dispose(); floor = texture(floorPixels);
        atlasPixels = new Pixmap(1024, 512, Pixmap.Format.RGBA8888);
        atlasPixels.setBlending(Pixmap.Blending.None); atlasPixels.setFilter(Pixmap.Filter.BiLinear);
        sheet("art/game-players.png", 2, 128, 160, 0, 0, true);
        sheet("art/game-enemy.png", 1, 112, 128, 0, 320, true);
        sheet("art/game-props.png", 1, 128, 192, 512, 0, false);
        radial(512, 224, 64, 32, false); radial(592, 224, 64, 64, true);
        atlasPixels.setColor(1, 1, 1, 1); atlasPixels.fillRectangle(800, 224, 4, 4);
        atlasPixels.fillTriangle(684, 224, 675, 240, 684, 255);
        atlasPixels.fillTriangle(684, 224, 693, 240, 684, 255);
        atlas = texture(atlasPixels);
        for (int row = 0; row < 2; row++) for (int col = 0; col < 4; col++)
            players[row][col] = new TextureRegion(atlas, col * 128, row * 160, 128, 160);
        for (int col = 0; col < 4; col++) {
            enemies[col] = new TextureRegion(atlas, col * 112, 320, 112, 128);
            props[col] = new TextureRegion(atlas, 512 + col * 128, 0, 128, 192);
        }
        shadow = new TextureRegion(atlas, 512, 224, 64, 32);
        glow = new TextureRegion(atlas, 592, 224, 64, 64);
        pixel = new TextureRegion(atlas, 800, 224, 4, 4);
        soul = new TextureRegion(atlas, 672, 224, 24, 32);
    }

    private Texture texture(Pixmap pixels) {
        Texture result = new Texture(new PixmapTextureData(pixels, null, false, false, true));
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }

    private void sheet(String file, int rows, int cw, int ch, int ox, int oy, boolean commonScale) {
        Pixmap image = new Pixmap(Gdx.files.internal(file));
        image.setBlending(Pixmap.Blending.None);
        // Generated transparent masters can contain almost invisible alpha noise.
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getPixel(x, y) & 255) < 32) image.drawPixel(x, y, 0);
        for (int row = 0; row < rows; row++) {
            int[][] bounds = new int[4][];
            float scale = Float.MAX_VALUE;
            for (int col = 0; col < 4; col++) {
                bounds[col] = bounds(image, col, row, rows);
                scale = Math.min(scale, Math.min((cw - 16f) / bounds[col][2], (ch - 18f) / bounds[col][3]));
            }
            for (int col = 0; col < 4; col++) {
                int[] b = bounds[col];
                float fit = commonScale ? scale : Math.min((cw - 16f) / b[2], (ch - 18f) / b[3]);
                int w = Math.max(1, Math.round(b[2] * fit)), h = Math.max(1, Math.round(b[3] * fit));
                atlasPixels.drawPixmap(image, b[0], b[1], b[2], b[3],
                    ox + col * cw + (cw - w) / 2, oy + row * ch + ch - 8 - h, w, h);
            }
        }
        image.dispose();
    }

    private int[] bounds(Pixmap image, int col, int row, int rows) {
        int x0 = col * image.getWidth() / 4, x1 = (col + 1) * image.getWidth() / 4;
        int y0 = row * image.getHeight() / rows, y1 = (row + 1) * image.getHeight() / rows;
        int left = x1, right = x0, top = y1, bottom = y0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((image.getPixel(x, y) & 255) >= 64) {
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (left > right || top > bottom) throw new IllegalStateException("Empty gameplay art cell");
        return new int[]{left, top, right - left + 1, bottom - top + 1};
    }

    private void radial(int ox, int oy, int w, int h, boolean light) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float dx = (x + .5f - w / 2f) / (w / 2f), dy = (y + .5f - h / 2f) / (h / 2f);
            float alpha = Math.max(0, 1 - dx * dx - dy * dy);
            int a = (int) (alpha * alpha * (light ? 170 : 145));
            atlasPixels.drawPixel(ox + x, oy + y, (light ? 0xffffff00 : 0) | a);
        }
    }

    @Override public void dispose() { floor.dispose(); atlas.dispose(); floorPixels.dispose(); atlasPixels.dispose(); }
}
