package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** Static approved SVG-derived glass, metal and rail layers; shared by both player HUDs. */
public final class HudArt implements Disposable {
    public static final int ORB_SIZE = 128, BLOOD_INSET = 21, BLOOD_SIZE = 86;
    public final long textureBytes = 512L * 256 * 4;
    public final long retainedPixmapBytes = 0; // File-backed managed texture restores from the small PNG.
    public final TextureRegion orbBack, orbBlood, orbReflection, orbGold, orbTeal;
    public final TextureRegion skillGold, skillTeal, dodgeBack, dodgeGlyph, healthFill, xpGold, xpTeal, pixel;
    private final Texture texture;
    private boolean disposed;

    public HudArt() {
        Texture loaded = new Texture(Gdx.files.internal("art/hud-atlas.png"), false);
        try {
            if (loaded.getWidth() != 512 || loaded.getHeight() != 256)
                throw new IllegalStateException("HUD atlas must stay within its fixed 512x256 layout");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (RuntimeException | Error failure) { loaded.dispose(); throw failure; }
        texture = loaded;
        orbBack = region(0, 0, 128, 128);
        orbBlood = region(128 + BLOOD_INSET, BLOOD_INSET, BLOOD_SIZE, BLOOD_SIZE);
        orbReflection = region(256, 0, 128, 128);
        orbGold = region(384, 0, 128, 128);
        orbTeal = region(0, 128, 128, 128);
        skillGold = region(128, 128, 64, 64);
        skillTeal = region(192, 128, 64, 64);
        dodgeBack = region(256, 128, 64, 64);
        dodgeGlyph = region(320, 128, 64, 64);
        healthFill = region(256, 192, 32, 16);
        xpGold = region(288, 192, 32, 8);
        xpTeal = region(320, 192, 32, 8);
        pixel = region(353, 193, 1, 1);
    }

    private TextureRegion region(int x, int y, int width, int height) {
        return new TextureRegion(texture, x, y, width, height);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        texture.dispose();
    }
}
