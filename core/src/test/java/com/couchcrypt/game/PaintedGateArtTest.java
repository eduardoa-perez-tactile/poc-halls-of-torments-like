package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintedGateArtTest {
    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("."); }
    private static JsonValue read(String path) throws Exception { return new JsonReader().parse(Files.readString(root().resolve(path))); }
    private static GameplayAtlas atlas() throws Exception { return new GameplayAtlas(read("assets/art/gameplay.json")); }
    private static PaintedGateArt art(GameplayAtlas atlas) throws Exception { return new PaintedGateArt(read("assets/art/painted-gate.json"), atlas, null); }

    @Test void actualPackedPaintRetainsOriginalPixelsContextAndEverySignificantSourcePixel() throws Exception {
        GameplayAtlas atlas = atlas(); PaintedGateArt art = art(atlas);
        BufferedImage source = ImageIO.read(root().resolve("art-source/epic-crypt-gate/" + art.file).toFile());
        BufferedImage[] pages = new BufferedImage[atlas.pages.length];
        for (int i = 0; i < pages.length; i++) pages[i] = ImageIO.read(root().resolve("assets/art/" + atlas.pages[i].file()).toFile());
        boolean[] covered = new boolean[source.getWidth() * source.getHeight()];
        for (var piece : art.pieces) {
            for (int y = 0; y < piece.height; y++) for (int x = 0; x < piece.width; x++)
                assertEquals(source.getRGB(piece.sourceX + x, piece.sourceY + y),
                    pages[piece.page].getRGB(piece.atlasX + x, piece.atlasY + y), "Stored collars must contain original neighbouring gate pixels");
            for (int y = piece.coreY; y < piece.coreY + piece.coreHeight; y++) for (int x = piece.coreX; x < piece.coreX + piece.coreWidth; x++) {
                int at = y * source.getWidth() + x; assertFalse(covered[at]); covered[at] = true;
            }
        }
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
            if ((source.getRGB(x, y) >>> 24) >= 32) assertTrue(covered[y * source.getWidth() + x]);
        assertEquals(0, read("assets/art/painted-gate.json").getLong("addedTextureBytes"));
    }

    @Test void bothActualWingBasesFitTheNorthWallGroundPlaneAndUprightsStayVertical() throws Exception {
        PaintedGateArt art = art(atlas());
        BufferedImage source = ImageIO.read(root().resolve("art-source/epic-crypt-gate/" + art.file).toFile());
        double squaredError = 0; int count = 0;
        for (int x = 40; x <= 214; x++) {
            if (x > 65 && x < 190) continue;
            int y = source.getHeight() - 1;
            while (y >= 0 && (source.getRGB(x, y) >>> 24) < 128) y--;
            assertTrue(y >= 0);
            double dx = (x + .5 - art.pivotX) * art.worldScale;
            double dy = (art.pivotY - y - .5) * art.worldScale + dx * art.renderShearY;
            double expected = dx * .5 - 36 * .35355339;
            squaredError += (dy - expected) * (dy - expected); count++;
        }
        assertEquals(51, count);
        assertTrue(Math.sqrt(squaredError / count) < 1.5,
            "The generated wings must meet the existing wall's actual front plane, not a guessed bottom pivot");
        float x = 30, lowerY = (art.pivotY - 100) * art.worldScale + x * art.renderShearY;
        float upperY = (art.pivotY - 40) * art.worldScale + x * art.renderShearY;
        assertEquals(60 * art.worldScale, upperY - lowerY, .0001f, "Ground correction cannot rotate or scale vertical uprights");
    }

    @Test void cachedQuadsUseTheirExactSourceCoordinatesAndAlwaysDrawBehindActors() throws Exception {
        GameplayAtlas atlas = atlas(); PaintedGateArt art = art(atlas);
        ArenaLayout layout = new ArenaLayout(read("assets/arena/crypt-layout.json"), atlas);
        var scene = art.place(layout); int previousPage = -1;
        assertTrue(scene.quads.length <= PaintedGateArt.MAX_SCENERY, "Feather bands remain in a bounded startup cache");
        double actualArea = 0, expectedArea = 0;
        for (var piece : art.pieces) expectedArea += Math.max(0,
            Math.min(220, piece.coreX + piece.coreWidth) - Math.max(36, piece.coreX))
            * piece.coreHeight * art.worldScale * art.worldScale;
        boolean leftCut = false, rightCut = false;
        Map<Long, Integer> sharedColors = new HashMap<>();
        int repeatedVertices = 0, featherBands = 0;
        for (var q : scene.quads) {
            assertTrue(q.sourceLeft >= 36 && q.sourceRight <= 220);
            assertFalse(q.sourceLeft < 44 && q.sourceRight > 44, "Fade interpolation must stop before the central arch");
            assertFalse(q.sourceLeft < 212 && q.sourceRight > 212, "The right fade must start only within its eight-pixel band");
            if (q.sourceRight <= 44 || q.sourceLeft >= 212) featherBands++;
            leftCut |= q.sourceLeft == 36; rightCut |= q.sourceRight == 220;
            double area = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                int a = vertex * 5, b = (vertex + 1) % 4 * 5;
                area += (double) q.vertices[a] * q.vertices[b + 1] - (double) q.vertices[b] * q.vertices[a + 1];
            }
            actualArea += Math.abs(area) * .5;
            assertEquals(PaintedBorderArt.BACK_JOIN_LAYER, q.renderLayer);
            assertTrue(q.renderLayer < PaintedBorderArt.INTERIOR_LAYER);
            assertEquals(0, q.worldX); assertEquals(912, q.worldY); assertEquals(0, q.sortDepth);
            assertTrue(q.piece.page >= previousPage); previousPage = q.piece.page;
            for (int at = 0; at < 20; at += 5) {
                var page = atlas.pages[q.piece.page];
                float sx = q.vertices[at + 3] * page.width() - q.piece.atlasX + q.piece.sourceX;
                float sy = q.vertices[at + 4] * page.height() - q.piece.atlasY + q.piece.sourceY;
                int rgba = Float.floatToRawIntBits(q.vertices[at + 2]);
                assertEquals(0x00ffffff, rgba & 0x00ffffff, "Feathering changes opacity only");
                int alpha = rgba >>> 24;
                if (Math.abs(sx - 36) < .001f || Math.abs(sx - 220) < .001f) assertEquals(0, alpha);
                else if (sx >= 44 - .001f && sx <= 212 + .001f) assertEquals(254, alpha, "The complete arch must remain opaque");
                long key = ((long) Math.round(sx * 1000) << 32) ^ Math.round(sy * 1000);
                Integer oldColor = sharedColors.putIfAbsent(key, rgba);
                if (oldColor != null) { assertEquals(oldColor.intValue(), rgba, "Storage joins must have identical opacity on both sides"); repeatedVertices++; }
                float dx = (sx - art.pivotX) * art.worldScale;
                assertEquals((q.worldX - q.worldY) * .70710678f + dx, q.vertices[at], .001f);
                assertEquals((q.worldX + q.worldY) * .35355339f + (art.pivotY - sy) * art.worldScale + dx * art.renderShearY,
                    q.vertices[at + 1], .001f);
                assertTrue(q.vertices[at] >= q.minX - .001f && q.vertices[at] <= q.maxX + .001f);
                assertTrue(q.vertices[at + 1] >= q.minY - .001f && q.vertices[at + 1] <= q.maxY + .001f);
            }
        }
        assertTrue(featherBands >= 2 && repeatedVertices > 10, "Exercise both fades and real shared atlas-piece vertices");
        assertTrue(leftCut && rightCut, "Both wings must stop before their painted endcap lines");
        assertEquals(expectedArea, actualArea, .2, "Handoff cropping must preserve every retained source row without overlaps");
    }

    @Test void movedGateWrongPagesAndUnboundedProjectionFailBeforeRendering() throws Exception {
        GameplayAtlas atlas = atlas();
        JsonValue bad = read("assets/art/painted-gate.json"); bad.get("gate").get("renderShearY").set(new JsonValue(.8));
        assertThrows(IllegalArgumentException.class, () -> new PaintedGateArt(bad, atlas, null));
        JsonValue moved = read("assets/art/painted-gate.json"); moved.get("gate").get("worldPosition").get(1).set(new JsonValue(870));
        assertThrows(IllegalArgumentException.class, () -> new PaintedGateArt(moved, atlas, null));
        JsonValue page = read("assets/art/painted-gate.json"); page.get("pages").get(3).get("file").set(new JsonValue("arena-border.png"));
        assertThrows(IllegalArgumentException.class, () -> new PaintedGateArt(page, atlas, null));
    }
}
