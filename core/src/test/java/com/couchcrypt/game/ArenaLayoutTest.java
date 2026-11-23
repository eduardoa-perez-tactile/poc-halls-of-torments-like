package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaLayoutTest {
    @Test void shippedOpenRoomHasContinuousBordersClearCourtAndBoundedMeasuredLandmarks() throws Exception {
        GameplayAtlas atlas = atlas();
        ArenaLayout layout = new ArenaLayout(layout(), atlas);
        assertTrue(layout.placements.length <= ArenaLayout.MAX_PLACEMENTS);
        int wallX = 0, wallY = 0, landmarks = 0;
        for (ArenaLayout.Placement p : layout.placements) {
            GameplayAtlas.Region region = atlas.regions.get(p.region);
            assertTrue(p.scale > 0 && Float.isFinite(p.scale));
            assertTrue(p.fadeWhenOccluding, "A foreground landmark must not hide a living actor");
            if (p.wall) {
                GameplayAtlas.Frame frame = atlas.frames[region.frame()];
                float rise = (frame.bottom + frame.height) * p.scale;
                // A 240-unit diagonal adds about 42 projected units above its centre-ground pivot.
                assertTrue(rise >= 65 && rise <= 145, "Broken border silhouette exceeds its character-height envelope: " + p.id + "=" + rise);
            }
            if (p.region.startsWith("wall-x")) {
                wallX++;
                assertEquals(912f, Math.abs(p.y));
                assertEquals(0, Math.round(p.x + 1080) % 240);
            } else if (p.region.startsWith("wall-y")) {
                wallY++;
                assertEquals(1212f, Math.abs(p.x));
                assertEquals(0f, (p.y + 787.5f) % 225f, .001f);
            }
            if (!p.wall) {
                landmarks++;
                assertTrue(Math.abs(p.x) > 420 || Math.abs(p.y) > 320);
                assertTrue(p.shadowWidth > 0 && p.shadowHeight > 0);
            }
            assertTrue(region.footprintWidth() > 0, "Each placed sprite has a measured source footprint");
        }
        assertEquals(20, wallX); assertEquals(16, wallY);
        assertTrue(landmarks >= 12 && landmarks <= 32, "The room needs grouped landmarks, not a floor full of clutter");
    }

    @Test void invalidLandmarkChangesCannotSilentlyAddCollidersOrFillTheCentralCourt() throws Exception {
        JsonValue solid = layout();
        solid.get("landmarks").get(0).get("members").get(0).addChild("solid", new JsonValue(true));
        GameplayAtlas atlas = atlas();
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(solid, atlas));
        JsonValue central = layout();
        central.get("landmarks").get(0).get("at").get(0).set(new JsonValue(0L));
        central.get("landmarks").get(0).get("at").get(1).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(central, atlas));
    }

    @Test void badReferencesDuplicateIdsAndOversizedRunsAreRejectedBeforeRendering() throws Exception {
        GameplayAtlas atlas = atlas();
        JsonValue missing = layout();
        missing.get("wallRuns").get(0).get("region").set(new JsonValue("missing-wall"));
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(missing, atlas));
        JsonValue duplicate = layout();
        JsonValue members = duplicate.get("landmarks").get(0).get("members");
        members.get(1).get("id").set(new JsonValue(members.get(0).getString("id")));
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(duplicate, atlas));
        JsonValue runaway = layout();
        runaway.get("wallRuns").get(0).get("count").set(new JsonValue(1000L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(runaway, atlas));
        JsonValue gap = layout();
        gap.get("wallRuns").get(0).get("step").set(new JsonValue(300L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaLayout(gap, atlas),
            "Module spacing cannot leave gaps between the measured wall footprints");
    }

    private static GameplayAtlas atlas() throws Exception { return new GameplayAtlas(source("art/gameplay.json")); }
    private static JsonValue layout() throws Exception { return source("arena/crypt-layout.json"); }
    private static JsonValue source(String file) throws Exception {
        Path path = Path.of("../assets/" + file);
        if (!Files.exists(path)) path = Path.of("assets/" + file);
        return new JsonReader().parse(Files.readString(path));
    }
}
