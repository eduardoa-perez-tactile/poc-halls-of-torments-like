package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Authored attacks and a neutral aura borrow spare gameplay-bank pixels; no texture ownership. */
public final class EnemyAttackArt {
    public enum Layer { ALL, BACK, FRONT }
    public final long textureBytes = 0, retainedPixmapBytes = 0;
    private final Metadata metadata;
    private final Part[][] parts;

    public EnemyAttackArt(Texture[] pages) {
        metadata = new Metadata(new JsonReader().parse(Gdx.files.internal("art/enemy-attacks.json")));
        if (pages.length < metadata.pageWidths.length) throw invalid("missing shared pages");
        for (int i = 0; i < metadata.pageWidths.length; i++)
            if (pages[i] == null || pages[i].getWidth() != metadata.pageWidths[i]
                || pages[i].getHeight() != metadata.pageHeights[i]) throw invalid("shared page dimensions");
        parts = new Part[metadata.frames.length][Layer.values().length];
        for (int i = 0; i < parts.length; i++) {
            Frame f = metadata.frames[i];
            int back = Math.max(0, Math.min(f.height, (int) Math.floor(f.pivotY) - f.top));
            parts[i][Layer.ALL.ordinal()] = part(pages[f.page], f, 0, f.height);
            parts[i][Layer.BACK.ordinal()] = part(pages[f.page], f, 0, back);
            parts[i][Layer.FRONT.ordinal()] = part(pages[f.page], f, back, f.height - back);
        }
    }

    private static Part part(Texture texture, Frame f, int skip, int height) {
        return height <= 0 ? null : new Part(new TextureRegion(texture, f.x, f.y + skip, f.width, height),
            f.left - f.pivotX, f.pivotY - f.top - skip - height, f.width, height);
    }

    public boolean hasActor(ArenaWorld.EnemyKind kind) { return metadata.actors[kind.ordinal()] != null; }
    public float duration(ArenaWorld.EnemyKind kind) { return actor(kind).clips[0].duration; }
    public float actorRadius(ArenaWorld.EnemyKind kind) { return actor(kind).radius; }
    public float auraGroundRadius() { return metadata.auraGroundRadius; }
    public float auraRadius() { return metadata.auraRadius; }
    public float muzzleX(ArenaWorld.EnemyKind kind, ActorAnimation.Facing facing) { return actor(kind).muzzle[facing.ordinal() * 2]; }
    public float muzzleY(ArenaWorld.EnemyKind kind, ActorAnimation.Facing facing) { return actor(kind).muzzle[facing.ordinal() * 2 + 1]; }
    public float slamX(ArenaWorld.EnemyKind kind, ActorAnimation.Facing facing) { return actor(kind).slam[facing.ordinal() * 2]; }
    public float slamY(ArenaWorld.EnemyKind kind, ActorAnimation.Facing facing) { return actor(kind).slam[facing.ordinal() * 2 + 1]; }

    public void drawActor(SpriteBatch batch, ArenaWorld.EnemyKind kind, ActorAnimation.Facing facing,
                          float age, float x, float y, float scale) {
        draw(batch, actor(kind).clips[facing.ordinal()].sample(age, false), x, y, scale, Layer.ALL);
    }

    public void drawAura(SpriteBatch batch, float age, float x, float y, float scale, Layer layer) {
        draw(batch, metadata.aura.sample(age, true), x, y, scale, layer);
    }

    private void draw(SpriteBatch batch, int index, float x, float y, float scale, Layer layer) {
        Part p = parts[index][layer.ordinal()];
        if (p != null) batch.draw(p.region, x + p.left * scale, y + p.bottom * scale,
            p.width * scale, p.height * scale);
    }

    private Actor actor(ArenaWorld.EnemyKind kind) {
        Actor actor = metadata.actors[kind.ordinal()];
        if (actor == null) throw invalid("unsupported actor " + kind);
        return actor;
    }
    private record Part(TextureRegion region, float left, float bottom, int width, int height) {}

    static final class Frame {
        final int page, x, y, width, height, sourceWidth, sourceHeight, left, top;
        final float pivotX, pivotY;
        Frame(JsonValue json, int[] widths, int[] heights) {
            page = json.getInt("page");
            if (page < 0 || page >= widths.length) throw invalid("frame page");
            int[] rect = ints(json, "rect", 4), source = ints(json, "source", 2), offset = ints(json, "offset", 2);
            JsonValue pivot = array(json, "pivot", 2);
            x = rect[0]; y = rect[1]; width = rect[2]; height = rect[3];
            sourceWidth = source[0]; sourceHeight = source[1]; left = offset[0]; top = offset[1];
            pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1);
            if (x < 0 || y < 0 || width <= 0 || height <= 0 || width > widths[page] - x || height > heights[page] - y
                || sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 256 || sourceHeight > 256
                || left < 0 || top < 0 || width > sourceWidth - left || height > sourceHeight - top
                || !Float.isFinite(pivotX) || !Float.isFinite(pivotY) || pivotX < 0 || pivotY < 0
                || pivotX > sourceWidth || pivotY > sourceHeight) throw invalid("frame geometry");
        }
        float radius() {
            float x = Math.max(Math.abs(left - pivotX), Math.abs(left + width - pivotX));
            float y = Math.max(Math.abs(top - pivotY), Math.abs(top + height - pivotY));
            return (float) Math.sqrt(x * x + y * y);
        }
    }

    static final class Clip {
        final int[] frames;
        final float[] ends;
        final float duration;
        Clip(int[] references, int[] durations, Frame[] bank, int width, int height, float pivotX, float pivotY) {
            if (references.length != 8 || durations.length != 8) throw invalid("eight poses required");
            frames = references; ends = new float[frames.length]; float sum = 0;
            for (int i = 0; i < frames.length; i++) {
                if (frames[i] < 0 || frames[i] >= bank.length || durations[i] <= 0 || durations[i] > 1000) throw invalid("clip index/timing");
                Frame f = bank[frames[i]];
                if (f.sourceWidth != width || f.sourceHeight != height || f.pivotX != pivotX || f.pivotY != pivotY)
                    throw invalid("clip changes canvas or root");
                sum += durations[i] / 1000f; ends[i] = sum;
            }
            duration = sum;
        }
        int sample(float age, boolean loop) {
            if (!Float.isFinite(age)) age = 0;
            float time = loop ? ((age % duration) + duration) % duration : Math.max(0, age);
            for (int i = 0; i < ends.length; i++) if (time < ends[i]) return frames[i];
            return frames[frames.length - 1];
        }
    }

    static final class Actor {
        final Clip[] clips = new Clip[ActorAnimation.Facing.values().length];
        final float[] muzzle = new float[8], slam = new float[8];
        final float radius;
        Actor(JsonValue json, Frame[] bank) {
            int[] times = ints(json, "durationsMs", 8); float reach = 0;
            for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) {
                int i = facing.ordinal();
                clips[i] = new Clip(ints(json.require("clips"), facing.name(), 8), times, bank, 96, 128, 48, 112);
                for (int frame : clips[i].frames) reach = Math.max(reach, bank[frame].radius());
                JsonValue m = array(json.require("muzzle"), facing.name(), 2), s = array(json.require("slam"), facing.name(), 2);
                muzzle[i * 2] = m.getFloat(0); muzzle[i * 2 + 1] = m.getFloat(1);
                slam[i * 2] = s.getFloat(0); slam[i * 2 + 1] = s.getFloat(1);
                for (int j = i * 2; j <= i * 2 + 1; j++)
                    if (!Float.isFinite(muzzle[j]) || !Float.isFinite(slam[j]) || Math.abs(muzzle[j]) > 128 || Math.abs(slam[j]) > 128)
                        throw invalid("contact anchor");
            }
            radius = reach;
        }
    }

    /** GL-free validation of immutable source geometry, timing and borrowed-page bounds. */
    static final class Metadata {
        final int[] pageWidths, pageHeights;
        final Frame[] frames;
        final Actor[] actors = new Actor[ArenaWorld.EnemyKind.values().length];
        final Clip aura;
        final float auraGroundRadius, auraRadius;
        Metadata(JsonValue json) {
            if (json.getInt("version") != 1 || json.getLong("textureBytesAdded") != 0) throw invalid("version/resource envelope");
            JsonValue pages = array(json, "pages", 2);
            pageWidths = new int[2]; pageHeights = new int[2];
            for (int i = 0; i < 2; i++) {
                int[] size = ints(pages.get(i), "size", 2); pageWidths[i] = size[0]; pageHeights[i] = size[1];
                String expected = i == 0 ? "gameplay-actors-main.png" : "gameplay-actors-common.png";
                int edge = i == 0 ? 1024 : 512;
                if (!pages.get(i).getString("file").equals(expected) || size[0] != edge || size[1] != edge) throw invalid("borrowed pages");
            }
            JsonValue list = json.require("frames");
            if (!list.isArray() || list.size < 1 || list.size > 104) throw invalid("frame count");
            frames = new Frame[list.size];
            for (int i = 0; i < frames.length; i++) frames[i] = new Frame(list.get(i), pageWidths, pageHeights);
            JsonValue actorList = array(json, "actors", 3);
            for (JsonValue entry = actorList.child; entry != null; entry = entry.next) {
                ArenaWorld.EnemyKind kind = ArenaWorld.EnemyKind.valueOf(entry.getString("kind"));
                if (kind == ArenaWorld.EnemyKind.GHOUL || kind == ArenaWorld.EnemyKind.BOSS || actors[kind.ordinal()] != null)
                    throw invalid("actor set");
                actors[kind.ordinal()] = new Actor(entry, frames);
            }
            for (ArenaWorld.EnemyKind kind : new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST})
                if (actors[kind.ordinal()] == null) throw invalid("missing actor");
            JsonValue a = json.require("aura");
            aura = new Clip(ints(a, "frames", 8), ints(a, "durationsMs", 8), frames, 128, 184, 64, 128);
            auraGroundRadius = a.getFloat("groundRadius");
            float reach = 0; for (int f : aura.frames) reach = Math.max(reach, frames[f].radius()); auraRadius = reach;
            if (!Float.isFinite(auraGroundRadius) || auraGroundRadius <= 0 || auraGroundRadius > auraRadius) throw invalid("aura range");
        }
    }

    private static JsonValue array(JsonValue json, String key, int count) {
        JsonValue v = json.require(key);
        if (!v.isArray() || v.size != count) throw invalid(key);
        return v;
    }
    private static int[] ints(JsonValue json, String key, int count) { return array(json, key, count).asIntArray(); }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("Invalid enemy attack art: " + reason); }
}
