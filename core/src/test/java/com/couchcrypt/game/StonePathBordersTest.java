package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StonePathBordersTest {
    @Test void bothBordersRemainInsideTheArenaAndClippingPreservesTheStoneChart() throws Exception {
        ArenaFloorLayout floor = floor();
        StonePathBorders borders = new StonePathBorders(floor);
        boolean[][] sides = new boolean[floor.paths.length][2];
        int clipped = 0;
        assertTrue(borders.blocks.length > 400 && borders.blocks.length <= StonePathBorders.MAX_STONES);
        assertEquals(borders.blocks.length, borders.polygons.length);
        for (int index = 0; index < borders.blocks.length; index++) {
            var stone = borders.blocks[index];
            var path = floor.paths[stone.pathIndex];
            sides[stone.pathIndex][(stone.side + 1) / 2] = true;
            assertSame(stone.vertices, borders.polygons[index]);
            assertTrue(stone.vertices.length >= 12 && stone.vertices.length <= StonePathBorders.MAX_VERTICES * 4);
            float area = area(stone.vertices);
            assertTrue(area > 0 && area <= StonePathBorders.STONE_WIDTH * StonePathBorders.STONE_LENGTH + .2f);
            if (area < StonePathBorders.STONE_WIDTH * stone.length - .1f) clipped++;
            for (int i = 0; i < stone.vertices.length; i += 4) {
                float x = stone.vertices[i], y = stone.vertices[i + 1], u = stone.vertices[i + 2], v = stone.vertices[i + 3];
                assertTrue(Math.abs(x) <= ArenaWorld.HALF_WIDTH && Math.abs(y) <= ArenaWorld.HALF_HEIGHT);
                assertTrue(u >= -.00001f && u <= 1.00001f && v >= -.00001f && v <= 1.00001f);
                float along = (x - floor.centerX) * path.unitX() + (y - floor.centerY) * path.unitY();
                float across = -(x - floor.centerX) * path.unitY() + (y - floor.centerY) * path.unitX();
                float borderCentre = stone.side * (floor.pathWidth - StonePathBorders.STONE_WIDTH) * .5f;
                assertEquals((along - stone.start) / StonePathBorders.STONE_LENGTH, u, .00003f);
                assertEquals(.5f - (across - borderCentre) / StonePathBorders.STONE_WIDTH, v, .00003f);
            }
        }
        assertTrue(clipped > 0, "Diagonal endpoints must be clipped rather than protruding through the wall");
        for (var pathSides : sides) assertTrue(pathSides[0] && pathSides[1], "Every path has two physical stone borders");
    }

    @Test void adjacentBlocksHaveRealGroutGapsAndConsistentPhysicalSize() throws Exception {
        ArenaFloorLayout floor = floor();
        StonePathBorders borders = new StonePathBorders(floor);
        int full = 0;
        for (int pathIndex = 0; pathIndex < floor.paths.length; pathIndex++) for (int side : new int[]{-1, 1}) {
            StonePathBorders.Block previous = null;
            for (var stone : borders.blocks) if (stone.pathIndex == pathIndex && stone.side == side) {
                if (previous != null) assertEquals(2f, stone.start - previous.start - previous.length, .001f);
                previous = stone;
                if (stone.length == StonePathBorders.STONE_LENGTH
                    && (floor.paths[pathIndex].unitX() == 0 || floor.paths[pathIndex].unitY() == 0)) {
                    assertEquals(760f, area(stone.vertices), .2f);
                    full++;
                }
            }
        }
        assertTrue(full > 100, "Most blocks retain their authored size instead of stretching to each path segment");
    }

    private static float area(float[] vertices) {
        float sum = 0;
        for (int i = 0, previous = vertices.length - 4; i < vertices.length; previous = i, i += 4)
            sum += vertices[previous] * vertices[i + 1] - vertices[i] * vertices[previous + 1];
        return sum * .5f;
    }

    private static ArenaFloorLayout floor() throws Exception {
        Path file = Path.of("../assets/arena/crypt-floor.json");
        if (!Files.exists(file)) file = Path.of("assets/arena/crypt-floor.json");
        return new ArenaFloorLayout(new JsonReader().parse(Files.readString(file)));
    }
}
