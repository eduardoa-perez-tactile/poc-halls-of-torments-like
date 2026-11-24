package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;

/** File-backed managed pages; canonical frame geometry is baked offline. */
public final class GameArt implements Disposable {
    public final GameplayAtlas metadata;
    public final VfxArt vfx;
    public final SpriteEffectArt spriteEffects;
    public final BorderTileArt borders;
    public final EnemyAttackArt enemyAttacks;
    public final MagicProjectileArt projectiles;
    public final PaintedBorderArt paintedBorders;
    public final EngravedFloorArt engravedFloor;
    public final TextureRegion shadow, glow, pixel, soul;
    public final long textureBytes;
    public final long retainedPixmapBytes = 0;
    private final Texture[] pages;
    private final TextureRegion[] frames;
    private final TextureRegion[][] materialFrames;

    public GameArt() {
        metadata = new GameplayAtlas(new JsonReader().parse(Gdx.files.internal("art/gameplay.json")));
        pages = new Texture[metadata.pages.length];
        vfx = new VfxArt();
        try { borders = new BorderTileArt(); }
        catch (RuntimeException | Error failure) { vfx.dispose(); throw failure; }
        try { spriteEffects = new SpriteEffectArt(); }
        catch (RuntimeException | Error failure) { borders.dispose(); vfx.dispose(); throw failure; }
        textureBytes = metadata.textureBytes + vfx.textureBytes + borders.textureBytes + spriteEffects.textureBytes;
        try {
            for (int i = 0; i < pages.length; i++) {
                GameplayAtlas.Page page = metadata.pages[i];
                Texture texture = new Texture(Gdx.files.internal("art/" + page.file()));
                pages[i] = texture;
                if (!texture.isManaged() || texture.getWidth() != page.width() || texture.getHeight() != page.height())
                    throw new IllegalArgumentException("Gameplay art page disagrees with metadata: " + page.file());
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            // Supplemental strips borrow unused space in these already managed pages.
            enemyAttacks = new EnemyAttackArt(pages);
            projectiles = new MagicProjectileArt(pages[2]);
            paintedBorders = new PaintedBorderArt(pages, metadata);
            Texture[] groundPages = java.util.Arrays.copyOf(pages, pages.length + 1);
            groundPages[pages.length] = borders.sharedTexture();
            engravedFloor = new EngravedFloorArt(new JsonReader().parse(Gdx.files.internal("art/engraved-floor.json")), metadata, groundPages);
            frames = new TextureRegion[metadata.frames.length];
            materialFrames = new TextureRegion[2][metadata.frames.length];
            for (int i = 0; i < frames.length; i++) {
                GameplayAtlas.Frame frame = metadata.frames[i];
                frames[i] = new TextureRegion(pages[frame.page], frame.x, frame.y, frame.width, frame.height);
                materialFrames[0][i] = EnemyPalette.region(frames[i], EnemyVariant.FROST);
                materialFrames[1][i] = EnemyPalette.region(frames[i], EnemyVariant.DUSK);
            }
            shadow = region("shadow"); glow = region("glow"); pixel = region("white"); soul = region("soul");
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    public TextureRegion frame(int index) { return frames[index]; }
    public TextureRegion frame(int index, EnemyVariant variant) {
        return variant == EnemyVariant.FROST ? materialFrames[0][index]
            : variant == EnemyVariant.DUSK ? materialFrames[1][index] : frames[index];
    }
    public GameplayAtlas.Actor actor(ActorAnimation.Actor actor) { return metadata.actors[actor.ordinal()]; }
    public GameplayAtlas.Region environment(String name) {
        GameplayAtlas.Region region = metadata.regions.get(name);
        if (region == null) throw new IllegalArgumentException("Unknown gameplay region: " + name);
        return region;
    }
    private TextureRegion region(String name) { return frames[environment(name).frame()]; }

    @Override public void dispose() {
        for (Texture page : pages) if (page != null) page.dispose();
        vfx.dispose();
        spriteEffects.dispose();
        borders.dispose();
    }
}
