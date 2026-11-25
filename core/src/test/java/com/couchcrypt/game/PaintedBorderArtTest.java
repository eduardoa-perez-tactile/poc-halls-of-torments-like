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
        long borderPages = 0;
        for (int i = 0; i < 3; i++) borderPages += (long) atlas.pages[i].width() * atlas.pages[i].height() * 4;
        assertEquals(6L * 1_048_576, borderPages, "Supplemental actors do not expand the borrowed border pages");
        assertEquals(0, json("assets/art/painted-borders.json").getLong("addedTextureBytes"));
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

    @Test void fullPaintingsMeetOuterWallFacesWithoutDoubleCountingTheirThickness() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        ArenaLayout layout = new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas);
        var scene = art.place(layout, atlas);
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (var run : layout.wallRuns) {
            if (run.alongX()) {
                minY = Math.min(minY, run.y() - run.thickness() * .5f);
                maxY = Math.max(maxY, run.y() + run.thickness() * .5f);
            } else {
                minX = Math.min(minX, run.x() - run.thickness() * .5f);
                maxX = Math.max(maxX, run.x() + run.thickness() * .5f);
            }
        }
        for (var run : layout.wallRuns) {
            var painting = run.alongX() ? art.x : art.y;
            PaintedBorderArt.Quad first = null, last = null;
            for (var q : scene.quads) if (q.painting == painting
                && (run.alongX() ? q.worldY == run.y() : q.worldX == run.x())) {
                if (first == null || q.sourceLeft < first.sourceLeft) first = q;
                if (last == null || q.sourceRight > last.sourceRight) last = q;
            }
            assertNotNull(first); assertNotNull(last);
            // Source ground-footprint vertices go through the same cached transform
            // as the stone paint. Use their actual endpoint pieces, rather than
            // extrapolating an entire wall from a tiny storage rectangle.
            float half = painting.footprintWidth * .5f;
            float[] left = point(first, painting.pivotX - half, painting.pivotY);
            float[] right = point(last, painting.pivotX + half, painting.pivotY);
            float outerSpan = run.alongX() ? maxX - minX : maxY - minY;
            assertEquals(outerSpan * .70710678f, right[0] - left[0], .001f,
                "Painted endcaps must stop at the adjoining outer faces, without an extra wall thickness");
        }
        for (var q : scene.quads) if (q.painting == art.corner) {
            float footprint = q.painting.footprintWidth * q.drawScale;
            assertEquals((art.x.worldThickness + art.y.worldThickness) * .70710678f, footprint, .001f,
                "A low junction backing must fit the two wall thicknesses, not project outside as a post");
            assertTrue(q.drawScale < art.corner.scale, "The original larger support remains only as low backing");
        }
    }

    @Test void bothEndsAtEveryCornerKeepOneFaceOrderAndTheBackingBehindThem() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        ArenaLayout layout = new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas);
        var scene = art.place(layout, atlas);
        int checked = 0;
        for (var placement : layout.placements) if (placement.wall && placement.region.equals("buttress")) {
            float latestX = Float.NEGATIVE_INFINITY, earliestY = Float.POSITIVE_INFINITY;
            float junctionLeft = Float.POSITIVE_INFINITY, junctionRight = Float.NEGATIVE_INFINITY;
            int xPieces = 0, yPieces = 0, backingPieces = 0;
            int xLayer = -1, yLayer = -1;
            for (var q : scene.quads) if (q.painting == art.corner && q.worldX == placement.x && q.worldY == placement.y) {
                junctionLeft = Math.min(junctionLeft, q.minX); junctionRight = Math.max(junctionRight, q.maxX);
            }
            for (var q : scene.quads) {
                if (q.painting == art.x && Math.abs(q.worldY - placement.y) < .01f
                    && q.minX < junctionRight && q.maxX > junctionLeft) {
                    latestX = Math.max(latestX, q.sortDepth);
                    if (xLayer != -1) assertEquals(xLayer, q.renderLayer);
                    xLayer = q.renderLayer; xPieces++;
                }
                if (q.painting == art.y && Math.abs(q.worldX - placement.x) < .01f
                    && q.minX < junctionRight && q.maxX > junctionLeft) {
                    earliestY = Math.min(earliestY, q.sortDepth);
                    if (yLayer != -1) assertEquals(yLayer, q.renderLayer);
                    yLayer = q.renderLayer; yPieces++;
                }
                if (q.painting == art.corner && q.worldX == placement.x && q.worldY == placement.y) {
                    assertEquals(PaintedBorderArt.CORNER_LAYER, q.renderLayer); backingPieces++;
                }
            }
            assertTrue(xPieces >= 2 && yPieces >= 2,
                "Check all source rows whose drawn wall ends actually intersect the junction at " + placement.id);
            assertEquals(art.corner.pieces.length, backingPieces);
            assertTrue(PaintedBorderArt.CORNER_LAYER < xLayer && PaintedBorderArt.CORNER_LAYER < yLayer);
            if (xLayer == yLayer) assertTrue(latestX < earliestY, "Wall face order changed at " + placement.id);
            else if (placement.y > 0) assertTrue(xLayer < yLayer, "The north wall must precede the west wall");
            else assertTrue(yLayer < xLayer, "The east wall must precede the south wall");
            checked++;
        }
        assertEquals(4, checked);
    }

    @Test void boundaryPlanesKeepAllStorageColumnsOutsideTheCompleteInteriorPass() throws Exception {
        GameplayAtlas atlas = atlas();
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        JsonValue document = json("assets/arena/crypt-layout.json");
        ArenaLayout layout = new ArenaLayout(document, atlas);
        var scene = art.place(layout, atlas);
        ArenaLayout.Placement gate = null;
        for (var p : layout.placements) if (p.region.equals("sealed-gate")) gate = p;
        assertNotNull(gate, "The approved layout has one entrance against its rear wall");
        var frame = atlas.frames[atlas.regions.get("sealed-gate").frame()];
        float rootX = (gate.x - gate.y) * .70710678f;
        float left = rootX + frame.left * gate.scale;
        float right = rootX + (frame.left + frame.width) * gate.scale;
        float attachedWallY = Float.NaN;
        for (var run : layout.wallRuns) if (run.alongX() && run.y() > gate.y) attachedWallY = run.y();
        assertTrue(Float.isFinite(attachedWallY));

        int leftColumns = 0, rightColumns = 0, backColumns = 0, frontColumns = 0;
        for (var q : scene.quads) {
            if (q.painting == art.corner) {
                assertEquals(PaintedBorderArt.CORNER_LAYER, q.renderLayer);
                continue;
            }
            float side = q.painting == art.x ? q.worldY : q.worldX;
            if (side > 0) {
                assertEquals(PaintedBorderArt.BACK_WALL_LAYER, q.renderLayer);
                assertTrue(q.renderLayer < PaintedBorderArt.INTERIOR_LAYER,
                    "Every north/east storage column must draw before the full actor body, regardless of its depth");
                backColumns++;
            } else {
                assertEquals(PaintedBorderArt.FRONT_WALL_LAYER, q.renderLayer);
                assertTrue(q.renderLayer > PaintedBorderArt.INTERIOR_LAYER,
                    "Every south/west storage column must draw after actors as one foreground plane");
                frontColumns++;
            }
            boolean attached = q.painting == art.x && q.worldY == attachedWallY && q.minX < right && q.maxX > left;
            if (attached) {
                assertTrue(q.renderLayer < PaintedBorderArt.INTERIOR_LAYER,
                    "No adjoining storage row may cut in front of either gate upright");
                if (q.minX < rootX) leftColumns++;
                if (q.maxX > rootX) rightColumns++;
            }
        }
        assertTrue(leftColumns >= 2 && rightColumns >= 2, "Check both uprights and every storage row");
        assertEquals(art.x.pieces.length + art.y.pieces.length, backColumns);
        assertEquals(art.x.pieces.length + art.y.pieces.length, frontColumns);
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
