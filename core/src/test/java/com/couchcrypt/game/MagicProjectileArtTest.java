package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagicProjectileArtTest {
    @Test void allMissilesAnimateAndLoopWithoutLoadingAnotherTexture() throws Exception {
        JsonValue json = source("magic-projectiles.json");
        MagicProjectileArt.Metadata bank = new MagicProjectileArt.Metadata(json);
        assertEquals(0, json.getLong("addedTextureBytes"));
        assertEquals("gameplay-environment.png", bank.file);
        for (MagicProjectileArt.Clip clip : bank.clips) {
            assertEquals(.64f, clip.duration, .0001f);
            assertEquals(clip.frames[0], clip.sample(0));
            assertEquals(clip.frames[0], clip.sample(Float.NaN));
            assertEquals(clip.frames[1], clip.sample(.081f));
            assertEquals(clip.frames[7], clip.sample(.639f));
            assertEquals(clip.frames[0], clip.sample(clip.duration));
            assertEquals(clip.frames[0], clip.sample(clip.duration * 3 + .01f));
            assertTrue(clip.radius > 30 && clip.radius < 120);
        }
    }

    @Test void supplementalSpritesLeaveExistingGameplaySamplesAndTheirGuttersUntouched() throws Exception {
        MagicProjectileArt.Metadata bank = new MagicProjectileArt.Metadata(source("magic-projectiles.json"));
        JsonValue gameplay = source("gameplay.json");
        for (MagicProjectileArt.Frame added : bank.frames) {
            for (JsonValue frame = gameplay.get("frames").child; frame != null; frame = frame.next) {
                if (frame.getInt("page") != 2) continue;
                int[] r = frame.get("rect").asIntArray();
                assertFalse(added.x - 2 < r[0] + r[2] + 2 && r[0] - 2 < added.x + added.width + 2
                    && added.y - 2 < r[1] + r[3] + 2 && r[1] - 2 < added.y + added.height + 2,
                    "Packed magic frames must not borrow an existing sprite's sampling gutter");
            }
        }
    }

    @Test void corruptHeadGeometryPageAndAnimationTimingFailBeforeRendering() throws Exception {
        JsonValue pivot = source("magic-projectiles.json");
        pivot.get("frames").get(0).get("pivot").get(0).set(new JsonValue(-1L));
        assertThrows(IllegalArgumentException.class, () -> new MagicProjectileArt.Metadata(pivot));
        JsonValue page = source("magic-projectiles.json");
        page.get("size").get(0).set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new MagicProjectileArt.Metadata(page));
        JsonValue timing = source("magic-projectiles.json");
        timing.get("animations").get(0).get("durationsMs").get(0).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new MagicProjectileArt.Metadata(timing));
    }

    private static JsonValue source(String name) throws Exception {
        Path path = Path.of("../assets/art/" + name);
        if (!Files.exists(path)) path = Path.of("assets/art/" + name);
        return new JsonReader().parse(Files.readString(path));
    }
}
