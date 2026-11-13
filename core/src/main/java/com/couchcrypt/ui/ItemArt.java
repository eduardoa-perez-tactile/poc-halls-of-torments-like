package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.game.ItemCatalog;

/** File-backed managed equipment atlas: three MiB GPU, no retained decoded CPU pixmap. */
public final class ItemArt implements Disposable {
    public static final int COLUMNS = 8, ROWS = 6, CELL = 128;
    public final TextureRegion[] regions = new TextureRegion[ItemCatalog.ALL.size()];
    public final long textureBytes = (long) COLUMNS * ROWS * CELL * CELL * 4;
    private final Texture texture;

    public ItemArt() {
        if (regions.length != COLUMNS * ROWS) throw new IllegalStateException("Equipment art requires 48 items");
        texture = new Texture(Gdx.files.internal("art/item-icons.png"));
        if (texture.getWidth() != COLUMNS * CELL || texture.getHeight() != ROWS * CELL) {
            texture.dispose();
            throw new IllegalStateException("Equipment atlas must be 1024 by 768");
        }
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        for (int i = 0; i < regions.length; i++)
            regions[i] = new TextureRegion(texture, i % COLUMNS * CELL, i / COLUMNS * CELL, CELL, CELL);
    }

    @Override public void dispose() { texture.dispose(); }
}
