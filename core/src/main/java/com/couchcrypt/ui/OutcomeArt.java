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
    public final long retainedPixmapBytes = textureBytes;
    private final Pixmap backdropPixels;
    private boolean disposed;

    /** Both small wordmarks are prepared before the first frame to avoid transition-time alpha scans. */
    public static final class Titles implements Disposable {
        public final long textureBytes = 2L * TITLE_WIDTH * TITLE_HEIGHT * 4;
        public final long retainedPixmapBytes = textureBytes;
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

        public Texture get(boolean victory) {
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

    /** Takes ownership of a worker-prepared pixmap; call only on the render thread. */
    private OutcomeArt(boolean victory, Titles titles, Pixmap background) {
        this.victory = victory;
        Texture backgroundTexture;
        Texture titleTexture;
        try {
            if (titles == null) throw new IllegalArgumentException("Shared outcome titles are required");
            titleTexture = titles.get(victory);
            backgroundTexture = texture(background);
        } catch (RuntimeException | Error failure) { background.dispose(); throw failure; }
        title = titleTexture;
        backdropPixels = background;
        backdrop = backgroundTexture;
    }

    /**
     * One worker and one replaceable request, rather than an unbounded task queue. Native decode,
     * crop and downsample run here; texture creation and ownership transfer happen only in poll().
     */
    public static final class Loader implements Disposable {
        @FunctionalInterface interface Decoder { Pixmap decode(boolean victory); }
        private final Decoder decoder;
        private final Thread worker;
        private long generation, requestedId, readyId;
        private boolean requestedVictory, readyVictory, queued, disposed;
        private Pixmap prepared;
        private Throwable failure;

        public Loader() {
            this(victory -> bakeBackdrop(victory ? "art/end-victory-background.png" : "art/end-defeat-background.png"));
        }

        Loader(Decoder decoder) {
            this.decoder = decoder;
            worker = new Thread(this::run, "outcome-art-decode");
            worker.setDaemon(true);
            worker.start();
        }

        /** Replaces the waiting request. An already running decode will be discarded if stale. */
        public synchronized long request(boolean victory) {
            if (disposed) throw new IllegalStateException("Outcome loader has been disposed");
            discardPrepared();
            requestedId = ++generation; requestedVictory = victory; queued = true;
            notifyAll();
            return requestedId;
        }

        /** Returns null while waiting. A decode failure is reported once; no GPU work runs on the worker. */
        public OutcomeArt poll(long id, Titles titles) {
            Pixmap pixels;
            boolean victory;
            synchronized (this) {
                if (disposed || id != requestedId || readyId != id) return null;
                if (failure != null) {
                    Throwable cause = failure; failure = null; readyId = 0;
                    throw new GdxRuntimeException("Cannot prepare outcome background", cause);
                }
                pixels = prepared; victory = readyVictory;
                prepared = null; readyId = 0;
            }
            return new OutcomeArt(victory, titles, pixels);
        }

        /** Ready CPU pixel storage awaiting render-thread upload; transient source decode is not retained. */
        public synchronized long preparedPixmapBytes() {
            return prepared == null ? 0 : (long) prepared.getWidth() * prepared.getHeight() * 4;
        }

        public synchronized void cancel() {
            requestedId = ++generation; queued = false; discardPrepared();
        }

        private void discardPrepared() {
            if (prepared != null) { prepared.dispose(); prepared = null; }
            readyId = 0; failure = null;
        }

        private void run() {
            while (true) {
                long id;
                boolean victory;
                synchronized (this) {
                    while (!queued && !disposed) {
                        try { wait(); }
                        catch (InterruptedException ignored) { if (disposed) return; }
                    }
                    if (disposed) return;
                    id = requestedId; victory = requestedVictory; queued = false;
                }
                Pixmap pixels = null;
                Throwable problem = null;
                try { pixels = decoder.decode(victory); }
                catch (RuntimeException | Error error) { problem = error; }
                synchronized (this) {
                    if (disposed || id != requestedId) {
                        if (pixels != null) pixels.dispose();
                    } else {
                        prepared = pixels; readyVictory = victory; readyId = id; failure = problem;
                    }
                }
            }
        }

        /** Does not block rendering on a native decode. Its final stale result is disposed before exit. */
        @Override public synchronized void dispose() {
            if (disposed) return;
            disposed = true; requestedId = ++generation; queued = false;
            discardPrepared(); notifyAll(); worker.interrupt();
        }
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
