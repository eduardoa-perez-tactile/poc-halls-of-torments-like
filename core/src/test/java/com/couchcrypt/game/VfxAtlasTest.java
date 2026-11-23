package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VfxAtlasTest {
    @Test void completeAtlasUsesBoundedStorageAndOneShotsClearInsteadOfHoldingTheLastFleck() throws Exception {
        VfxArt.Metadata metadata = new VfxArt.Metadata(source());
        assertEquals(32, metadata.frames.length); assertEquals(589824, metadata.textureBytes);
        for (VfxArt.Clip clip : metadata.clips) {
            assertEquals(clip.frames[0], clip.sample(0));
            assertEquals(-1, clip.sample(-1)); assertEquals(-1, clip.sample(Float.NaN));
            assertEquals(clip.frames[7], clip.sample(clip.duration - .0001f));
            assertEquals(-1, clip.sample(clip.duration)); assertEquals(-1, clip.sample(10));
            assertTrue(clip.radius > 0);
        }
    }
    @Test void corruptTrimAndOversizedPagesAreRejectedBeforeLoadingTextures() throws Exception {
        JsonValue value = source(); value.get("frames").get(0).get("offset").get(0).set(new JsonValue(999L));
        assertThrows(IllegalArgumentException.class, () -> new VfxArt.Metadata(value));
        JsonValue tooLarge = source(); tooLarge.get("size").get(1).set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new VfxArt.Metadata(tooLarge));
    }
    @Test void malformedTimingCannotCreateAnUnboundedEffect() throws Exception {
        JsonValue value = source(); value.get("animations").get(0).get("durationsMs").get(0).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new VfxArt.Metadata(value));
    }
    private static JsonValue source() throws Exception {
        Path path = Path.of("../assets/art/combat-vfx.json");
        if (!Files.exists(path)) path = Path.of("assets/art/combat-vfx.json");
        return new JsonReader().parse(Files.readString(path));
    }
}
