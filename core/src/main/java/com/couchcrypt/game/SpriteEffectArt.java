package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Approved bitmap effects, rooted once during loading and shared by both cameras. */
public final class SpriteEffectArt implements Disposable {
    public enum Type { TARGET, SOUL_PULSE, WARNING, GROUND_IMPACT, DODGE, SWORD_SWEEP, STORM, CONTACT, BLOCK }
    public enum Layer { ALL, BACK, FRONT }
    public final long textureBytes;
    public final long retainedPixmapBytes = 0;
    private final Texture texture;
    private final Metadata metadata;
    private final Part[][] parts;
    private final float[] vertices = new float[20];

    public SpriteEffectArt() {
        metadata = new Metadata(new JsonReader().parse(Gdx.files.internal("art/sprite-effects.json")));
        textureBytes = metadata.textureBytes;
        Texture loaded = new Texture(Gdx.files.internal("art/" + metadata.file));
        try {
            if (!loaded.isManaged() || loaded.getWidth() != metadata.width || loaded.getHeight() != metadata.height)
                throw invalid("managed page disagrees with metadata");
            loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            parts = new Part[metadata.frames.length][Layer.values().length];
            for (int i = 0; i < parts.length; i++) {
                Frame f = metadata.frames[i];
                int backHeight = MathUtils.clamp((int) Math.floor(f.pivotY) - f.top, 0, f.height);
                parts[i][Layer.ALL.ordinal()] = part(loaded, f, 0, f.height);
                parts[i][Layer.BACK.ordinal()] = part(loaded, f, 0, backHeight);
                parts[i][Layer.FRONT.ordinal()] = part(loaded, f, backHeight, f.height - backHeight);
            }
            texture = loaded;
        } catch (RuntimeException | Error failure) {
            loaded.dispose();
            throw failure;
        }
    }

    private static Part part(Texture texture, Frame f, int skip, int height) {
        return height <= 0 ? null : new Part(new TextureRegion(texture, f.x, f.y + skip, f.width, height),
            f.left - f.pivotX, f.pivotY - f.top - skip - height, f.width, height);
    }

    public float duration(Type type) { return clip(type).duration; }
    public float groundRadius(Type type) { return clip(type).groundRadius; }
    public float referenceScale(Type type) { return clip(type).referenceScale; }
    public float maxRadius(Type type) { return clip(type).radius; }

    /** Progress is normalized to the host-owned effect lifetime; this bank never advances a clock. */
    public void draw(SpriteBatch batch, Type type, float progress, float x, float y,
                     float scale, float rotationDegrees, Layer layer) {
        Part p = parts[clip(type).sample(progress)][layer.ordinal()];
        if (p == null || !(scale > 0f)) return;
        batch.draw(p.region, x + p.left * scale, y + p.bottom * scale,
            -p.left * scale, -p.bottom * scale, p.width * scale, p.height * scale,
            1f, 1f, rotationDegrees);
    }

    /** Rotate in the actual ground plane, rather than tilting an already projected ellipse. */
    public void drawGround(SpriteBatch batch, Type type, float progress, float x, float y,
                           float radius, float angleRadians, Layer layer) {
        Clip clip = clip(type);
        if (!(clip.groundRadius > 0f) || !(radius > 0f)) return;
        drawProjected(batch, type, progress, x, y, radius / clip.groundRadius, angleRadians, layer);
    }

    /** Ground-aligned directional trails use the same projection without needing a circular radius. */
    public void drawProjected(SpriteBatch batch, Type type, float progress, float x, float y,
                              float scale, float angleRadians, Layer layer) {
        Part p = parts[clip(type).sample(progress)][layer.ordinal()];
        if (p == null || !(scale > 0f)) return;
        drawAffine(batch, p, x, y,
            groundX(scale, 0f, angleRadians), groundX(0f, scale, angleRadians),
            groundY(scale, 0f, angleRadians), groundY(0f, scale, angleRadians));
    }

    /** Fit the painted lightning's measured endpoints to a live pair, retaining independent width. */
    public void drawStretch(SpriteBatch batch, Type type, float progress, float x1, float y1,
                            float x2, float y2, float thicknessScale) {
        Clip clip = clip(type);
        if (!clip.hasEndpoints || !(thicknessScale > 0f)) return;
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < .001f) return;
        float sourceX = clip.endX - clip.startX, sourceY = clip.endY - clip.startY;
        float sourceLength = (float) Math.sqrt(sourceX * sourceX + sourceY * sourceY);
        float ux = sourceX / sourceLength, uy = sourceY / sourceLength;
        float lx = dx / sourceLength, ly = dy / sourceLength;
        float vx = -dy / length * thicknessScale, vy = dx / length * thicknessScale;
        float a = lx * ux - vx * uy, b = lx * uy + vx * ux;
        float c = ly * ux - vy * uy, d = ly * uy + vy * ux;
        Part p = parts[clip.sample(progress)][Layer.ALL.ordinal()];
        drawAffine(batch, p, x1 - a * clip.startX - b * clip.startY,
            y1 - c * clip.startX - d * clip.startY, a, b, c, d);
    }

    static float groundX(float x, float y, float angleRadians) {
        return x * MathUtils.cos(angleRadians) - 2f * y * MathUtils.sin(angleRadians);
    }
    static float groundY(float x, float y, float angleRadians) {
        return .5f * x * MathUtils.sin(angleRadians) + y * MathUtils.cos(angleRadians);
    }

    private Clip clip(Type type) { return metadata.clips[type.ordinal()]; }

    private void drawAffine(SpriteBatch batch, Part p, float x, float y, float a, float b, float c, float d) {
        float left = p.left, right = left + p.width, bottom = p.bottom, top = bottom + p.height;
        float color = batch.getPackedColor();
        vertex(0, x + a * left + b * bottom, y + c * left + d * bottom, color, p.region.getU(), p.region.getV2());
        vertex(5, x + a * left + b * top, y + c * left + d * top, color, p.region.getU(), p.region.getV());
        vertex(10, x + a * right + b * top, y + c * right + d * top, color, p.region.getU2(), p.region.getV());
        vertex(15, x + a * right + b * bottom, y + c * right + d * bottom, color, p.region.getU2(), p.region.getV2());
        batch.draw(texture, vertices, 0, vertices.length);
    }

    private void vertex(int offset, float x, float y, float color, float u, float v) {
        vertices[offset] = x; vertices[offset + 1] = y; vertices[offset + 2] = color;
        vertices[offset + 3] = u; vertices[offset + 4] = v;
    }

    @Override public void dispose() { texture.dispose(); }
    private record Part(TextureRegion region, float left, float bottom, int width, int height) {}

    static final class Frame {
        final int x, y, width, height, sourceWidth, sourceHeight, left, top;
        final float pivotX, pivotY;
        Frame(JsonValue json, int pageWidth, int pageHeight) {
            int[] rect = ints(json, "rect", 4), source = ints(json, "source", 2), offset = ints(json, "offset", 2);
            float[] pivot = floats(json, "pivot", 2);
            x = rect[0]; y = rect[1]; width = rect[2]; height = rect[3];
            sourceWidth = source[0]; sourceHeight = source[1]; left = offset[0]; top = offset[1];
            pivotX = pivot[0]; pivotY = pivot[1];
            if (x < 0 || y < 0 || width <= 0 || height <= 0 || width > pageWidth - x || height > pageHeight - y
                || sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 512 || sourceHeight > 512
                || left < 0 || top < 0 || width > sourceWidth - left || height > sourceHeight - top
                || !Float.isFinite(pivotX) || !Float.isFinite(pivotY) || pivotX < 0 || pivotY < 0
                || pivotX > sourceWidth || pivotY > sourceHeight) throw invalid("frame bounds or pivot");
        }
    }

    static final class Clip {
        final int[] frames;
        final float[] ends;
        final float duration, radius, groundRadius, referenceScale;
        final boolean hasEndpoints;
        final float startX, startY, endX, endY;
        Clip(JsonValue json, Frame[] bank) {
            JsonValue indices = json.require("frames");
            if (!indices.isArray() || indices.size < 1 || indices.size > 8) throw invalid("clip frame count");
            frames = indices.asIntArray();
            int[] times = ints(json, "durationsMs", frames.length);
            groundRadius = json.getFloat("groundRadius", 0f);
            referenceScale = json.getFloat("referenceScale");
            if (!Float.isFinite(groundRadius) || groundRadius < 0f || !Float.isFinite(referenceScale)
                || referenceScale <= 0f || referenceScale > 32f) throw invalid("clip scales");
            ends = new float[frames.length]; float sum = 0f, reach = 0f;
            Frame first = null;
            for (int i = 0; i < frames.length; i++) {
                if (frames[i] < 0 || frames[i] >= bank.length || times[i] <= 0 || times[i] > 1000) throw invalid("clip timing or index");
                Frame f = bank[frames[i]];
                if (first == null) first = f;
                else if (f.pivotX != first.pivotX || f.pivotY != first.pivotY
                    || f.sourceWidth != first.sourceWidth || f.sourceHeight != first.sourceHeight) throw invalid("clip ground anchor drifts");
                sum += times[i] / 1000f; ends[i] = sum;
                float x = Math.max(Math.abs(f.left - f.pivotX), Math.abs(f.left + f.width - f.pivotX));
                float y = Math.max(Math.abs(f.top - f.pivotY), Math.abs(f.top + f.height - f.pivotY));
                reach = Math.max(reach, (float) Math.sqrt(x * x + y * y));
            }
            duration = sum; radius = reach;
            JsonValue endpoints = json.get("endpoints");
            hasEndpoints = endpoints != null;
            if (hasEndpoints) {
                if (!endpoints.isArray() || endpoints.size != 2) throw invalid("endpoints");
                float[] a = floatArray(endpoints.get(0), 2), b = floatArray(endpoints.get(1), 2);
                for (float value : a) if (!Float.isFinite(value)) throw invalid("endpoints");
                for (float value : b) if (!Float.isFinite(value)) throw invalid("endpoints");
                if (a[0] < 0 || b[0] < 0 || a[1] < 0 || b[1] < 0 || a[0] > first.sourceWidth
                    || b[0] > first.sourceWidth || a[1] > first.sourceHeight || b[1] > first.sourceHeight
                    || Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < .01f) throw invalid("endpoints");
                startX = a[0] - first.pivotX; startY = first.pivotY - a[1];
                endX = b[0] - first.pivotX; endY = first.pivotY - b[1];
            } else startX = startY = endX = endY = 0f;
        }

        int sample(float progress) {
            if (!Float.isFinite(progress) || progress < 0f) progress = 0f;
            if (progress >= 1f) return frames[frames.length - 1];
            float time = progress * duration;
            for (int i = 0; i < ends.length; i++) if (time < ends[i]) return frames[i];
            return frames[frames.length - 1];
        }
    }

    /** Parsing and timing validation remain available without a graphics context. */
    static final class Metadata {
        final String file;
        final int width, height;
        final long textureBytes;
        final Frame[] frames;
        final Clip[] clips = new Clip[Type.values().length];
        Metadata(JsonValue json) {
            if (json.getInt("version") != 1) throw invalid("version");
            file = json.getString("file"); int[] size = ints(json, "size", 2); width = size[0]; height = size[1];
            if (!file.matches("[A-Za-z0-9_-]+\\.png") || width <= 0 || height <= 0 || width > 512 || height > 512) throw invalid("page");
            textureBytes = (long) width * height * 4;
            JsonValue list = json.require("frames");
            if (!list.isArray() || list.size < Type.values().length || list.size > Type.values().length * 8)
                throw invalid("frame count");
            frames = new Frame[list.size];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = new Frame(list.get(i), width, height);
                for (int j = 0; j < i; j++) {
                    Frame a = frames[i], b = frames[j];
                    if (a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height)
                        throw invalid("overlapping frames");
                }
            }
            JsonValue animations = json.require("animations");
            if (!animations.isArray() || animations.size != clips.length) throw invalid("animation count");
            boolean[] referenced = new boolean[frames.length];
            for (JsonValue clip = animations.child; clip != null; clip = clip.next) {
                Type type;
                try { type = Type.valueOf(clip.getString("name")); }
                catch (IllegalArgumentException failure) { throw invalid("unknown animation"); }
                if (clips[type.ordinal()] != null) throw invalid("duplicate animation");
                Clip parsed = new Clip(clip, frames); clips[type.ordinal()] = parsed;
                for (int index : parsed.frames) {
                    if (referenced[index]) throw invalid("duplicate frame reference");
                    referenced[index] = true;
                }
                if ((type == Type.TARGET || type == Type.SOUL_PULSE || type == Type.WARNING
                    || type == Type.GROUND_IMPACT || type == Type.SWORD_SWEEP) && !(parsed.groundRadius > 0))
                    throw invalid("missing ground radius");
                if (type == Type.STORM && !parsed.hasEndpoints) throw invalid("missing lightning endpoints");
            }
            for (Clip clip : clips) if (clip == null) throw invalid("missing animation");
            for (boolean used : referenced) if (!used) throw invalid("unreferenced frame");
        }
    }

    private static int[] ints(JsonValue json, String key, int length) {
        JsonValue value = json.require(key);
        if (!value.isArray() || value.size != length) throw invalid(key);
        return value.asIntArray();
    }
    private static float[] floats(JsonValue json, String key, int length) { return floatArray(json.require(key), length); }
    private static float[] floatArray(JsonValue value, int length) {
        if (!value.isArray() || value.size != length) throw invalid("float array");
        return value.asFloatArray();
    }
    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid sprite effect art: " + reason);
    }
}
