package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaFloorLayoutTest {
    @Test void clippedRepeatCellsStayInsideTheArenaAndKeepTheirOriginalMaterialCoordinates() throws Exception {
        ArenaFloorLayout floor = new ArenaFloorLayout(radialSource());
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

    @Test void approvedPavingBandsCoverTheirRectanglesWithoutExtraSpokesOrStretchedEndSlabs() throws Exception {
        ArenaFloorLayout floor = new ArenaFloorLayout(source());
        assertTrue(floor.usesPavingBands());
        assertEquals(0, floor.paths.length);
        assertEquals(3, floor.bands.length);
        assertEquals(23, floor.pathCells.length);
        float[] area = new float[floor.bands.length];
        float[] nextY = new float[floor.bands.length];
        for (int i = 0; i < floor.bands.length; i++) nextY[i] = floor.bands[i].minY();
        for (var cell : floor.pathCells) {
            var band = floor.bands[cell.pathIndex];
            assertEquals(4, cell.vertexCount);
            assertEquals(nextY[cell.pathIndex], cell.vertices[1], .001f, "No gap between paving slabs");
            nextY[cell.pathIndex] = cell.vertices[9];
            assertTrue(signedArea(cell.vertices) > 0);
            area[cell.pathIndex] += signedArea(cell.vertices);
            for (int vertex = 0; vertex < cell.vertices.length; vertex += 4) {
                float x = cell.vertices[vertex], y = cell.vertices[vertex + 1];
                assertTrue(x >= band.minX() && x <= band.maxX());
                assertTrue(y >= band.minY() && y <= band.maxY());
                assertEquals(.1f + .8f * (x - band.minX()) / (band.maxX() - band.minX()),
                    cell.vertices[vertex + 2], .00001f,
                    "The source crop must preserve the complete world-space paving width");
                assertEquals((y - band.minY() - cell.repeatStart) / floor.materialRepeat,
                    cell.vertices[vertex + 3], .00001f);
            }
        }
        for (int i = 0; i < floor.bands.length; i++) {
            var band = floor.bands[i];
            assertEquals(band.maxY(), nextY[i], .001f);
            assertEquals((band.maxX() - band.minX()) * (band.maxY() - band.minY()), area[i], .5f);
        }
        assertEquals(-120, floor.bands[0].minX()); assertEquals(120, floor.bands[0].maxX());
        assertEquals(-880, floor.bands[0].minY()); assertEquals(880, floor.bands[0].maxY());
        assertEquals(120, floor.fieldRepeat); assertEquals(200, floor.materialRepeat);
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
        JsonValue solid = radialSource();
        solid.get("paths").get(0).addChild("solid", new JsonValue(true));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(solid));
        JsonValue escaped = radialSource();
        escaped.get("paths").get(0).get("to").get(0).set(new JsonValue(1201L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(escaped));
        JsonValue runaway = source();
        runaway.get("materialRepeat").set(new JsonValue(.1));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(runaway));
        JsonValue duplicate = radialSource();
        duplicate.get("paths").get(1).get("id").set(new JsonValue("east"));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(duplicate));
    }

    @Test void invalidBandsCannotOverlapEscapeTheFloorOrAddCollision() throws Exception {
        JsonValue escaped = source();
        escaped.get("bands").get(0).get("to").get(1).set(new JsonValue(901L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(escaped));
        JsonValue reversed = source();
        reversed.get("bands").get(0).get("to").get(0).set(new JsonValue(-121L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(reversed));
        JsonValue overlap = source();
        overlap.get("bands").get(1).get("to").get(0).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(overlap));
        JsonValue solid = source();
        solid.get("bands").get(0).addChild("solid", new JsonValue(true));
        assertThrows(IllegalArgumentException.class, () -> new ArenaFloorLayout(solid));
        JsonValue duplicate = source();
        duplicate.get("bands").get(1).get("id").set(new JsonValue("processional-aisle"));
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

    private static JsonValue radialSource() throws Exception {
        JsonValue root = source();
        root.remove("bands"); root.remove("pavingMode");
        root.get("pathWidth").set(new JsonValue(200L));
        root.addChild("paths", new JsonReader().parse("""
            [{"id":"east","to":[1200,0]}, {"id":"north-east","to":[1200,900]},
             {"id":"north","to":[0,900]}, {"id":"north-west","to":[-1200,900]},
             {"id":"west","to":[-1200,0]}, {"id":"south-west","to":[-1200,-900]},
             {"id":"south","to":[0,-900]}, {"id":"south-east","to":[1200,-900]}]
            """));
        return root;
    }
}
