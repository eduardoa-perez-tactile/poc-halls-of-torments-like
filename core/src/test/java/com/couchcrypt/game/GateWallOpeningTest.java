package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GateWallOpeningTest {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;

    @Test void seatedGateCoversTheWholeOpeningAtBothWallFacesWithActualResidentPaint() throws Exception {
        Fixture fixture = fixture();
        ArenaLayout.Placement gate = gate(fixture.layout);
        ArenaLayout.WallRun attached = null;
        for (var run : fixture.layout.wallRuns) if (run.alongX()
            && (attached == null || Math.abs(run.y() - gate.y) < Math.abs(attached.y() - gate.y))) attached = run;
        assertNotNull(attached);
        assertEquals(attached.y(), gate.y, .001f, "Gate must straddle the north wall, not stand in front of its inner face");
        assertTrue(attached.y() > 0);
        var region = fixture.atlas.regions.get(gate.region);
        var frame = fixture.atlas.frames[region.frame()];
        float excessDepth = (region.footprintWorldY() - attached.thickness()) * .5f;
        assertTrue(excessDepth >= 0 && excessDepth <= 8f, "Only the painted plinth's small proud edge should extend past the wall");
        BufferedImage page = page(fixture.atlas, frame.page);
        float rootX = (gate.x - gate.y) * ISO_X;
        int samples = 0, minimumAlpha = 255;
        // Exercise the cut edges and interior through the full wall thickness and above its tallest ordinary top.
        for (int column = 0; column <= 60; column++) {
            float dx = fixture.opening.left + (fixture.opening.right - fixture.opening.left) * column / 60f - rootX;
            for (int depthStep = 0; depthStep <= 12; depthStep++) {
                float depth = -attached.thickness() * .5f + attached.thickness() * depthStep / 12f;
                float along = dx / ISO_X + depth;
                for (int height = 0; height <= 110; height += 2) {
                    float dy = (along + depth) * ISO_Y + height;
                    float sourceYup = (dy - dx * region.renderShearY()) / (gate.scale * region.renderScaleY());
                    int x = frame.x + Math.round(dx / gate.scale - frame.left);
                    int y = frame.y + Math.round(frame.height + frame.bottom - sourceYup);
                    assertTrue(x >= frame.x && x < frame.x + frame.width && y >= frame.y && y < frame.y + frame.height,
                        "The proposed hole must remain inside the packed gate artwork");
                    minimumAlpha = Math.min(minimumAlpha, page.getRGB(x, y) >>> 24);
                    samples++;
                }
            }
        }
        assertTrue(samples > 40_000);
        assertTrue(minimumAlpha >= 240, "The wall cut may never expose transparent gaps around the gate's jambs or threshold");
    }

    @Test void cutRetainsOriginalAffinePaintAndNeverChangesAnyOtherWallOrBacking() throws Exception {
        Fixture fixture = fixture();
        float[][] original = new float[fixture.scene.quads.length][];
        for (int i = 0; i < original.length; i++) original[i] = fixture.scene.quads[i].vertices.clone();
        GateWallOpening cut = new GateWallOpening(fixture.scene, fixture.layout, fixture.atlas);
        ArenaLayout.Placement gate = gate(fixture.layout);
        int changed = 0, untouched = 0, emittedQuads = 0;
        for (int i = 0; i < original.length; i++) {
            var q = fixture.scene.quads[i];
            assertArrayEquals(original[i], q.vertices, "Clipping must not mutate the shared whole-painting geometry");
            float[] vertices = cut.vertices[i];
            if (vertices == null) { untouched++; continue; }
            changed++;
            assertSame(fixture.art.x, q.painting);
            assertEquals(PaintedBorderArt.BACK_WALL_LAYER, q.renderLayer);
            assertEquals(gate.y, q.worldY, .001f, "No south/east/west wall or corner may receive the gate opening");
            assertEquals(0, vertices.length % 20);
            float retainedWidth = 0;
            for (int quad = 0; quad < vertices.length; quad += 20) {
                emittedQuads++;
                float left = vertices[quad], right = vertices[quad + 10];
                assertTrue(right <= cut.left + .001f || left >= cut.right - .001f,
                    "No retained stone rectangle may bridge the doorway");
                retainedWidth += right - left;
                var page = fixture.atlas.pages[q.piece.page];
                for (int vertex = quad; vertex < quad + 20; vertex += 5) {
                    float fraction = (vertices[vertex] - q.vertices[0]) / (q.vertices[10] - q.vertices[0]);
                    boolean top = vertex == quad + 5 || vertex == quad + 10;
                    int a = top ? 5 : 0, b = top ? 10 : 15;
                    assertEquals(q.vertices[a + 1] + (q.vertices[b + 1] - q.vertices[a + 1]) * fraction,
                        vertices[vertex + 1], .001f, "The sheared wall's original row slope must survive the cut");
                    assertEquals(q.vertices[a + 3] + (q.vertices[b + 3] - q.vertices[a + 3]) * fraction,
                        vertices[vertex + 3], .000001f, "A cut edge must sample its original position in one continuous painting");
                    assertEquals(q.vertices[a + 4], vertices[vertex + 4], .000001f);
                    float atlasX = vertices[vertex + 3] * page.width(), atlasY = vertices[vertex + 4] * page.height();
                    assertTrue(atlasX >= q.piece.atlasX - .001f && atlasX <= q.piece.atlasX + q.piece.width + .001f);
                    assertTrue(atlasY >= q.piece.atlasY - .001f && atlasY <= q.piece.atlasY + q.piece.height + .001f,
                        "New UVs may not reach a neighbouring atlas rectangle");
                }
            }
            float removedWidth = Math.max(0, Math.min(q.vertices[10], cut.right) - Math.max(q.vertices[0], cut.left));
            assertEquals(q.vertices[10] - q.vertices[0] - removedWidth, retainedWidth, .001f,
                "The original wall outside the doorway must remain complete");
        }
        assertEquals(changed, cut.clippedPieces);
        assertTrue(changed > 0 && untouched > changed && emittedQuads > 0);
        assertEquals(fixture.scene.quads.length, cut.vertices.length, "The cut uses one bounded startup geometry cache");
        assertEquals(0, json("assets/art/painted-borders.json").getLong("addedTextureBytes"));
    }

    private record Fixture(GameplayAtlas atlas, ArenaLayout layout, PaintedBorderArt art,
                           PaintedBorderArt.Scene scene, GateWallOpening opening) { }

    private static Fixture fixture() throws Exception {
        GameplayAtlas atlas = new GameplayAtlas(json("assets/art/gameplay.json"));
        ArenaLayout layout = new ArenaLayout(json("assets/arena/crypt-layout.json"), atlas);
        PaintedBorderArt art = new PaintedBorderArt(json("assets/art/painted-borders.json"), atlas, null);
        var scene = art.place(layout, atlas);
        return new Fixture(atlas, layout, art, scene, new GateWallOpening(scene, layout, atlas));
    }

    private static ArenaLayout.Placement gate(ArenaLayout layout) {
        for (var p : layout.placements) if (p.region.equals("sealed-gate")) return p;
        throw new AssertionError("Missing approved north gate");
    }

    private static BufferedImage page(GameplayAtlas atlas, int page) throws Exception {
        return ImageIO.read(root().resolve("assets/art/" + atlas.pages[page].file()).toFile());
    }

    private static JsonValue json(String name) throws Exception {
        return new JsonReader().parse(Files.readString(root().resolve(name)));
    }

    private static Path root() {
        return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of(".");
    }
}
