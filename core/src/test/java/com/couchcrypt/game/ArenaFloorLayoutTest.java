package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaFloorLayoutTest {
    @Test void clippedRepeatCellsStayInsideTheArenaAndKeepTheirOriginalMaterialCoordinates() throws Exception {
        ArenaFloorLayout floor = new ArenaFloorLayout(source());
        assertEquals(8, floor.paths.length);
        assertTrue(floor.pathCells.length > 8 && floor.pathCells.length <= ArenaFloorLayout.MAX_CELLS);
        float[] areaByPath = new float[floor.paths.length];
        for (var cell : floor.pathCells) {
            assertTrue(cell.vertexCount >= 3 && cell.vertexCount <= ArenaFloorLayout.MAX_CELL_VERTICES);
            ArenaFloorLayout.Path path = floor.paths[cell.pathIndex];
            float area = signedArea(cell.vertices);
            assertTrue(area > 0, "Cell winding must remain consistent after clipping");
            areaByPath[cell.pathIndex] += area;
            for (int vertex = 0; vertex < cell.vertices.length; vertex += 4) {
                float x = cell.vertices[vertex], y = cell.vertices[vertex + 1];
                float u = cell.vertices[vertex + 2], v = cell.vertices[vertex + 3];
                assertTrue(Math.abs(x) <= ArenaWorld.HALF_WIDTH && Math.abs(y) <= ArenaWorld.HALF_HEIGHT);
                assertTrue(u >= -.00001f && u <= 1.00001f && v >= -.00001f && v <= 1.00001f);
                // Clipping must interpolate the existing UV chart, rather than stretching the last slab.
                float along = (x - floor.centerX) * path.unitX() + (y - floor.centerY) * path.unitY();
                float across = -(x - floor.centerX) * path.unitY() + (y - floor.centerY) * path.unitX();
                assertEquals((floor.pathWidth * .5f - across) / floor.pathWidth, u, .00002f);
                assertEquals((along - cell.repeatStart) / floor.materialRepeat, v, .00002f);
            }
        }
        // Axis-aligned strips fit fully; diagonal strips lose only their small outside-corner wedges.
        for (int i = 0; i < floor.paths.length; i++) {
            var path = floor.paths[i];
            float fullArea = floor.pathWidth * path.length();
            if (path.unitX() == 0 || path.unitY() == 0) assertEquals(fullArea, areaByPath[i], .5f);
            else assertTrue(areaByPath[i] > fullArea * .94f && areaByPath[i] < fullArea);
        }
    }

    @Test void quietWearIsContinuousBoundedAndDoesNotTurnIntoAlternatingCellTints() throws Exception {
        ArenaFloorLayout floor = new ArenaFloorLayout(source());
        assertEquals(600f, floor.medallionDiameter);
        assertEquals(0f, floor.centerX); assertEquals(0f, floor.centerY);
        float minimum = 1f, maximum = 1f;
        for (int x = -1200; x <= 1200; x += 20) for (int y = -900; y <= 900; y += 20) {
            float shade = floor.shade(x, y);
            minimum = Math.min(minimum, shade); maximum = Math.max(maximum, shade);
            assertTrue(shade >= .94f && shade <= 1.03f);
            assertTrue(Math.abs(shade - floor.shade(x + .1f, y - .1f)) < .0001f,
                "Wear must remain continuous across paving-cell boundaries");
            assertTrue(Math.abs(shade - floor.shade(x + 120, y)) < .028f,
                "Variation should be broad quiet patches, not a checkerboard");
        }
        assertTrue(minimum < .97f && maximum > 1.01f);
        assertEquals(1f, floor.shade(0, 0), .001f, "The engraving court stays neutral");
    }

    @Test void invalidFloorDataCannotExpandBoundsAddCollidersOrCreateUnboundedGeometry() throws Exception {
        JsonValue badBounds = source();
        badBounds.get("worldSize").get(0).set(new JsonValue(2600L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(badBounds));
        JsonValue solid = source();
        solid.get("paths").get(0).addChild("solid", new JsonValue(true));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(solid));
        JsonValue escaped = source();
        escaped.get("paths").get(0).get("to").get(0).set(new JsonValue(1201L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(escaped));
        JsonValue runaway = source();
        runaway.get("materialRepeat").set(new JsonValue(.1));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(runaway));
        JsonValue duplicate = source();
        duplicate.get("paths").get(1).get("id").set(new JsonValue("east"));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(duplicate));
    }

    private static float signedArea(float[] vertices) {
        float twiceArea = 0;
        for (int current = 0, previous = vertices.length - 4; current < vertices.length; previous = current, current += 4)
            twiceArea += vertices[previous] * vertices[current + 1] - vertices[current] * vertices[previous + 1];
        return twiceArea * .5f;
    }

    private static JsonValue source() throws Exception {
        Path file = Path.of("../assets/arena/crypt-floor.json");
        if (!Files.exists(file)) file = Path.of("assets/arena/crypt-floor.json");
        return new JsonReader().parse(Files.readString(file));
    }
}
