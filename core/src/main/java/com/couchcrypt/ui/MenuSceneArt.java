package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

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
    public final long retainedPixmapBytes = textureBytes;
    private final Texture atlas;
    private final Pixmap basePixels, atlasPixels;
    private final Layer left, right, orb;
    private final TextureRegion particlePoint;
    private final MenuAmbientParticles particles = new MenuAmbientParticles(LEFT_ORB_X, RIGHT_ORB_X, ORB_Y);
    private float clock;
    private Mood mood = Mood.NEUTRAL;
    private boolean disposed;

    public MenuSceneArt() {
        Pixmap room = null, sheet = null, light = null, roomSmall = null, packed = null;
        Texture roomTexture = null, packedTexture = null;
        try {
            room = new Pixmap(Gdx.files.internal("art/menu-crypt-base-v2.png"));
            roomSmall = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
            roomSmall.setBlending(Pixmap.Blending.None);
            roomSmall.setFilter(Pixmap.Filter.BiLinear);
            roomSmall.drawPixmap(room, 0, 0, room.getWidth(), room.getHeight(), 0, 0, WIDTH, HEIGHT);
            room.dispose(); room = null;

            packed = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
            packed.setBlending(Pixmap.Blending.None);
            packed.setFilter(Pixmap.Filter.BiLinear);
            sheet = new Pixmap(Gdx.files.internal("art/menu-crypt-guardians-v2.png"));
            left = pack(sheet, packed, 0, sheet.getWidth() / 2, 2, 2, 252, 764, 64);
            right = pack(sheet, packed, sheet.getWidth() / 2, sheet.getWidth(), 258, 2, 252, 764, 64);
            sheet.dispose(); sheet = null;
            light = new Pixmap(Gdx.files.internal("art/menu-crypt-orb-v2.png"));
            orb = pack(light, packed, 0, light.getWidth(), 2, 770, 252, 252, 8);
            light.dispose(); light = null;
            bakeParticlePoint(packed);
            // Bottom-right, below the ambience point (260,772,32,32), clear of both guardian/orb slots.
            light = new Pixmap(Gdx.files.internal("art/level-up-crest.png"));
            Layer crest = pack(light, packed, 0, light.getWidth(), 298, 810, 212, 212, 8);
            light.dispose(); light = null;

            roomTexture = texture(roomSmall);
            packedTexture = texture(packed);
            left.region = new TextureRegion(packedTexture, left.atlasX, left.atlasY, left.atlasWidth, left.atlasHeight);
            right.region = new TextureRegion(packedTexture, right.atlasX, right.atlasY, right.atlasWidth, right.atlasHeight);
            orb.region = new TextureRegion(packedTexture, orb.atlasX, orb.atlasY, orb.atlasWidth, orb.atlasHeight);
            particlePoint = new TextureRegion(packedTexture, 260, 772, 32, 32);
            levelUpLight = particlePoint;
            levelUpCrest = new TextureRegion(packedTexture, crest.atlasX, crest.atlasY, crest.atlasWidth, crest.atlasHeight);
            base = roomTexture; atlas = packedTexture;
            basePixels = roomSmall; atlasPixels = packed;
        } catch (RuntimeException | Error failure) {
            if (roomTexture != null) roomTexture.dispose();
            if (packedTexture != null) packedTexture.dispose();
            if (roomSmall != null) roomSmall.dispose();
            if (packed != null) packed.dispose();
            throw failure;
        } finally {
            if (room != null) room.dispose();
            if (sheet != null) sheet.dispose();
            if (light != null) light.dispose();
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

    /** A soft white point in unused atlas space; no extra texture or image asset. */
    private static void bakeParticlePoint(Pixmap target) {
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            float dx = (x + .5f - 16f) / 16f, dy = (y + .5f - 16f) / 16f;
            float falloff = Math.max(0, 1 - dx * dx - dy * dy);
            int alpha = Math.round(255 * falloff * falloff * falloff);
            target.drawPixel(260 + x, 772 + y, 0xffffff00 | alpha);
        }
    }

    private static void drawLayer(Batch batch, Layer layer, float x, float y, float width, float height) {
        batch.draw(layer.region, x + layer.x * width, y + layer.y * height,
            layer.width * width, layer.height * height);
    }

    /** Startup-only flattened phone rendition; caller disposes it after PNG encoding. */
    public Pixmap controllerPixmap() {
        Pixmap output = new Pixmap(640, 360, Pixmap.Format.RGBA8888);
        try {
            output.setFilter(Pixmap.Filter.BiLinear);
            output.setBlending(Pixmap.Blending.None);
            output.drawPixmap(basePixels, 0, 0, WIDTH, HEIGHT, 0, 0, 640, 360);
            output.setBlending(Pixmap.Blending.SourceOver);
            composite(output, left, left.x, left.y, left.width, left.height);
            composite(output, right, right.x, right.y, right.width, right.height);
            float haloHeight = ORB_WIDTH * WIDTH / HEIGHT * orb.atlasHeight / orb.atlasWidth;
            composite(output, orb, LEFT_ORB_X - ORB_WIDTH / 2, ORB_Y - haloHeight / 2, ORB_WIDTH, haloHeight);
            composite(output, orb, RIGHT_ORB_X - ORB_WIDTH / 2, ORB_Y - haloHeight / 2, ORB_WIDTH, haloHeight);
            return output;
        } catch (RuntimeException | Error failure) {
            output.dispose(); throw failure;
        }
    }

    private void composite(Pixmap target, Layer layer, float x, float y, float width, float height) {
        target.drawPixmap(atlasPixels, layer.atlasX, layer.atlasY, layer.atlasWidth, layer.atlasHeight,
            Math.round(x * target.getWidth()), Math.round((1 - y - height) * target.getHeight()),
            Math.max(1, Math.round(width * target.getWidth())), Math.max(1, Math.round(height * target.getHeight())));
    }

    private static Layer pack(Pixmap source, Pixmap target, int startX, int endX,
                              int targetX, int targetY, int maxWidth, int maxHeight, int alphaThreshold) {
        source.setBlending(Pixmap.Blending.None);
        int left = endX, top = source.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) for (int x = startX; x < endX; x++) {
            int rgba = source.getPixel(x, y);
            if ((rgba & 255) < 8) source.drawPixel(x, y, 0);
            if ((rgba & 255) < alphaThreshold) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right < left || bottom < top) throw new GdxRuntimeException("Empty menu layer");
        left = Math.max(startX, left - 3); right = Math.min(endX - 1, right + 3);
        top = Math.max(0, top - 3); bottom = Math.min(source.getHeight() - 1, bottom + 3);
        int width = right - left + 1, height = bottom - top + 1;
        float scale = Math.min(1f, Math.min((float) maxWidth / width, (float) maxHeight / height));
        int packedWidth = Math.max(1, Math.round(width * scale));
        int packedHeight = Math.max(1, Math.round(height * scale));
        target.drawPixmap(source, left, top, width, height, targetX, targetY, packedWidth, packedHeight);
        return new Layer(targetX, targetY, packedWidth, packedHeight,
            (float) left / source.getWidth(), 1f - (float) (top + height) / source.getHeight(),
            (float) width / source.getWidth(), (float) height / source.getHeight());
    }

    private static Texture texture(Pixmap pixels) {
        Texture texture = new Texture(new PixmapTextureData(pixels, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static final class Layer {
        final int atlasX, atlasY, atlasWidth, atlasHeight;
        final float x, y, width, height;
        TextureRegion region;
        Layer(int atlasX, int atlasY, int atlasWidth, int atlasHeight, float x, float y, float width, float height) {
            this.atlasX = atlasX; this.atlasY = atlasY; this.atlasWidth = atlasWidth; this.atlasHeight = atlasHeight;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        base.dispose(); atlas.dispose(); basePixels.dispose(); atlasPixels.dispose();
    }
}
