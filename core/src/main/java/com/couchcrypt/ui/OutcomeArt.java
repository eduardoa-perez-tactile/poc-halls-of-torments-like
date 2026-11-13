package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** Lazy outcome backdrop with a borrowed, startup-prewarmed managed title. */
public final class OutcomeArt implements Disposable {
    private static final int BACKDROP_WIDTH = 1536, BACKDROP_HEIGHT = 864;
    private static final int TITLE_WIDTH = 1024, TITLE_HEIGHT = 256, TITLE_PADDING = 8;
    public final boolean victory;
    public final Texture backdrop, title;
    public final long textureBytes = 1536L * 864 * 4;
    private final Pixmap backdropPixels;
    private boolean disposed;

    /** Both small wordmarks are prepared before the first frame to avoid transition-time alpha scans. */
    public static final class Titles implements Disposable {
        public final long textureBytes = 2L * TITLE_WIDTH * TITLE_HEIGHT * 4;
        private final Texture[] textures = new Texture[2];
        private final Pixmap[] pixels = new Pixmap[2];
        private boolean disposed;

        public Titles() {
            try {
                pixels[0] = bakeTitle("art/end-victory-title.png");
                textures[0] = texture(pixels[0]);
                pixels[1] = bakeTitle("art/end-defeat-title.png");
                textures[1] = texture(pixels[1]);
            } catch (RuntimeException | Error failure) {
                dispose();
                throw failure;
            }
        }

        private Texture get(boolean victory) {
            if (disposed) throw new IllegalStateException("Outcome titles have been disposed");
            return textures[victory ? 0 : 1];
        }

        @Override public void dispose() {
            if (disposed) return;
            disposed = true;
            for (Texture value : textures) if (value != null) value.dispose();
            for (Pixmap value : pixels) if (value != null) value.dispose();
        }
    }

    public OutcomeArt(boolean victory, Titles titles) {
        if (titles == null) throw new IllegalArgumentException("Shared outcome titles are required");
        this.victory = victory;
        title = titles.get(victory);
        String path = victory ? "art/end-victory-background.png" : "art/end-defeat-background.png";
        Pixmap background = bakeBackdrop(path);
        Texture backgroundTexture;
        try { backgroundTexture = texture(background); }
        catch (RuntimeException | Error failure) { background.dispose(); throw failure; }
        backdropPixels = background;
        backdrop = backgroundTexture;
    }

    private static Pixmap bakeBackdrop(String path) {
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        Pixmap output = null;
        try {
            output = new Pixmap(BACKDROP_WIDTH, BACKDROP_HEIGHT, Pixmap.Format.RGBA8888);
            output.setColor(0, 0, 0, 1); output.fill();
            output.setFilter(Pixmap.Filter.BiLinear);
            // Center-crop to the TV aspect ratio rather than stretching the painted scene.
            int width = source.getWidth(), height = source.getHeight();
            if ((long) width * BACKDROP_HEIGHT > (long) height * BACKDROP_WIDTH)
                width = Math.max(1, Math.round(height * (float) BACKDROP_WIDTH / BACKDROP_HEIGHT));
            else height = Math.max(1, Math.round(width * (float) BACKDROP_HEIGHT / BACKDROP_WIDTH));
            output.drawPixmap(source, (source.getWidth() - width) / 2, (source.getHeight() - height) / 2,
                width, height, 0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT);
            return output;
        } catch (RuntimeException | Error failure) {
            if (output != null) output.dispose();
            throw failure;
        } finally { source.dispose(); }
    }

    private static Pixmap bakeTitle(String path) {
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        Pixmap output = null;
        try {
            source.setBlending(Pixmap.Blending.None);
            int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int alpha = source.getPixel(x, y) & 255;
                if (alpha < 8) source.drawPixel(x, y, 0);
                if (alpha < 64) continue;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
            if (right < left || bottom < top) throw new GdxRuntimeException("Empty outcome title: " + path);
            left = Math.max(0, left - 4); top = Math.max(0, top - 4);
            right = Math.min(source.getWidth() - 1, right + 4);
            bottom = Math.min(source.getHeight() - 1, bottom + 4);
            int width = right - left + 1, height = bottom - top + 1;
            float scale = Math.min((TITLE_WIDTH - TITLE_PADDING * 2f) / width,
                (TITLE_HEIGHT - TITLE_PADDING * 2f) / height);
            int targetWidth = Math.max(1, Math.min(TITLE_WIDTH - TITLE_PADDING * 2, Math.round(width * scale)));
            int targetHeight = Math.max(1, Math.min(TITLE_HEIGHT - TITLE_PADDING * 2, Math.round(height * scale)));
            output = new Pixmap(TITLE_WIDTH, TITLE_HEIGHT, Pixmap.Format.RGBA8888);
            output.setBlending(Pixmap.Blending.None); output.setFilter(Pixmap.Filter.BiLinear);
            output.drawPixmap(source, left, top, width, height,
                (TITLE_WIDTH - targetWidth) / 2, (TITLE_HEIGHT - targetHeight) / 2, targetWidth, targetHeight);
            return output;
        } catch (RuntimeException | Error failure) {
            if (output != null) output.dispose();
            throw failure;
        } finally { source.dispose(); }
    }

    private static Texture texture(Pixmap pixels) {
        Texture result = new Texture(new PixmapTextureData(pixels, null, false, false, true));
        try {
            result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return result;
        } catch (RuntimeException | Error failure) { result.dispose(); throw failure; }
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        backdrop.dispose();
        backdropPixels.dispose();
    }
}
