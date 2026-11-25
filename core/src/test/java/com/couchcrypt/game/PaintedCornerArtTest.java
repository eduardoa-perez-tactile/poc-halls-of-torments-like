package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintedCornerArtTest {
    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("."); }
    private static JsonValue read(String file) throws Exception { return new JsonReader().parse(Files.readString(root().resolve(file))); }
    private record Fixture(GameplayAtlas atlas, ArenaLayout layout, PaintedBorderArt.Scene walls, PaintedCornerArt art) {}
    private static Fixture fixture() throws Exception {
        GameplayAtlas atlas = new GameplayAtlas(read("assets/art/gameplay.json"));
        ArenaLayout layout = new ArenaLayout(read("assets/arena/crypt-layout.json"), atlas);
        PaintedBorderArt border = new PaintedBorderArt(read("assets/art/painted-borders.json"), atlas, null);
        return new Fixture(atlas, layout, border.place(layout, atlas),
            new PaintedCornerArt(read("assets/art/painted-corners.json"), atlas, null));
    }

    @Test void splitJoinVerticesRetainExactResidentPaintCoordinatesAndAllSourceArea() throws Exception {
        Fixture f = fixture(); var scene = f.art.place(f.layout, f.walls);
        Map<PaintedCornerArt.Piece, Double> area = new IdentityHashMap<>();
        assertTrue(scene.quads.length <= PaintedCornerArt.MAX_SCENERY);
        for (var q : scene.quads) {
            var page = f.atlas.pages[q.piece.page]; float[] v = q.vertices;
            double sum = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                int at = vertex * 5, next = (vertex + 1) % 4 * 5;
                float sx = v[at + 3] * page.width() - q.piece.atlasX + q.piece.sourceX;
                float sy = v[at + 4] * page.height() - q.piece.atlasY + q.piece.sourceY;
                assertEquals((q.worldX - q.worldY) * .70710678f + (sx - q.corner.pivotX) * q.corner.scale, v[at], .001f);
                assertEquals((q.worldX + q.worldY) * .35355339f + (q.corner.pivotY - sy) * q.corner.scale, v[at + 1], .001f);
                assertTrue(sx >= q.piece.coreX - .001f && sx <= q.piece.coreX + q.piece.coreWidth + .001f);
                assertTrue(sy >= q.piece.coreY - .001f && sy <= q.piece.coreY + q.piece.coreHeight + .001f);
                sum += (double) v[at] * v[next + 1] - (double) v[next] * v[at + 1];
            }
            area.merge(q.piece, Math.abs(sum) * .5, Double::sum);
        }
        for (var corner : f.art.corners) for (var piece : corner.pieces) {
            assertNotNull(area.get(piece));
            assertEquals(piece.coreWidth * piece.coreHeight * corner.scale * corner.scale, area.get(piece), .2,
                "Splitting for actor occlusion must neither duplicate nor discard painted source area");
        }
    }

    @Test void mixedCornersSplitAtTheGroundAlignedFoldAndKeepPageGroupsStable() throws Exception {
        Fixture f = fixture(); var scene = f.art.place(f.layout, f.walls);
        int previousLayer = -1, previousPage = -1, backMixed = 0, frontMixed = 0;
        for (var q : scene.quads) {
            assertTrue(q.renderLayer >= previousLayer);
            if (q.renderLayer == previousLayer) assertTrue(q.piece.page >= previousPage);
            previousLayer = q.renderLayer; previousPage = q.piece.page;
            assertEquals(0, q.sortDepth);
            if (q.corner.key.equals("ne")) assertEquals(PaintedBorderArt.BACK_JOIN_LAYER, q.renderLayer);
            else if (q.corner.key.equals("sw")) assertEquals(PaintedBorderArt.FRONT_JOIN_LAYER, q.renderLayer);
            else {
                float slope = q.corner.key.equals("nw") ? -.5f : .5f;
                float rootX = (q.worldX - q.worldY) * .70710678f, rootY = (q.worldX + q.worldY) * .35355339f;
                boolean back = q.renderLayer == PaintedBorderArt.BACK_JOIN_LAYER;
                if (back) backMixed++; else { assertEquals(PaintedBorderArt.FRONT_JOIN_LAYER, q.renderLayer); frontMixed++; }
                for (int i = 0; i < q.vertices.length; i += 5) {
                    float distance = q.vertices[i + 1] - rootY - 100f - slope * (q.vertices[i] - rootX);
                    assertTrue(back ? distance >= -.001f : distance <= .001f,
                        "The rear arm must stay behind actors while the near arm remains a foreground wall");
                }
            }
        }
        assertTrue(backMixed > 4 && frontMixed > 4);
    }

    @Test void oldWallCutsRemainAffineAndOriginalGeometryAndGateOpeningStayIndependent() throws Exception {
        Fixture f = fixture(); float[][] original = new float[f.walls.quads.length][];
        for (int i = 0; i < original.length; i++) original[i] = f.walls.quads[i].vertices.clone();
        var scene = f.art.place(f.layout, f.walls); var gate = new GateWallOpening(f.walls, f.layout, f.atlas);
        int hiddenBacking = 0, partialCuts = 0, retained = 0;
        for (int i = 0; i < original.length; i++) {
            var q = f.walls.quads[i]; float[] cut = scene.wallVertices[i];
            assertArrayEquals(original[i], q.vertices, "New corner wraps may not rewrite the original painted-wall bank");
            if (q.painting.name.equals("corner")) { assertNotNull(cut); assertEquals(0, cut.length); hiddenBacking++; continue; }
            if (cut == null) { retained++; continue; }
            assertNull(gate.vertices[i], "Corner trims and the gate opening must never compete for the same old wall rectangle");
            if (cut.length == 0) continue;
            assertEquals(20, cut.length); partialCuts++;
            for (int v = 0; v < 4; v++) {
                int at = v * 5, a = v == 0 || v == 3 ? 0 : 5, b = v == 0 || v == 3 ? 15 : 10;
                float t = (cut[at] - original[i][0]) / (original[i][15] - original[i][0]);
                assertTrue(t >= -.0001f && t <= 1.0001f);
                for (int component : new int[]{1, 3, 4}) assertEquals(original[i][a + component]
                    + (original[i][b + component] - original[i][a + component]) * t, cut[at + component], .0001f);
            }
        }
        assertTrue(hiddenBacking >= 4 && partialCuts >= 8 && retained > 80);
    }

    @Test void malformedAtlasPageOrUnboundedScaleFailsBeforeRendering() throws Exception {
        Fixture f = fixture(); JsonValue pages = read("assets/art/painted-corners.json");
        pages.get("pages").get(3).get("file").set(new JsonValue("arena-border.png"));
        assertThrows(IllegalArgumentException.class, () -> new PaintedCornerArt(pages, f.atlas, null));
        JsonValue scale = read("assets/art/painted-corners.json");
        scale.get("corners").get("ne").get("worldScale").set(new JsonValue(20));
        assertThrows(IllegalArgumentException.class, () -> new PaintedCornerArt(scale, f.atlas, null));
    }
}
