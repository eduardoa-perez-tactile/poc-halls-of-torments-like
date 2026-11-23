package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.HashSet;

/** Startup-only authored scenery. Every placement is a landmark, never a collider. */
public final class ArenaLayout {
    public static final int MAX_PLACEMENTS = 192;
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    public final Placement[] placements;

    public static final class Placement {
        public final String id, region, zone;
        public final float x, y, scale, shadowWidth, shadowHeight;
        public final boolean wall, fadeWhenOccluding;

        private Placement(String id, String region, String zone, float x, float y,
                          float scale, float shadowWidth, float shadowHeight, boolean wall, boolean fade) {
            this.id = id; this.region = region; this.zone = zone; this.x = x; this.y = y;
            this.scale = scale; this.shadowWidth = shadowWidth; this.shadowHeight = shadowHeight;
            this.wall = wall; fadeWhenOccluding = fade;
        }
    }

    public static ArenaLayout load(GameplayAtlas metadata) {
        return new ArenaLayout(new JsonReader().parse(Gdx.files.internal("arena/crypt-layout.json")), metadata);
    }

    public ArenaLayout(JsonValue root, GameplayAtlas metadata) {
        if (root.getInt("version") != 1 || !root.getString("interiorMode").equals("open"))
            throw invalid("the authored arena must stay open");
        float[] bounds = pair(root, "worldSize");
        if (bounds[0] != ArenaWorld.HALF_WIDTH * 2 || bounds[1] != ArenaWorld.HALF_HEIGHT * 2)
            throw invalid("scenery may not change the playable bounds");
        ArrayList<Placement> result = new ArrayList<>(128);
        HashSet<String> ids = new HashSet<>();
        JsonValue runs = root.require("wallRuns");
        if (!runs.isArray() || runs.size != 4) throw invalid("four authored wall runs required");
        for (JsonValue run = runs.child; run != null; run = run.next) {
            float[] start = pair(run, "from");
            String axis = run.getString("axis"), id = run.getString("id");
            if (!axis.equals("x") && !axis.equals("y")) throw invalid("wall axis");
            int count = run.getInt("count");
            float step = finite(run.getFloat("step"), "wall step");
            String region = run.getString("region"), alternate = run.getString("regionAlternate", region);
            GameplayAtlas.Region source = metadata.regions.get(region), other = metadata.regions.get(alternate);
            if (source == null || other == null) throw invalid("unknown wall region");
            float size = finite(run.getFloat("sizeScale", 1f), "wall size");
            float length = axis.equals("x") ? source.footprintWorldX() : source.footprintWorldY();
            if (count < 1 || count > 32 || length < 100 || Math.abs(step - length * size) > .1f
                || source.footprintWorldX() != other.footprintWorldX()
                || source.footprintWorldY() != other.footprintWorldY())
                throw invalid("wall repeats must meet at their measured footprint edges");
            for (int i = 0; i < count; i++) {
                float x = start[0] + (axis.equals("x") ? i * step : 0);
                float y = start[1] + (axis.equals("y") ? i * step : 0);
                add(result, ids, metadata, run, i % 2 == 0 ? region : alternate,
                    id + "-" + i, "border", x, y, true);
            }
        }
        JsonValue groups = root.require("landmarks");
        if (!groups.isArray() || groups.size < 1 || groups.size > 12) throw invalid("landmark groups");
        for (JsonValue group = groups.child; group != null; group = group.next) {
            String zone = group.getString("id");
            float[] origin = pair(group, "at");
            JsonValue members = group.require("members");
            if (!members.isArray() || members.size < 1 || members.size > 32) throw invalid("landmark members");
            for (JsonValue member = members.child; member != null; member = member.next) {
                float[] offset = pair(member, "offset");
                add(result, ids, metadata, member, member.getString("region"), zone + "/" + member.getString("id"), zone,
                    origin[0] + offset[0], origin[1] + offset[1], member.getBoolean("wall", false));
            }
        }
        placements = result.toArray(new Placement[0]);
    }

    private static void add(ArrayList<Placement> result, HashSet<String> ids, GameplayAtlas metadata,
                            JsonValue entry, String region, String id, String zone, float x, float y, boolean wall) {
        if (result.size() >= MAX_PLACEMENTS || !ids.add(id)) throw invalid("placement pool or duplicate id");
        if (entry.getBoolean("solid", false)) throw invalid("landmarks must not block movement");
        if (Math.abs(x) > ArenaWorld.HALF_WIDTH + 160 || Math.abs(y) > ArenaWorld.HALF_HEIGHT + 160)
            throw invalid("placement exceeds the scenery envelope");
        if (wall && Math.abs(x) < ArenaWorld.HALF_WIDTH && Math.abs(y) < ArenaWorld.HALF_HEIGHT)
            throw invalid("a full-height border must remain outside the fighting floor");
        GameplayAtlas.Region source = metadata.regions.get(region);
        if (source == null) throw invalid("unknown scenery region " + region);
        float wx = source.footprintWorldX(), wy = source.footprintWorldY();
        float sizeScale = finite(entry.getFloat("sizeScale", 1f), "size scale");
        if (sizeScale < .35f || sizeScale > 2f || entry.has("worldFootprint"))
            throw invalid("scenery variants must preserve their isometric footprint ratio");
        wx *= sizeScale; wy *= sizeScale;
        if (wx < 8 || wy < 8 || wx > 400 || wy > 400 || source.footprintWidth() <= 0)
            throw invalid("scenery needs a measured footprint");
        // The central court is deliberately empty; grouped landmarks sit beyond its approaches.
        if (!wall && Math.abs(x) - wx * .5f < 420 && Math.abs(y) - wy * .5f < 320)
            throw invalid("landmark intrudes into the central court");
        float width = ISO_X * (wx + wy), height = ISO_Y * (wx + wy);
        float scale = width / source.footprintWidth();
        if (!Float.isFinite(scale) || scale <= 0 || scale > 8) throw invalid("scenery scale");
        result.add(new Placement(id, region, zone, x, y, scale, wall ? 0 : width * .85f,
            wall ? 0 : height * .6f, wall, entry.getBoolean("fadeWhenOccluding", true)));
    }

    private static float[] pair(JsonValue parent, String key) {
        JsonValue value = parent.require(key);
        if (!value.isArray() || value.size != 2) throw invalid(key);
        return new float[]{finite(value.getFloat(0), key), finite(value.getFloat(1), key)};
    }

    private static float finite(float value, String key) {
        if (!Float.isFinite(value)) throw invalid(key);
        return value;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Arena layout: " + reason);
    }
}
