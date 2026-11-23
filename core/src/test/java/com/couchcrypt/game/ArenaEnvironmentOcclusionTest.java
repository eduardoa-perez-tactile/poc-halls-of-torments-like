package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaEnvironmentOcclusionTest {
    @Test void paintedWallBasesProjectOntoTheFloorAxesWhileUprightsStayVertical() throws Exception {
        Path root = Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of(".");
        GameplayAtlas atlas = new GameplayAtlas(new JsonReader().parse(Files.readString(root.resolve("assets/art/gameplay.json"))));
        var sources = new JsonReader().parse(Files.readString(root.resolve("art-source/crypt-environment-v2/environment.json")));
        for (String name : new String[]{"wall-x", "wall-x-broken", "wall-y", "wall-y-broken"}) {
            var calibration = sources.get("regions").get(name).get("baseCalibration");
            float sourceSlope = calibration.getFloat("normalizedSlopeDown");
            var region = atlas.regions.get(name);
            float shear = region.renderShearY();
            float leftX = -80, rightX = 80;
            float left = ArenaRenderer.environmentProjectedY(-sourceSlope * leftX, leftX, 0, shear);
            float right = ArenaRenderer.environmentProjectedY(-sourceSlope * rightX, rightX, 0, shear);
            assertEquals(name.startsWith("wall-x") ? .5f : -.5f, (right - left) / (rightX - leftX), .00001f,
                "Painted wall ground edges must meet the floor's 2:1 projection");
            assertEquals(100f, ArenaRenderer.environmentProjectedY(-sourceSlope * leftX + 100, leftX, 0, shear) - left, .0001f,
                "Projection calibration preserves vertical body height");
            float scale = .70710678f * (region.footprintWorldX() + region.footprintWorldY()) / region.footprintWidth();
            float frontBaseY = (atlas.frames[region.frame()].pivotY - calibration.getFloat("frontBaseYAtPivot")) * scale;
            assertEquals(-.35355339f * 36, frontBaseY, .001f,
                "Every variant meets the same grounded front plane, rather than stepping at its joins");
        }
    }

    @Test void onlyForegroundPaintCoveringAnActorsUpperBodyReveals() {
        assertTrue(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, 40, 28, 110));
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, -40, 28, 110),
            "A structure behind the actor must remain solid");
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, 0, 28, 110),
            "Equal-depth props render before actors and cannot hide them");
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 120, 40, 28, 110),
            "A nearby actor beside the structure must not fade it");
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, 230, 28, 110),
            "An actor above the painted structure is already visible");
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 90, 0, 40, 28, 110),
            "Low masonry covering only legs stays continuous while the head and upper body remain readable");
    }

    @Test void lowBasesStayOpaqueAndTwoCameraTargetsCanMakeDifferentRevealDecisions() {
        assertFalse(ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, 8, 20, 10),
            "Overlap confined to the retained solid base does not reveal the upper wall");
        boolean firstView = ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 0, 40, 28, 110);
        boolean secondView = ArenaRenderer.environmentOccludesBody(0, -60, 24, 60, 210, 180, 40, 28, 110);
        assertTrue(firstView);
        assertFalse(secondView, "Reveal decisions use each view's actor overlap rather than shared placement opacity");
        assertFalse(ArenaRenderer.environmentOccludesBody(.5f, 0, -100, 24, 100, 125, 0, 30, 20, 110),
            "The far end of a low diagonal wall does not hide an actor beside its lower middle");
        assertTrue(ArenaRenderer.environmentOccludesBody(.5f, 0, -100, 24, 100, 210, 0, 30, 20, 110),
            "A genuinely tall wall at the actor still reveals its upper paint");
    }
}
