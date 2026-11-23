package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngravedFloorArtTest {
    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("."); }
    private static JsonValue json(String path) throws Exception { return new JsonReader().parse(Files.readString(root().resolve(path))); }
    private static EngravedFloorArt art() throws Exception {
        return new EngravedFloorArt(json("assets/art/engraved-floor.json"), new GameplayAtlas(json("assets/art/gameplay.json")), null);
    }

    @Test void packedPaintAndEveryNeighbourSampleMatchTheCanonicalSources() throws Exception {
        var art = art();
        JsonValue pages = json("assets/art/engraved-floor.json").get("pages");
        BufferedImage[] packed = new BufferedImage[pages.size];
        for (int i = 0; i < pages.size; i++) packed[i] = ImageIO.read(root().resolve("assets/art/" + pages.get(i).getString("file")).toFile());
        for (var painting : new EngravedFloorArt.Painting[]{art.field, art.path, art.medallion, art.curb}) {
            BufferedImage source = ImageIO.read(root().resolve("art-source/crypt-floor-clarity/" + painting.file).toFile());
            boolean[] seen = new boolean[painting.width * painting.height];
            for (var p : painting.pieces) {
                for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++)
                    assertEquals(source.getRGB(p.sourceX + x, p.sourceY + y), packed[p.page].getRGB(p.x + x, p.y + y));
                for (int y = p.coreY; y < p.coreY + p.coreHeight; y++) for (int x = p.coreX; x < p.coreX + p.coreWidth; x++) {
                    int i = y * painting.width + x;
                    assertFalse(seen[i], "Storage cores must not double-blend the artwork"); seen[i] = true;
                }
            }
            for (int y = painting.contentY; y < painting.contentY + painting.contentHeight; y++)
                for (int x = painting.contentX; x < painting.contentX + painting.contentWidth; x++)
                    if ((source.getRGB(x, y) >>> 24) >= 32) assertTrue(seen[y * painting.width + x], "Visible stone was lost");
        }
    }

    @Test void materialCollarsWrapRealOppositeEdgePixelsWithoutChangingStoneScale() throws Exception {
        var art = art();
        for (var painting : new EngravedFloorArt.Painting[]{art.field, art.path}) {
            var image = ImageIO.read(root().resolve("art-source/crypt-floor-clarity/" + painting.file).toFile());
            int x = painting.contentX, y = painting.contentY, w = painting.contentWidth, h = painting.contentHeight;
            for (int i = 0; i < h && painting == art.field; i++) {
                assertEquals(image.getRGB(x + w - 1, y + i), image.getRGB(x - 1, y + i));
                assertEquals(image.getRGB(x, y + i), image.getRGB(x + w, y + i));
            }
            for (int i = 0; i < w; i++) {
                assertEquals(image.getRGB(x + i, y + h - 1), image.getRGB(x + i, y - 1));
                assertEquals(image.getRGB(x + i, y), image.getRGB(x + i, y + h));
            }
        }
    }

    @Test void clippedMaterialGeometryCoversTheFloorAndFitsTheStaticCache() throws Exception {
        var art = art(); var layout = new ArenaFloorLayout(json("assets/arena/crypt-floor.json"));
        float area = 0; int count = ArenaTileLayout.QUADS - ArenaTileLayout.COLUMNS * ArenaTileLayout.ROWS;
        float repeat = layout.fieldRepeat;
        for (float x = -1200; x < 1200; x += repeat) for (float y = -900; y < 900; y += repeat) {
            float right = Math.min(x + repeat, 1200), top = Math.min(y + repeat, 900);
            float[] polygon = EngravedFloorArt.rectangle(x, y, right, top);
            polygon[10] = polygon[14] = (right - x) / repeat;
            polygon[3] = polygon[15] = 1 - (top - y) / repeat;
            var geometry = art.geometry(art.field, polygon, 1f);
            for (var quad : geometry) { area += worldArea(quad.vertices()); assertVertices(quad.vertices()); }
            count += geometry.length;
        }
        assertEquals(2400 * 1800f, area, 12f, "Clipped atlas storage pieces must own the complete floor exactly once");
        for (var cell : layout.pathCells) {
            var geometry = art.geometry(art.path, cell.vertices, 1f, .10f, .90f); count += geometry.length;
            for (var quad : geometry) assertVertices(quad.vertices());
        }
        for (float[] polygon : new StonePathBorders(layout).polygons)
            count += art.geometry(art.curb, polygon, 1f).length;
        assertTrue(count < EngravedFloorArt.MAX_FLOOR_QUADS, "Bounded shared static cache must fit the shipped layout");
        assertEquals(0, json("assets/art/engraved-floor.json").getLong("addedTextureBytes"));
    }

    @Test void invalidBorrowedPageOrExtraTextureBudgetFailsBeforeRendering() throws Exception {
        var atlas = new GameplayAtlas(json("assets/art/gameplay.json"));
        var root = json("assets/art/engraved-floor.json"); root.get("addedTextureBytes").set(new JsonValue(1L));
        assertThrows(IllegalArgumentException.class, () -> new EngravedFloorArt(root, atlas, null));
        var wrong = json("assets/art/engraved-floor.json"); wrong.get("pages").get(3).get("size").get(0).set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new EngravedFloorArt(wrong, atlas, null));
    }

    @Test void bordersKeepTheirFullStoneDetailAndTheirAlphaCannotCreateBrightFringes() throws Exception {
        var art = art(); var floor = new ArenaFloorLayout(json("assets/arena/crypt-floor.json"));
        assertEquals(1, art.curb.pieces.length, "A border block should not require many atlas submissions");
        assertTrue(art.field.contentWidth / floor.fieldRepeat >= .5f, "Field details need enough samples at gameplay scale");
        assertTrue(art.path.contentWidth / floor.pathWidth >= .6f, "Path joints need more cross-path samples than the old64px bands");
        var source = ImageIO.read(root().resolve("art-source/crypt-floor-clarity/" + art.curb.file).toFile());
        int transparent = 0, stone = 0;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int rgba = source.getRGB(x, y), a = rgba >>> 24;
            assertTrue((rgba >> 16 & 255) <= a && (rgba >> 8 & 255) <= a && (rgba & 255) <= a);
            if (a == 0) transparent++; else if (a > 200) stone++;
        }
        assertTrue(transparent > 0 && stone > 100, "Painted chips must reveal the ground between separate blocks");
    }

    @Test void actualProjectedVerticesSampleTheSameWholePaintingAcrossStorageCuts() throws Exception {
        var art = art(); var pages = json("assets/art/engraved-floor.json").get("pages");
        for (var painting : new EngravedFloorArt.Painting[]{art.field, art.path, art.medallion, art.curb}) {
            var geometry = art.geometry(painting, EngravedFloorArt.rectangle(0, 0, 240, 240), 1f);
            for (var quad : geometry) {
                float[] v = quad.vertices();
                float width = pages.get(quad.page()).get("size").getFloat(0), height = pages.get(quad.page()).get("size").getFloat(1);
                float cx = 0, cy = 0;
                for (int i = 0; i < 20; i += 5) { cx += v[i + 3] * width / 4; cy += v[i + 4] * height / 4; }
                EngravedFloorArt.Piece owner = null;
                for (var piece : painting.pieces) if (piece.page == quad.page() && cx > piece.x && cx < piece.x + piece.width
                    && cy > piece.y && cy < piece.y + piece.height) { owner = piece; break; }
                assertNotNull(owner);
                for (int i = 0; i < 20; i += 5) {
                    float wx = (v[i] / .70710678f + v[i + 1] / .35355339f) * .5f;
                    float wy = (v[i + 1] / .35355339f - v[i] / .70710678f) * .5f;
                    assertEquals(painting.contentX + wx / 240 * painting.contentWidth,
                        v[i + 3] * width - owner.x + owner.sourceX, .003f);
                    assertEquals(painting.contentY + (1 - wy / 240) * painting.contentHeight,
                        v[i + 4] * height - owner.y + owner.sourceY, .003f);
                }
            }
        }
    }

    private static float worldArea(float[] v) {
        float area = 0;
        for (int i = 0, previous = 15; i < 20; previous = i, i += 5) area += v[previous] * v[i + 1] - v[i] * v[previous + 1];
        return Math.abs(area); // projected determinant .5 cancels the shoelace divisor 2
    }
    private static void assertVertices(float[] vertices) {
        for (int i = 0; i < vertices.length; i += 5) {
            assertTrue(Float.isFinite(vertices[i]) && Float.isFinite(vertices[i + 1]));
            assertTrue(vertices[i + 3] >= 0 && vertices[i + 3] <= 1);
            assertTrue(vertices[i + 4] >= 0 && vertices[i + 4] <= 1);
        }
    }
}
