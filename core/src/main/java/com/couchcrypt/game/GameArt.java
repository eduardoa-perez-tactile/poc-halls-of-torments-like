package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;

/** File-backed managed pages; canonical frame geometry is baked offline. */
public final class GameArt implements Disposable {
    public final GameplayAtlas metadata;
    public final TextureRegion shadow, glow, pixel, soul;
    public final TextureRegion[] floors = new TextureRegion[3];
    public final long textureBytes;
    public final long retainedPixmapBytes = 0;
    private final Texture[] pages;
    private final TextureRegion[] frames;

    public GameArt() {
        metadata = new GameplayAtlas(new JsonReader().parse(Gdx.files.internal("art/gameplay.json")));
        textureBytes = metadata.textureBytes;
        pages = new Texture[metadata.pages.length];
        try {
            for (int i = 0; i < pages.length; i++) {
                GameplayAtlas.Page page = metadata.pages[i];
                Texture texture = new Texture(Gdx.files.internal("art/" + page.file()));
                pages[i] = texture;
                if (!texture.isManaged() || texture.getWidth() != page.width() || texture.getHeight() != page.height())
                    throw new IllegalArgumentException("Gameplay art page disagrees with metadata: " + page.file());
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            frames = new TextureRegion[metadata.frames.length];
            for (int i = 0; i < frames.length; i++) {
                GameplayAtlas.Frame frame = metadata.frames[i];
                frames[i] = new TextureRegion(pages[frame.page], frame.x, frame.y, frame.width, frame.height);
            }
            shadow = region("shadow"); glow = region("glow"); pixel = region("white"); soul = region("soul");
            floors[0] = region("floor-intact"); floors[1] = region("floor-chipped"); floors[2] = region("floor-fractured");
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    public TextureRegion frame(int index) { return frames[index]; }
    public GameplayAtlas.Actor actor(ActorAnimation.Actor actor) { return metadata.actors[actor.ordinal()]; }
    public GameplayAtlas.Region environment(String name) {
        GameplayAtlas.Region region = metadata.regions.get(name);
        if (region == null) throw new IllegalArgumentException("Unknown gameplay region: " + name);
        return region;
    }
    private TextureRegion region(String name) { return frames[environment(name).frame()]; }

    @Override public void dispose() {
        for (Texture page : pages) if (page != null) page.dispose();
    }
}
