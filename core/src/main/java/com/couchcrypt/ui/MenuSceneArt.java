package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** One reusable crypt: an opaque room, two independent guardians and two quiet soul lights. */
public final class MenuSceneArt extends BaseDrawable implements Disposable {
    private static final int WIDTH = 1536, HEIGHT = 864, ATLAS_WIDTH = 512, ATLAS_HEIGHT = 1024;
    // Normalized scene positions use a bottom-left origin, matching Scene2D.
    private static final float LEFT_ORB_X = 198f / 1672, RIGHT_ORB_X = 1473f / 1672, ORB_Y = 1f - 608f / 941;
    private static final float ORB_WIDTH = .036f;

    public enum Mood { NEUTRAL, VICTORY, DEFEAT }

    public final Texture base;
    /** Level-up ornaments borrow unused atlas space; menu layers and texture dimensions stay fixed. */
    public final TextureRegion levelUpCrest, levelUpLight;
    public final long textureBytes = ((long) WIDTH * HEIGHT + (long) ATLAS_WIDTH * ATLAS_HEIGHT) * 4;
    public final long retainedPixmapBytes = 0; // Managed textures restore from the small baked PNGs.
    private final Texture atlas;
    private final Layer left, right, orb;
    private final TextureRegion particlePoint;
    private final MenuAmbientParticles particles = new MenuAmbientParticles(LEFT_ORB_X, RIGHT_ORB_X, ORB_Y);
    private float clock;
    private Mood mood = Mood.NEUTRAL;
    private boolean disposed;

    public MenuSceneArt() {
        JsonValue layers = new JsonReader().parse(Gdx.files.internal("art/runtime-ui/menu-layers.json")).require("layers");
        Texture roomTexture = null, packedTexture = null;
        try {
            roomTexture = texture("art/runtime-ui/menu-base.png", WIDTH, HEIGHT);
            packedTexture = texture("art/runtime-ui/menu-atlas.png", ATLAS_WIDTH, ATLAS_HEIGHT);
            left = new Layer(layers.require("left"), packedTexture);
            right = new Layer(layers.require("right"), packedTexture);
            orb = new Layer(layers.require("orb"), packedTexture);
            Layer crest = new Layer(layers.require("crest"), packedTexture);
            particlePoint = new TextureRegion(packedTexture, 260, 772, 32, 32);
            levelUpLight = particlePoint;
            levelUpCrest = crest.region;
            base = roomTexture; atlas = packedTexture;
        } catch (RuntimeException | Error failure) {
            if (roomTexture != null) roomTexture.dispose();
            if (packedTexture != null) packedTexture.dispose();
            throw failure;
        }
    }

    public void setMood(Mood mood) {
        this.mood = mood == null ? Mood.NEUTRAL : mood;
    }

    /** Call once for a displayed menu frame; geometry and atlas regions never change. */
    public void advance(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        float step = Math.min(delta, .1f);
        clock = (clock + step) % 32f;
        particles.advance(step);
    }

    @Override public void draw(Batch batch, float x, float y, float width, float height) {
        batch.draw(base, x, y, width, height);
        particles.drawDust(batch, particlePoint, x, y, width, height);
        drawLayer(batch, left, x, y, width, height);
        drawLayer(batch, right, x, y, width, height);
        Color color = batch.getColor();
        float red = color.r, green = color.g, blue = color.b, alpha = color.a;
        float strength = mood == Mood.DEFEAT ? .88f : 1f;
        float tintGreen = mood == Mood.DEFEAT ? .94f : 1f;
        float tintBlue = mood == Mood.VICTORY ? .94f : 1f;
        float haloHeight = ORB_WIDTH * WIDTH / HEIGHT * orb.atlasHeight / orb.atlasWidth;
        batch.setColor(red, green * tintGreen, blue * tintBlue,
            alpha * strength * (.97f + .03f * MathUtils.sin(clock * MathUtils.PI2 / 8f)));
        batch.draw(orb.region, x + (LEFT_ORB_X - ORB_WIDTH / 2) * width,
            y + (ORB_Y - haloHeight / 2) * height, ORB_WIDTH * width, haloHeight * height);
        batch.setColor(red, green * tintGreen, blue * tintBlue,
            alpha * strength * (.97f + .03f * MathUtils.sin(clock * MathUtils.PI2 / 8f + 1.8f)));
        batch.draw(orb.region, x + (RIGHT_ORB_X - ORB_WIDTH / 2) * width,
            y + (ORB_Y - haloHeight / 2) * height, ORB_WIDTH * width, haloHeight * height);
        batch.setColor(red, green, blue, alpha);
        particles.drawOrbMotes(batch, particlePoint, x, y, width, height);
    }

    private static void drawLayer(Batch batch, Layer layer, float x, float y, float width, float height) {
        batch.draw(layer.region, x + layer.x * width, y + layer.y * height,
            layer.width * width, layer.height * height);
    }

    private static Texture texture(String path, int width, int height) {
        Texture texture = new Texture(Gdx.files.internal(path), false);
        try {
            if (texture.getWidth() != width || texture.getHeight() != height || !texture.isManaged())
                throw new GdxRuntimeException("Menu texture must be managed with its fixed dimensions: " + path);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } catch (RuntimeException | Error failure) {
            texture.dispose();
            throw failure;
        }
    }

    private static final class Layer {
        final int atlasX, atlasY, atlasWidth, atlasHeight;
        final float x, y, width, height;
        final TextureRegion region;
        Layer(JsonValue value, Texture texture) {
            atlasX = value.getInt("atlasX"); atlasY = value.getInt("atlasY");
            atlasWidth = value.getInt("atlasWidth"); atlasHeight = value.getInt("atlasHeight");
            x = value.getFloat("x"); y = value.getFloat("y");
            width = value.getFloat("width"); height = value.getFloat("height");
            if (atlasX < 0 || atlasY < 0 || atlasWidth <= 0 || atlasHeight <= 0
                || atlasX > ATLAS_WIDTH - atlasWidth || atlasY > ATLAS_HEIGHT - atlasHeight
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1.000001f || y + height > 1.000001f)
                throw new GdxRuntimeException("Invalid baked menu layer: " + value.name());
            region = new TextureRegion(texture, atlasX, atlasY, atlasWidth, atlasHeight);
        }
    }

    /** Same-handle reload exercises the file-backed restoration path without leaking old handles. */
    public void reloadFileTextures() {
        base.load(base.getTextureData()); atlas.load(atlas.getTextureData());
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        base.dispose(); atlas.dispose();
    }
}
