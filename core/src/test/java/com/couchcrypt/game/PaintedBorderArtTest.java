package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintedBorderArtTest {
    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("." ); }
    private static JsonValue json(String file) throws Exception { return new JsonReader().parse(Files.readString(root().resolve(file))); }
    private static GameplayAtlas atlas() throws Exception { return new GameplayAtlas(json("assets/art/gameplay.json")); }

    @Test void packedPiecesRetainTheActualPaintAndCoverEveryVisibleSourcePixel() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        BufferedImage[] pages = pages(atlas);
        for (var painting : new PaintedBorderArt.Painting[]{art.x, art.y, art.corner}) {
            BufferedImage source = source(painting);
            boolean[] covered = new boolean[source.getWidth() * source.getHeight()];
            for (var piece : painting.pieces) {
                for (int y = 0; y < piece.height; y++) for (int x = 0; x < piece.width; x++)
                    assertEquals(source.getRGB(piece.sourceX + x, piece.sourceY + y),
                        pages[piece.page].getRGB(piece.atlasX + x, piece.atlasY + y), "Packed context must contain original neighbouring paint");
                for (int y = piece.coreY; y < piece.coreY + piece.coreHeight; y++)
                    for (int x = piece.coreX; x < piece.coreX + piece.coreWidth; x++) {
                        int at = y * source.getWidth() + x;
                        assertFalse(covered[at], "Two visible cores may not overlap and double-blend their paint");
                        covered[at] = true;
                    }
            }
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                if ((source.getRGB(x, y) >>> 24) >= 32)
                    assertTrue(covered[y * source.getWidth() + x], "The trimmed slices must retain all significant painted stone");
        }
    }

    @Test void actualDrawVerticesAndBilinearSamplesAgreeAcrossEveryStorageJoin() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        var scene = art.place(new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas), atlas);
        var quads = new IdentityHashMap<PaintedBorderArt.Piece, PaintedBorderArt.Quad>();
        for (var quad : scene.quads) quads.putIfAbsent(quad.piece, quad);
        BufferedImage[] pages = pages(atlas);
        int vertical = 0, horizontal = 0;
        for (var painting : new PaintedBorderArt.Painting[]{art.x, art.y, art.corner}) {
            for (var a : painting.pieces) for (var b : painting.pieces) if (a != b) {
                if (a.coreX + a.coreWidth == b.coreX) {
                    int lo = Math.max(a.coreY, b.coreY), hi = Math.min(a.coreY + a.coreHeight, b.coreY + b.coreHeight);
                    for (int y = lo; y < hi; y += 3) {
                        assertJoin(quads.get(a), quads.get(b), b.coreX, y + .5f, atlas, pages); vertical++;
                    }
                }
                if (a.coreY + a.coreHeight == b.coreY) {
                    int lo = Math.max(a.coreX, b.coreX), hi = Math.min(a.coreX + a.coreWidth, b.coreX + b.coreWidth);
                    for (int x = lo; x < hi; x += 3) {
                        assertJoin(quads.get(a), quads.get(b), x + .5f, b.coreY, atlas, pages); horizontal++;
                    }
                }
            }
        }
        assertTrue(vertical > 100, "Check the complete paintings, not only a single declared footprint join");
        assertTrue(horizontal > 100, "Check row subdivisions as well as column subdivisions");
    }

    @Test void sceneUsesFourWholeRunsAndFourLowCornersWithoutAddingTextures() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        ArenaLayout layout = new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas);
        assertEquals(4, layout.wallRuns.length);
        var scene = art.place(layout, atlas);
        assertEquals(art.x.pieces.length * 2 + art.y.pieces.length * 2 + art.corner.pieces.length * 4, scene.quads.length);
        assertTrue(scene.quads.length <= PaintedBorderArt.MAX_SCENERY);
        assertEquals(6L * 1_048_576, atlas.textureBytes);
        for (var painting : new PaintedBorderArt.Painting[]{art.x, art.y}) {
            BufferedImage image = source(painting);
            JsonValue generation = json("assets/art/painted-borders.json").get("paintings").get(painting.name);
            JsonValue fit = generation.get("groundFit");
            float slope = fit.getFloat("normalizedSlopeDown"), base = fit.getFloat("frontBaseYAtPivot");
            float actualScale = 0;
            for (var quad : scene.quads) if (quad.painting == painting) { actualScale = quad.drawScale; break; }
            assertTrue(actualScale >= painting.scale && actualScale <= painting.scale * 1.05f,
                "Corner overlap must not enlarge the approved wall art materially");
            float[] heights = new float[image.getWidth()]; int count = 0;
            for (int x = image.getWidth() / 5; x < image.getWidth() * 4 / 5; x++) {
                int top = 0;
                while (top < image.getHeight() && (image.getRGB(x, top) >>> 24) < 32) top++;
                if (top < image.getHeight()) heights[count++] = (base + slope * (x - painting.pivotX) - top) * actualScale;
            }
            java.util.Arrays.sort(heights, 0, count);
            assertTrue(count > 100);
            assertTrue(heights[count / 2] >= 65 && heights[count / 2] <= 120, "Typical painted masonry stays near hero height");
        }
        for (var quad : scene.quads) for (int i = 0; i < quad.vertices.length; i += 5) {
            assertTrue(Float.isFinite(quad.vertices[i]) && Float.isFinite(quad.vertices[i + 1]));
            assertTrue(quad.vertices[i + 3] >= 0 && quad.vertices[i + 3] <= 1);
            assertTrue(quad.vertices[i + 4] >= 0 && quad.vertices[i + 4] <= 1);
        }
    }

    @Test void malformedSharedPageOrContextContractFailsBeforeRendering() throws Exception {
        GameplayAtlas atlas = atlas();
        JsonValue wrongContext = json("assets/art/painted-borders.json");
        wrongContext.get("context").set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new PaintedBorderArt(wrongContext, atlas, null));
        JsonValue extraTexture = json("assets/art/painted-borders.json");
        extraTexture.get("addedTextureBytes").set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new PaintedBorderArt(extraTexture, atlas, null));
    }

    @Test void cornerOverlapOrderCannotFlipAtTheLastNarrowAtlasSlice() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        var scene = art.place(new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas), atlas);
        float latestX = Float.NEGATIVE_INFINITY, earliestY = Float.POSITIVE_INFINITY;
        int xPieces = 0, yPieces = 0;
        for (var q : scene.quads) {
            if (q.painting == art.x && q.worldY < 0 && q.sourceLeft < 64) {
                latestX = Math.max(latestX, q.sortDepth); xPieces++;
            }
            if (q.painting == art.y && q.worldX < 0 && q.sourceRight > art.y.sourceWidth - 64) {
                earliestY = Math.min(earliestY, q.sortDepth); yPieces++;
            }
        }
        assertTrue(xPieces > 2 && yPieces > 2);
        assertTrue(latestX < earliestY,
            "Every overlapping X-end slice must follow Y-end slices, including the last four-pixel column");
    }

    private static BufferedImage[] pages(GameplayAtlas atlas) throws Exception {
        BufferedImage[] pages = new BufferedImage[atlas.pages.length];
        for (int i = 0; i < pages.length; i++) pages[i] = ImageIO.read(root().resolve("assets/art/" + atlas.pages[i].file()).toFile());
        return pages;
    }
    private static BufferedImage source(PaintedBorderArt.Painting painting) throws Exception {
        return ImageIO.read(root().resolve("art-source/painted-arena-borders/" + painting.file).toFile());
    }
    private static float[] point(PaintedBorderArt.Quad q, float sx, float sy) {
        float u = (sx - q.sourceLeft) / (q.sourceRight - q.sourceLeft), v = (sy - q.piece.coreY) / q.piece.coreHeight;
        float[] a = q.vertices;
        return new float[]{a[5] + (a[10] - a[5]) * u + (a[0] - a[5]) * v,
            a[6] + (a[11] - a[6]) * u + (a[1] - a[6]) * v,
            a[8] + (a[13] - a[8]) * u, a[9] + (a[4] - a[9]) * v};
    }
    private static void assertJoin(PaintedBorderArt.Quad a, PaintedBorderArt.Quad b, float sx, float sy,
                                   GameplayAtlas atlas, BufferedImage[] pages) {
        float[] pa = point(a, sx, sy), pb = point(b, sx, sy);
        assertEquals(pa[0], pb[0], .001f); assertEquals(pa[1], pb[1], .001f);
        var ap = atlas.pages[a.piece.page]; var bp = atlas.pages[b.piece.page];
        for (int shift : new int[]{0, 8, 16, 24})
            assertEquals(sample(pages[a.piece.page], pa[2] * ap.width(), pa[3] * ap.height(), shift),
                sample(pages[b.piece.page], pb[2] * bp.width(), pb[3] * bp.height(), shift), .02,
                "The actual draw UVs must filter identical original paint at a storage boundary");
    }
    private static double sample(BufferedImage image, float x, float y, int shift) {
        double px = x - .5, py = y - .5; int ix = (int) Math.floor(px), iy = (int) Math.floor(py);
        double fx = px - ix, fy = py - iy, value = 0;
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            int sx = Math.max(0, Math.min(image.getWidth() - 1, ix + dx));
            int sy = Math.max(0, Math.min(image.getHeight() - 1, iy + dy));
            value += ((image.getRGB(sx, sy) >>> shift) & 255) * (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
        }
        return value;
    }
}
