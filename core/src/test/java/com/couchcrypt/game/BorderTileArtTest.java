package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BorderTileArtTest {
    @Test void shippedAtlasKeepsItsHalfMiBBudgetAndTrimmedFarVertexAnchors() throws Exception {
        BorderTileArt.Metadata metadata = new BorderTileArt.Metadata(shipped());
        assertEquals(524_288, metadata.textureBytes);
        assertEquals(120, metadata.tileWidth);
        assertEquals(60, metadata.tileHeight);
        assertEquals(9, metadata.frames.length);
        for (BorderTileArt.Frame frame : metadata.frames) assertNotNull(frame);
        BorderTileArt.Frame floor = metadata.frames[BorderTileArt.Tile.INTERIOR.ordinal()];
        assertEquals(-60, floor.left);
        assertEquals(-60, floor.bottom);
        BorderTileArt.Frame foreground = metadata.frames[BorderTileArt.Tile.CORNER_S.ordinal()];
        assertTrue(foreground.bottom < -metadata.tileHeight,
            "The painted foundation skirt must survive trimming below the footprint");
        assertTrue(foreground.left < -metadata.tileWidth / 2,
            "The foreground corner extends past its diamond rather than being clipped");
    }

    @Test void oversizedPageOrUnsafeFileFailsBeforeAnyGlAllocation() throws Exception {
        JsonValue oversized = shipped();
        oversized.require("size").get(0).set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(oversized));
        JsonValue unsafe = shipped(); unsafe.require("file").set("../arena-border.png");
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(unsafe));
    }

    @Test void incompleteDuplicateAndUnknownTileSetsAreRejected() throws Exception {
        JsonValue missing = shipped(); missing.require("tiles").remove(8);
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(missing));
        JsonValue duplicate = shipped();
        duplicate.require("tiles").get(1).require("id").set("interior");
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(duplicate));
        JsonValue unknown = shipped(); unknown.require("tiles").get(0).require("id").set("other-room");
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(unknown));
    }

    @Test void displacedAnchorsInvalidTrimsAndFractionalPixelCoordinatesAreRejected() throws Exception {
        JsonValue anchor = shipped(); anchor.require("tiles").get(0).require("anchor").get(0).set(new JsonValue(75L));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(anchor));
        JsonValue trim = shipped(); trim.require("tiles").get(0).require("trimOffset").get(0).set(new JsonValue(150L));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(trim));
        JsonValue fraction = shipped(); fraction.require("tiles").get(0).require("rect").get(0).set(new JsonValue(2.5));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(fraction));
    }

    @Test void samplingGuttersCannotOverlapOrLeaveThePage() throws Exception {
        JsonValue overlap = shipped();
        JsonValue first = overlap.require("tiles").get(0).require("rect");
        JsonValue second = overlap.require("tiles").get(1).require("rect");
        second.get(0).set(new JsonValue(first.getLong(0))); second.get(1).set(new JsonValue(first.getLong(1)));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(overlap));
        JsonValue outside = shipped(); outside.require("tiles").get(0).require("rect").get(0).set(new JsonValue(1L));
        assertThrows(IllegalArgumentException.class, () -> new BorderTileArt.Metadata(outside));
    }

    private static JsonValue shipped() throws Exception {
        Path path = Path.of("../assets/art/arena-border.json");
        if (!Files.exists(path)) path = Path.of("assets/art/arena-border.json");
        return new JsonReader().parse(Files.readString(path));
    }
}
