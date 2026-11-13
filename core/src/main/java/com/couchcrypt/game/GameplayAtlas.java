package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import java.util.Locale;

/** Validated offline atlas metadata. It neither loads GL textures nor fits artwork. */
public final class GameplayAtlas {
    public static final long MAX_TEXTURE_BYTES = 6L * 1_048_576;
    public static final String[] REQUIRED_REGIONS = {"floor-intact", "floor-chipped", "floor-fractured",
        "wall-ne", "wall-nw", "corner", "arch", "pillar", "sarcophagus", "rubble", "urn", "relic",
        "obelisk", "shrine", "orb", "shadow", "glow", "soul", "white"};

    public record Page(String file, int width, int height) {}

    public static final class Frame {
        public final int page, x, y, width, height, sourceWidth, sourceHeight;
        public final float pivotX, pivotY, left, bottom;
        private Frame(JsonValue json, Page[] pages) {
            page = json.getInt("page");
            if (page < 0 || page >= pages.length) throw invalid("Unknown frame page");
            int[] rectangle = integers(json, "rect", 4), source = integers(json, "source", 2);
            int[] offset = integers(json, "offset", 2);
            JsonValue pivot = array(json, "pivot", 2);
            x = rectangle[0]; y = rectangle[1]; width = rectangle[2]; height = rectangle[3];
            sourceWidth = source[0]; sourceHeight = source[1];
            pivotX = pivot.getFloat(0); pivotY = pivot.getFloat(1);
            Page p = pages[page];
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                || width > p.width - x || height > p.height - y)
                throw invalid("Frame rectangle exceeds its page");
            if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 1024 || sourceHeight > 1024
                || offset[0] < 0 || offset[1] < 0
                || width > sourceWidth - offset[0] || height > sourceHeight - offset[1])
                throw invalid("Frame trim exceeds its original canvas");
            if (!Float.isFinite(pivotX) || !Float.isFinite(pivotY)
                || pivotX < 0 || pivotY < 0 || pivotX > sourceWidth || pivotY > sourceHeight)
                throw invalid("Invalid ground pivot");
            left = offset[0] - pivotX;
            bottom = pivotY - offset[1] - height;
        }
    }

    public static final class Clip {
        public final int[] frames;
        public final boolean loop;
        public final float duration;
        private final float[] ends;
        private Clip(JsonValue json, Frame[] bank, int canvasWidth, int canvasHeight) {
            JsonValue references = json.get("frames");
            if (references == null || !references.isArray() || references.size < 1 || references.size > 32)
                throw invalid("A clip needs 1–32 frames");
            frames = integers(json, "frames", references.size);
            JsonValue times = array(json, "durationsMs", frames.length);
            ends = new float[frames.length];
            float sum = 0;
            for (int i = 0; i < frames.length; i++) {
                if (frames[i] < 0 || frames[i] >= bank.length) throw invalid("Unknown clip frame");
                Frame frame = bank[frames[i]];
                if (frame.sourceWidth != canvasWidth || frame.sourceHeight != canvasHeight)
                    throw invalid("Clip changes the actor's original canvas");
                float milliseconds = times.getFloat(i);
                if (!Float.isFinite(milliseconds) || milliseconds <= 0 || milliseconds > 10_000)
                    throw invalid("Invalid frame duration");
                sum += milliseconds / 1000f; ends[i] = sum;
            }
            duration = sum; loop = json.getBoolean("loop");
        }

        /** Negative phases intentionally play a looping retreat gait backwards. */
        public int frame(float phase) {
            if (!Float.isFinite(phase)) phase = 0;
            float time = loop ? ((phase % duration) + duration) % duration : Math.max(0, phase);
            for (int i = 0; i < ends.length; i++) if (time < ends[i]) return frames[i];
            return frames[frames.length - 1];
        }
    }

    public static final class Actor {
        public final ActorAnimation.Actor id;
        public final int canvasWidth, canvasHeight;
        public final float worldHeight, pixelScale, maxLeft, maxRight, maxAbove, maxBelow;
        private final Clip[][] clips = new Clip[ActorAnimation.State.values().length][ActorAnimation.Facing.values().length];

        private Actor(JsonValue json, Frame[] bank) {
            id = ActorAnimation.Actor.valueOf(json.getString("id").toUpperCase(Locale.ROOT));
            int[] canvas = integers(json, "canvas", 2);
            canvasWidth = canvas[0]; canvasHeight = canvas[1]; worldHeight = json.getFloat("worldHeight");
            if (canvasWidth <= 0 || canvasHeight <= 0 || !Float.isFinite(worldHeight)
                || worldHeight <= 0 || worldHeight > 1024) throw invalid("Invalid actor scale");
            pixelScale = worldHeight / canvasHeight;
            JsonValue states = json.require("clips");
            float left = 0, right = 0, above = 0, below = 0;
            for (JsonValue state = states.child; state != null; state = state.next) {
                ActorAnimation.State stateId = ActorAnimation.State.valueOf(state.name.toUpperCase(Locale.ROOT));
                for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) {
                    Clip clip = new Clip(state.require(facing.name()), bank, canvasWidth, canvasHeight);
                    boolean looping = stateId == ActorAnimation.State.IDLE || stateId == ActorAnimation.State.MOVE
                        || stateId == ActorAnimation.State.WINDUP;
                    if (clip.loop != looping) throw invalid("Clip loop policy disagrees with its action");
                    clips[stateId.ordinal()][facing.ordinal()] = clip;
                    for (int index : clip.frames) {
                        Frame frame = bank[index];
                        left = Math.max(left, -frame.left * pixelScale);
                        right = Math.max(right, (frame.left + frame.width) * pixelScale);
                        above = Math.max(above, (frame.bottom + frame.height) * pixelScale);
                        below = Math.max(below, -frame.bottom * pixelScale);
                    }
                }
            }
            require(ActorAnimation.State.IDLE); require(ActorAnimation.State.MOVE);
            if (id == ActorAnimation.Actor.WARDEN || id == ActorAnimation.Actor.RANGER) {
                require(ActorAnimation.State.ATTACK); require(ActorAnimation.State.DODGE);
                require(ActorAnimation.State.HIT); require(ActorAnimation.State.DOWNED);
            } else if (id == ActorAnimation.Actor.CULTIST || id == ActorAnimation.Actor.SOVEREIGN) {
                require(ActorAnimation.State.ATTACK); require(ActorAnimation.State.WINDUP);
            }
            maxLeft = left; maxRight = right; maxAbove = above; maxBelow = below;
        }

        private void require(ActorAnimation.State state) {
            for (ActorAnimation.Facing facing : ActorAnimation.Facing.values())
                if (clips[state.ordinal()][facing.ordinal()] == null)
                    throw invalid("Missing " + id + " " + state + " " + facing);
        }

        public Clip clip(ActorAnimation.State state, ActorAnimation.Facing facing) {
            Clip result = clips[state.ordinal()][facing.ordinal()];
            if (result == null) throw invalid("Unsupported actor action: " + id + " " + state);
            return result;
        }
    }

    public record Region(int frame, float worldHeight) {}

    public final Page[] pages;
    public final Frame[] frames;
    public final Actor[] actors = new Actor[ActorAnimation.Actor.values().length];
    public final ObjectMap<String, Region> regions = new ObjectMap<>();
    public final long textureBytes;

    public GameplayAtlas(JsonValue root) {
        if (root.getInt("version") != 1) throw invalid("Unsupported atlas version");
        if (root.getBoolean("preview", false)) throw invalid("An incomplete preview atlas cannot be loaded by the game");
        JsonValue pageList = root.require("pages");
        if (!pageList.isArray() || pageList.size < 1 || pageList.size > 3) throw invalid("Invalid page count");
        pages = new Page[pageList.size]; long bytes = 0;
        for (int i = 0; i < pages.length; i++) {
            JsonValue page = pageList.get(i); String file = page.getString("file");
            int[] size = integers(page, "size", 2);
            if (!file.matches("[A-Za-z0-9_-]+\\.png") || size[0] <= 0 || size[1] <= 0
                || size[0] > 1024 || size[1] > 1024) throw invalid("Invalid atlas page");
            pages[i] = new Page(file, size[0], size[1]); bytes += (long) size[0] * size[1] * 4;
        }
        if (bytes > MAX_TEXTURE_BYTES) throw invalid("Gameplay atlas exceeds its 6 MiB envelope");
        textureBytes = bytes;
        JsonValue frameList = root.require("frames");
        if (!frameList.isArray() || frameList.size < 1 || frameList.size > 512) throw invalid("Invalid frame count");
        frames = new Frame[frameList.size];
        for (int i = 0; i < frames.length; i++) frames[i] = new Frame(frameList.get(i), pages);
        JsonValue actorList = root.require("actors");
        if (!actorList.isArray() || actorList.size != actors.length) throw invalid("Seven real actor banks are required");
        for (JsonValue entry = actorList.child; entry != null; entry = entry.next) {
            Actor actor = new Actor(entry, frames);
            if (actors[actor.id.ordinal()] != null) throw invalid("Duplicate actor bank");
            actors[actor.id.ordinal()] = actor;
        }
        for (Actor actor : actors) if (actor == null) throw invalid("Missing actor bank");
        for (JsonValue entry = root.require("regions").child; entry != null; entry = entry.next) {
            int frame = entry.getInt("frame"); float height = entry.getFloat("worldHeight");
            if (frame < 0 || frame >= frames.length || !Float.isFinite(height) || height <= 0)
                throw invalid("Invalid environment region");
            if (regions.put(entry.name, new Region(frame, height)) != null) throw invalid("Duplicate environment region");
        }
        for (String name : REQUIRED_REGIONS) if (!regions.containsKey(name)) throw invalid("Missing region " + name);
    }

    private static JsonValue array(JsonValue json, String key, int length) {
        JsonValue value = json.get(key);
        if (value == null || !value.isArray() || value.size != length) throw invalid("Invalid " + key + " array");
        return value;
    }

    private static int[] integers(JsonValue json, String key, int length) {
        JsonValue value = array(json, key, length); int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            JsonValue element = value.get(i);
            if (!element.isNumber() || element.asDouble() != element.asInt()) throw invalid("Noninteger " + key);
            result[i] = element.asInt();
        }
        return result;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Gameplay art: " + message);
    }
}
