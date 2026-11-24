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
    private static final int TITLE_WIDTH = 1024, TITLE_HEIGHT = 256;
    public final boolean victory;
    public final Texture backdrop, title;
    public final long textureBytes = 1536L * 864 * 4;
    public final long retainedPixmapBytes = textureBytes;
    private final Pixmap backdropPixels;
    private boolean disposed;

    /** Both normalized file-backed wordmarks are uploaded before the first frame. */
    public static final class Titles implements Disposable {
        public final long textureBytes = 2L * TITLE_WIDTH * TITLE_HEIGHT * 4;
        public final long retainedPixmapBytes = 0; // Managed textures restore from their normalized files.
        private final Texture[] textures = new Texture[2];
        private boolean disposed;

        public Titles() {
            try {
                textures[0] = loadTitle("art/runtime-ui/victory-title.png");
                textures[1] = loadTitle("art/runtime-ui/defeat-title.png");
            } catch (RuntimeException | Error failure) {
                dispose();
                throw failure;
            }
        }

        public Texture get(boolean victory) {
            if (disposed) throw new IllegalStateException("Outcome titles have been disposed");
            return textures[victory ? 0 : 1];
        }

        private static Texture loadTitle(String path) {
            Texture loaded = new Texture(Gdx.files.internal(path), false);
            try {
                if (loaded.getWidth() != TITLE_WIDTH || loaded.getHeight() != TITLE_HEIGHT)
                    throw new GdxRuntimeException("Outcome title must retain its fixed 1024x256 layout: " + path);
                loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                return loaded;
            } catch (RuntimeException | Error failure) { loaded.dispose(); throw failure; }
        }

        public void reloadFileTextures() {
            for (Texture texture : textures) texture.load(texture.getTextureData());
        }

        @Override public void dispose() {
            if (disposed) return;
            disposed = true;
            for (Texture value : textures) if (value != null) value.dispose();
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
