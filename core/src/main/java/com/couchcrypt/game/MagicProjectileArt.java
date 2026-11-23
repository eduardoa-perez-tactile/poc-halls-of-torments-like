package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Animated magic missiles borrowing spare pixels in the managed gameplay environment page. */
public final class MagicProjectileArt {
    public enum Type { RANGER, CULTIST, SOVEREIGN }
    /** The sprites borrow an existing page, so this bank adds no GPU texture or decoded Pixmap bytes. */
    public final long textureBytes = 0, retainedPixmapBytes = 0;
    private final Metadata metadata;
    private final Part[] parts;

    public MagicProjectileArt(Texture environmentPage) {
        metadata = new Metadata(new JsonReader().parse(Gdx.files.internal("art/magic-projectiles.json")));
        if (!environmentPage.isManaged() || environmentPage.getWidth() != metadata.width
            || environmentPage.getHeight() != metadata.height)
            throw invalid("borrowed environment page disagrees with metadata");
        parts = new Part[metadata.frames.length];
        for (int i = 0; i < parts.length; i++) {
            Frame f = metadata.frames[i];
            parts[i] = new Part(new TextureRegion(environmentPage, f.x, f.y, f.width, f.height),
                f.left - f.pivotX, f.pivotY - f.top - f.height, f.width, f.height);
        }
    }

    public float duration(Type type) { return metadata.clips[type.ordinal()].duration; }
    public float maxRadius(Type type) { return metadata.clips[type.ordinal()].radius; }

    /** Local +X points at the bright head; rotation follows the projected world velocity. */
    public void draw(SpriteBatch batch, Type type, float age, float x, float y,
                     float scale, float rotationDegrees) {
        Part p = parts[metadata.clips[type.ordinal()].sample(age)];
        batch.draw(p.region, x + p.left * scale, y + p.bottom * scale,
            -p.left * scale, -p.bottom * scale, p.width * scale, p.height * scale,
            1, 1, rotationDegrees);
    }

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
                || source[0] != 96 || source[1] != 128 || left < 0 || top < 0
                || width > source[0] - left || height > source[1] - top
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
            float pivotX = Float.NaN, pivotY = Float.NaN;
            for (int i = 0; i < 8; i++) {
                if (frames[i] < 0 || frames[i] >= bank.length || times[i] <= 0 || times[i] > 1000) throw invalid("clip");
                sum += times[i] / 1000f; ends[i] = sum;
                Frame f = bank[frames[i]];
                if (i == 0) { pivotX = f.pivotX; pivotY = f.pivotY; }
                else if (f.pivotX != pivotX || f.pivotY != pivotY) throw invalid("head pivot drifts");
                float x = Math.max(Math.abs(f.left - f.pivotX), Math.abs(f.left + f.width - f.pivotX));
                float y = Math.max(Math.abs(f.top - f.pivotY), Math.abs(f.top + f.height - f.pivotY));
                reach = Math.max(reach, (float) Math.sqrt(x * x + y * y));
            }
            duration = sum; radius = reach;
        }
        int sample(float age) {
            if (!Float.isFinite(age) || age < 0) age = 0;
            float phase = age % duration;
            for (int i = 0; i < ends.length; i++) if (phase < ends[i]) return frames[i];
            return frames[0];
        }
    }

    static final class Metadata {
        final String file;
        final int width, height;
        final Frame[] frames;
        final Clip[] clips = new Clip[Type.values().length];
        Metadata(JsonValue json) {
            if (json.getInt("version") != 1 || json.getInt("borrowedPage") != 2) throw invalid("version or borrowed page");
            file = json.getString("file"); int[] size = array(json, "size", 2); width = size[0]; height = size[1];
            if (!file.equals("gameplay-environment.png") || width != 512 || height != 512) throw invalid("page");
            JsonValue list = json.require("frames");
            if (!list.isArray() || list.size != 24) throw invalid("24 projectile frames required");
            frames = new Frame[list.size];
            for (int i = 0; i < frames.length; i++) frames[i] = new Frame(list.get(i), width, height);
            for (int i = 0; i < frames.length; i++) for (int j = 0; j < i; j++) {
                Frame a = frames[i], b = frames[j];
                if (a.x < b.x + b.width && b.x < a.x + a.width
                    && a.y < b.y + b.height && b.y < a.y + a.height) throw invalid("overlapping frames");
            }
            for (JsonValue clip = json.require("animations").child; clip != null; clip = clip.next) {
                Type name = Type.valueOf(clip.getString("name"));
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
    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid magic projectile art: " + reason);
    }
}
