package com.couchcrypt.game;

/** Render-only cells: the approved border lives in the existing cosmetic padding. */
final class ArenaTileLayout {
    static final int COLUMNS = 20, ROWS = 15, PADDING = 8;
    static final float CELL = 120f;
    static final int QUADS = (COLUMNS + PADDING * 2) * (ROWS + PADDING * 2);

    private ArenaTileLayout() {}

    static boolean exterior(int column, int row) {
        return column < -1 || column > COLUMNS || row < -1 || row > ROWS;
    }

    static BorderTileArt.Tile tile(int column, int row) {
        if (exterior(column, row)) return BorderTileArt.Tile.INTERIOR;
        boolean west = column == -1, east = column == COLUMNS;
        boolean south = row == -1, north = row == ROWS;
        // Export i moves down-right (world -Y); j moves down-left (world -X).
        if (north && east) return BorderTileArt.Tile.CORNER_N;
        if (south && east) return BorderTileArt.Tile.CORNER_E;
        if (north && west) return BorderTileArt.Tile.CORNER_W;
        if (south && west) return BorderTileArt.Tile.CORNER_S;
        if (north) return BorderTileArt.Tile.EDGE_NW;
        if (east) return BorderTileArt.Tile.EDGE_NE;
        if (west) return BorderTileArt.Tile.EDGE_SW;
        if (south) return BorderTileArt.Tile.EDGE_SE;
        return BorderTileArt.Tile.INTERIOR;
    }

    /** Far diamond vertex; the original playable cell occupies [column,column+1]. */
    static float farX(int column) { return -ArenaWorld.HALF_WIDTH + (column + 1) * CELL; }
    static float farY(int row) { return -ArenaWorld.HALF_HEIGHT + (row + 1) * CELL; }
}
