package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaTileLayoutTest {
    @Test void borderCellsDoNotConsumeAnyOfTheExistingFightingFloor() {
        assertEquals(-ArenaWorld.HALF_WIDTH, ArenaTileLayout.farX(0) - ArenaTileLayout.CELL);
        assertEquals(ArenaWorld.HALF_WIDTH, ArenaTileLayout.farX(ArenaTileLayout.COLUMNS - 1));
        assertEquals(-ArenaWorld.HALF_HEIGHT, ArenaTileLayout.farY(0) - ArenaTileLayout.CELL);
        assertEquals(ArenaWorld.HALF_HEIGHT, ArenaTileLayout.farY(ArenaTileLayout.ROWS - 1));
        for (int row = 0; row < ArenaTileLayout.ROWS; row++) {
            for (int column = 0; column < ArenaTileLayout.COLUMNS; column++) {
                assertEquals(BorderTileArt.Tile.INTERIOR, ArenaTileLayout.tile(column, row));
                assertFalse(ArenaTileLayout.exterior(column, row));
            }
        }
        assertEquals(-ArenaWorld.HALF_WIDTH, ArenaTileLayout.farX(-1));
        assertEquals(ArenaWorld.HALF_WIDTH, ArenaTileLayout.farX(ArenaTileLayout.COLUMNS) - ArenaTileLayout.CELL);
        assertEquals(-ArenaWorld.HALF_HEIGHT, ArenaTileLayout.farY(-1));
        assertEquals(ArenaWorld.HALF_HEIGHT, ArenaTileLayout.farY(ArenaTileLayout.ROWS) - ArenaTileLayout.CELL);
    }

    @Test void fourCornerFacingsFollowTheExportCameraInsteadOfMirroringWorldAxes() {
        int east = ArenaTileLayout.COLUMNS, north = ArenaTileLayout.ROWS;
        assertEquals(BorderTileArt.Tile.CORNER_N, ArenaTileLayout.tile(east, north));
        assertEquals(BorderTileArt.Tile.CORNER_W, ArenaTileLayout.tile(-1, north));
        assertEquals(BorderTileArt.Tile.CORNER_E, ArenaTileLayout.tile(east, -1));
        assertEquals(BorderTileArt.Tile.CORNER_S, ArenaTileLayout.tile(-1, -1));
        assertEquals(BorderTileArt.Tile.EDGE_NW, ArenaTileLayout.tile(8, north));
        assertEquals(BorderTileArt.Tile.EDGE_NE, ArenaTileLayout.tile(east, 6));
        assertEquals(BorderTileArt.Tile.EDGE_SW, ArenaTileLayout.tile(-1, 6));
        assertEquals(BorderTileArt.Tile.EDGE_SE, ArenaTileLayout.tile(8, -1));
        // The next row outside each closed edge is dark backing, not another wall.
        for (int column : new int[]{-2, east + 1}) {
            assertTrue(ArenaTileLayout.exterior(column, 6));
            assertEquals(BorderTileArt.Tile.INTERIOR, ArenaTileLayout.tile(column, 6));
        }
        for (int row : new int[]{-2, north + 1}) {
            assertTrue(ArenaTileLayout.exterior(8, row));
            assertEquals(BorderTileArt.Tile.INTERIOR, ArenaTileLayout.tile(8, row));
        }
    }

    @Test void boundedCacheContainsOneClosedBorderAndRetainsTheOriginalPaddingBudget() {
        int fighting = 0, border = 0, exterior = 0, corners = 0;
        for (int row = -ArenaTileLayout.PADDING; row < ArenaTileLayout.ROWS + ArenaTileLayout.PADDING; row++) {
            for (int column = -ArenaTileLayout.PADDING; column < ArenaTileLayout.COLUMNS + ArenaTileLayout.PADDING; column++) {
                BorderTileArt.Tile tile = ArenaTileLayout.tile(column, row);
                if (ArenaTileLayout.exterior(column, row)) exterior++;
                else if (tile == BorderTileArt.Tile.INTERIOR) fighting++;
                else border++;
                if (tile.name().startsWith("CORNER_")) corners++;
            }
        }
        assertEquals(300, fighting);
        assertEquals(74, border);
        assertEquals(4, corners);
        assertEquals(1116, fighting + border + exterior);
        assertEquals(ArenaTileLayout.QUADS, fighting + border + exterior);
    }

    @Test void originalPaddingStillCoversSoloAndCoopCamerasAtEveryMovementLimit() {
        float isoX = .70710678f, isoY = .35355339f;
        float minX = -ArenaWorld.HALF_WIDTH - ArenaTileLayout.PADDING * ArenaTileLayout.CELL;
        float maxX = ArenaWorld.HALF_WIDTH + ArenaTileLayout.PADDING * ArenaTileLayout.CELL;
        float minY = -ArenaWorld.HALF_HEIGHT - ArenaTileLayout.PADDING * ArenaTileLayout.CELL;
        float maxY = ArenaWorld.HALF_HEIGHT + ArenaTileLayout.PADDING * ArenaTileLayout.CELL;
        for (float[] viewport : new float[][]{{1100, 618.75f}, {650, 731.25f}}) {
            for (float px : new float[]{-ArenaWorld.HALF_WIDTH + 20, ArenaWorld.HALF_WIDTH - 20}) {
                for (float py : new float[]{-ArenaWorld.HALF_HEIGHT + 20, ArenaWorld.HALF_HEIGHT - 20}) {
                    float cx = (px - py) * isoX, cy = (px + py) * isoY + 65;
                    for (int sideX : new int[]{-1, 1}) for (int sideY : new int[]{-1, 1}) {
                        float sx = cx + sideX * viewport[0] / 2;
                        float sy = cy + sideY * viewport[1] / 2;
                        float worldX = sx / (2 * isoX) + sy / (2 * isoY);
                        float worldY = sy / (2 * isoY) - sx / (2 * isoX);
                        assertTrue(worldX >= minX && worldX <= maxX, "Camera exposes an unpainted X edge");
                        assertTrue(worldY >= minY && worldY <= maxY, "Camera exposes an unpainted Y edge");
                    }
                }
            }
        }
    }
}
