package com.couchcrypt.game;

import com.badlogic.gdx.graphics.OrthographicCamera;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillExpansionCullingTest {
    @Test void retainsEffectsWhoseEdgesIntersectEitherPaneAndRejectsOnlyCompleteExits() {
        OrthographicCamera camera = new OrthographicCamera();
        camera.viewportWidth = 650; camera.viewportHeight = 730;
        camera.position.set(400, -200, 0);
        assertTrue(SkillExpansionArt.visible(camera, 400 + 325 + 100, -200, 100, 50));
        assertTrue(SkillExpansionArt.visible(camera, 400, -200 - 365 - 50, 100, 50));
        assertTrue(SkillExpansionArt.visible(camera, 400 + 325 + 101, -200, 100, 50));
        assertFalse(SkillExpansionArt.visible(camera, 400 + 325 + 102, -200, 100, 50));
        assertFalse(SkillExpansionArt.visible(camera, 400, -200 - 365 - 52, 100, 50));
        // Rotated 54-unit Reap squares keep their full half-diagonal and elevated origin.
        assertTrue(SkillExpansionArt.visible(camera, 400 + 325 + 38, -200 + 365 + 38, 39, 39));
        assertFalse(SkillExpansionArt.visible(camera, 400 + 325 + 41, -200 + 365 + 38, 39, 39));
        camera.zoom = 2f;
        assertTrue(SkillExpansionArt.visible(camera, 400 + 600, -200, 11, 5.5f));
    }
}
