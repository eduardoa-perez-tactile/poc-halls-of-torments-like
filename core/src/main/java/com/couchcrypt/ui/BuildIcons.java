package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.game.PartyProgression;

/** One managed atlas: unchanged legacy emblems, compact expansion glyphs and four world sprites. */
public final class BuildIcons implements Disposable {
    public static final int WIDTH = 512, HEIGHT = 512;
    private static final int LEGACY_COUNT = 12, LEGACY_CELL = 128, EXPANSION_CELL = 64;
    public final TextureRegion[] regions = new TextureRegion[PartyProgression.Upgrade.values().length];
    public final long textureBytes = (long) WIDTH * HEIGHT * 4;
    public final long retainedPixmapBytes = 0; // Managed texture restores from its normalized file.
    public final TextureRegion sentinelBlade, glaiveWeapon, soulHarvestRelic, stasisRelic;
    private final Texture texture;
    private byte[] controllerPng;
    private boolean disposed;

    public BuildIcons() {
        if (regions.length != 24) throw new IllegalStateException("Skill icon atlas requires twenty-four upgrades");
        Texture loaded = new Texture(Gdx.files.internal("art/runtime-ui/skill-icons.ktx"), false);
        try {
            if (!loaded.isManaged() || loaded.getWidth() != WIDTH || loaded.getHeight() != HEIGHT || loaded.getTextureData().isPrepared())
                throw new IllegalStateException("Skill icon atlas requires its fixed managed 512x512 layout");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            for (int i = 0; i < regions.length; i++) {
                int cell = i < LEGACY_COUNT ? LEGACY_CELL : EXPANSION_CELL;
                int index = i < LEGACY_COUNT ? i : i - LEGACY_COUNT;
                int columns = i < LEGACY_COUNT ? 4 : 8;
                int y = i < LEGACY_COUNT ? 0 : 384;
                regions[i] = new TextureRegion(loaded, index % columns * cell, y + index / columns * cell, cell, cell);
            }
            sentinelBlade = new TextureRegion(loaded, 256, 448, 64, 64);
            glaiveWeapon = new TextureRegion(loaded, 320, 448, 64, 64);
            soulHarvestRelic = new TextureRegion(loaded, 384, 448, 64, 64);
            stasisRelic = new TextureRegion(loaded, 448, 448, 64, 64);
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
