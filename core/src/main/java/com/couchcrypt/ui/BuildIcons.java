package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.game.PartyProgression;

/** One small managed atlas for the twelve upgrade emblems shown in Run Details. */
public final class BuildIcons implements Disposable {
    private static final int WIDTH = 512, HEIGHT = 384, CELL = 128;
    public final TextureRegion[] regions = new TextureRegion[PartyProgression.Upgrade.values().length];
    public final long textureBytes = (long) WIDTH * HEIGHT * 4;
    public final long retainedPixmapBytes = 0; // Managed texture restores from its normalized file.
    private final Texture texture;
    private byte[] controllerPng;
    private boolean disposed;

    public BuildIcons() {
        if (regions.length != 12) throw new IllegalStateException("Skill icon atlas requires twelve upgrades");
        Texture loaded = new Texture(Gdx.files.internal("art/runtime-ui/skill-icons.png"), false);
        try {
            if (loaded.getWidth() != WIDTH || loaded.getHeight() != HEIGHT)
                throw new IllegalStateException("Skill icon atlas must retain its fixed 512x384 layout");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            for (int i = 0; i < regions.length; i++)
                regions[i] = new TextureRegion(loaded, i % 4 * CELL, i / 4 * CELL, CELL, CELL);
        } catch (RuntimeException | Error failure) {
            loaded.dispose();
            throw failure;
        }
        texture = loaded;
    }

    /** Read the preencoded normalized atlas once; HTTP responses share these bounded PNG bytes. */
    public byte[] controllerIcons() {
        if (disposed) throw new IllegalStateException("Skill icons have been disposed");
        if (controllerPng == null) controllerPng = Gdx.files.internal("art/runtime-ui/skill-icons.png").readBytes();
        return controllerPng;
    }

    public void reloadFileTexture() { texture.load(texture.getTextureData()); }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        controllerPng = null;
        texture.dispose();
    }
}
