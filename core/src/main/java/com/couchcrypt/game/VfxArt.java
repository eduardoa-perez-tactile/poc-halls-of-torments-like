package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Four baked one-shot sprite animations, one file-backed page and no retained Pixmaps. */
public final class VfxArt implements Disposable {
    public enum Animation { SPAWN, LEVEL_UP, SWORD_HIT, BOLT_HIT }
    public enum Layer { ALL, BACK, FRONT }
    public final long textureBytes;
    private final Texture texture;
    private final Metadata metadata;
    private final Part[][] parts;
    private final float[] vertices = new float[20];

    public VfxArt() {
        metadata = new Metadata(new JsonReader().parse(Gdx.files.internal("art/combat-vfx.json")));
        textureBytes = metadata.textureBytes;
        Texture loaded = new Texture(Gdx.files.internal("art/" + metadata.file));
        try {
            if (!loaded.isManaged() || loaded.getWidth() != metadata.width || loaded.getHeight() != metadata.height)
                throw new IllegalArgumentException("VFX page disagrees with metadata");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            parts = new Part[metadata.frames.length][3];
            for (int i = 0; i < parts.length; i++) {
                Frame f = metadata.frames[i];
                int backHeight = Math.max(0, Math.min(f.height, (int) Math.floor(f.pivotY) - f.top));
                parts[i][Layer.ALL.ordinal()] = part(loaded, f, 0, f.height);
                parts[i][Layer.BACK.ordinal()] = part(loaded, f, 0, backHeight);
                parts[i][Layer.FRONT.ordinal()] = part(loaded, f, backHeight, f.height - backHeight);
            }
            texture = loaded;
        } catch (RuntimeException | Error failure) { loaded.dispose(); throw failure; }
    }

    private static Part part(Texture texture, Frame f, int skip, int height) {
        if (height <= 0) return null;
        return new Part(new TextureRegion(texture, f.x, f.y + skip, f.width, height),
            f.left - f.pivotX, f.pivotY - f.top - skip - height, f.width, height);
    }

    public float duration(Animation animation) { return metadata.clips[animation.ordinal()].duration; }
    public float maxRadius(Animation animation) { return metadata.clips[animation.ordinal()].radius; }

    public void draw(SpriteBatch batch, Animation animation, float age, float x, float y,
                     float scale, float rotationDegrees, Layer layer) {
        int index = metadata.clips[animation.ordinal()].sample(age);
        if (index < 0) return;
        Part p = parts[index][layer.ordinal()];
        if (p == null) return;
        batch.draw(p.region, x + p.left * scale, y + p.bottom * scale,
            -p.left * scale, -p.bottom * scale, p.width * scale, p.height * scale,
            1, 1, rotationDegrees);
    }

    /** A small directional contact sweep lies in the same plane as a weapon, at its hit height. */
    public void drawProjected(SpriteBatch batch, Animation animation, float age, float x, float y,
                              float scale, float angleRadians) {
        int index = metadata.clips[animation.ordinal()].sample(age);
        if (index < 0) return;
        Part p = parts[index][Layer.ALL.ordinal()];
        if (p == null) return;
        float c = (float) Math.cos(angleRadians), s = (float) Math.sin(angleRadians);
        float left = p.left * scale, right = (p.left + p.width) * scale;
        float bottom = p.bottom * scale, top = (p.bottom + p.height) * scale;
        float color = batch.getPackedColor();
        projectedVertex(0, x, y, left, bottom, c, s, color, p.region.getU(), p.region.getV2());
        projectedVertex(5, x, y, left, top, c, s, color, p.region.getU(), p.region.getV());
        projectedVertex(10, x, y, right, top, c, s, color, p.region.getU2(), p.region.getV());
        projectedVertex(15, x, y, right, bottom, c, s, color, p.region.getU2(), p.region.getV2());
        batch.draw(texture, vertices, 0, vertices.length);
    }

    private void projectedVertex(int at, float x, float y, float u, float v, float c, float s,
                                 float color, float textureU, float textureV) {
        vertices[at] = x + u * c - v * s;
        vertices[at + 1] = y + .5f * (u * s + v * c);
        vertices[at + 2] = color; vertices[at + 3] = textureU; vertices[at + 4] = textureV;
    }

    @Override public void dispose() { texture.dispose(); }
    private record Part(TextureRegion region, float left, float bottom, int width, int height) {}

    static final class Frame {
        final int x, y, width, height, left, top;
        final float pivotX, pivotY;
        Frame(JsonValue json, int pageWidth, int pageHeight) {
            int[] r = array(json, "rect", 4), source = array(json, "source", 2), offset = array(json, "offset", 2);
            JsonValue pivot = json.require("pivot");
            if (!pivot.isArray() || pivot.size != 2) throw invalid("pivot");
            x = r[0]; y = r[1]; width = r[2]; height = r[3]; left = offset[0]; top = offset[1];
            pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1);
            if (x < 0 || y < 0 || width <= 0 || height <= 0 || width > pageWidth - x || height > pageHeight - y
                || source[0] <= 0 || source[1] <= 0 || source[0] > 512 || source[1] > 512
                || left < 0 || top < 0 || width > source[0] - left || height > source[1] - top
                || !Float.isFinite(pivotX) || !Float.isFinite(pivotY)
                || pivotX < 0 || pivotY < 0 || pivotX > source[0] || pivotY > source[1]) throw invalid("frame bounds");
        }
    }

    static final class Clip {
        final int[] frames;
        final float[] ends;
        final float duration, radius;
        Clip(JsonValue json, Frame[] bank) {
            frames = array(json, "frames", 8);
            int[] times = array(json, "durationsMs", 8);
            ends = new float[8]; float sum = 0, reach = 0;
            for (int i = 0; i < 8; i++) {
                if (frames[i] < 0 || frames[i] >= bank.length || times[i] <= 0 || times[i] > 1000) throw invalid("clip");
                sum += times[i] / 1000f; ends[i] = sum;
                Frame f = bank[frames[i]];
                float x = Math.max(Math.abs(f.left - f.pivotX), Math.abs(f.left + f.width - f.pivotX));
                float y = Math.max(Math.abs(f.top - f.pivotY), Math.abs(f.top + f.height - f.pivotY));
                reach = Math.max(reach, (float) Math.sqrt(x * x + y * y));
            }
            duration = sum; radius = reach;
        }
        /** One-shots clear after their last frame instead of retaining a residual glint forever. */
        int sample(float age) {
            if (!Float.isFinite(age) || age < 0 || age >= duration) return -1;
            for (int i = 0; i < ends.length; i++) if (age < ends[i]) return frames[i];
            return -1;
        }
    }

    /** GL-free validation and timing, also used by focused metadata checks. */
    static final class Metadata {
        final String file;
        final int width, height;
        final long textureBytes;
        final Frame[] frames;
        final Clip[] clips = new Clip[Animation.values().length];
        Metadata(JsonValue json) {
            if (json.getInt("version") != 1) throw invalid("version");
            file = json.getString("file"); int[] size = array(json, "size", 2); width = size[0]; height = size[1];
            if (!file.matches("[A-Za-z0-9_-]+\\.png") || width <= 0 || height <= 0 || width > 512 || height > 512)
                throw invalid("page");
            textureBytes = (long) width * height * 4;
            JsonValue list = json.require("frames");
            if (!list.isArray() || list.size != 32) throw invalid("32 sprite frames required");
            frames = new Frame[list.size];
            for (int i = 0; i < frames.length; i++) frames[i] = new Frame(list.get(i), width, height);
            for (JsonValue clip = json.require("animations").child; clip != null; clip = clip.next) {
                Animation name = Animation.valueOf(clip.getString("name"));
                if (clips[name.ordinal()] != null) throw invalid("duplicate animation");
                clips[name.ordinal()] = new Clip(clip, frames);
            }
            for (Clip clip : clips) if (clip == null) throw invalid("missing animation");
        }
    }
    private static int[] array(JsonValue json, String key, int length) {
        JsonValue value = json.require(key);
        if (!value.isArray() || value.size != length) throw invalid(key);
        return value.asIntArray();
    }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("Invalid combat VFX: " + reason); }
}
