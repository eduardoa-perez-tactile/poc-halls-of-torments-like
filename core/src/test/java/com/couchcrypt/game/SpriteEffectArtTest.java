package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpriteEffectArtTest {
    @Test void completeBankFitsCombinedBudgetAndKeepsFilteredFramesIsolated() throws Exception {
        SpriteEffectArt.Metadata metadata = metadata();
        assertEquals(72, metadata.frames.length);
        assertEquals(SpriteEffectArt.Type.values().length, metadata.clips.length);
        // The resident application, including its live QR, was measured before this bank.
        assertTrue(32_467_788L + metadata.textureBytes <= 32L * 1_048_576,
            "A new effect bank must fit the remaining combined texture headroom");
        BufferedImage png = ImageIO.read(art("sprite-effects.png").toFile());
        assertEquals(metadata.width, png.getWidth());
        assertEquals(metadata.height, png.getHeight());
        for (int i = 0; i < metadata.frames.length; i++) {
            SpriteEffectArt.Frame frame = metadata.frames[i];
            assertTrue(frame.x >= 2 && frame.y >= 2);
            assertTrue(frame.x + frame.width + 2 <= metadata.width);
            assertTrue(frame.y + frame.height + 2 <= metadata.height);
            for (int j = i + 1; j < metadata.frames.length; j++) {
                SpriteEffectArt.Frame other = metadata.frames[j];
                assertFalse(overlap(frame.x - 2, frame.y - 2, frame.width + 4, frame.height + 4,
                    other.x - 2, other.y - 2, other.width + 4, other.height + 4),
                    "Sampling gutters must never contain another effect's pixels");
            }
            // One neighbour pixel is extruded; the second reserved pixel is transparent.
            for (int x = frame.x; x < frame.x + frame.width; x++) {
                assertEquals(png.getRGB(x, frame.y), png.getRGB(x, frame.y - 1));
                assertEquals(png.getRGB(x, frame.y + frame.height - 1),
                    png.getRGB(x, frame.y + frame.height));
                assertEquals(0, png.getRGB(x, frame.y - 2) >>> 24);
                assertEquals(0, png.getRGB(x, frame.y + frame.height + 1) >>> 24);
            }
            for (int y = frame.y; y < frame.y + frame.height; y++) {
                assertEquals(png.getRGB(frame.x, y), png.getRGB(frame.x - 1, y));
                assertEquals(png.getRGB(frame.x + frame.width - 1, y),
                    png.getRGB(frame.x + frame.width, y));
                assertEquals(0, png.getRGB(frame.x - 2, y) >>> 24);
                assertEquals(0, png.getRGB(frame.x + frame.width + 1, y) >>> 24);
            }
        }
    }

    @Test void normalizedSamplingUsesAuthoredFrameWeightsAndCallerOwnedLifetimes() throws Exception {
        SpriteEffectArt.Metadata metadata = metadata();
        for (SpriteEffectArt.Clip clip : metadata.clips) {
            assertEquals(clip.frames[0], clip.sample(-1));
            assertEquals(clip.frames[0], clip.sample(Float.NaN));
            assertEquals(clip.frames[7], clip.sample(1));
            assertEquals(clip.frames[7], clip.sample(10));
            for (int i = 0; i < clip.frames.length - 1; i++) {
                float boundary = clip.ends[i] / clip.duration;
                assertEquals(clip.frames[i], clip.sample(boundary - .0001f));
                assertEquals(clip.frames[i + 1], clip.sample(boundary + .0001f));
            }
        }

        ArenaWorld world = new ArenaWorld();
        world.activatePlayer(0);
        ArenaWorld.Effect effect = world.effects[0];
        effect.active = true;
        effect.kind = ArenaWorld.EffectKind.HIT;
        effect.maxTtl = .24f;
        effect.ttl = .12f;
        SpriteEffectArt.Clip contact = metadata.clips[SpriteEffectArt.Type.CONTACT.ordinal()];
        int before = contact.sample(1f - effect.ttl / effect.maxTtl);
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(.12f, effect.ttl, "Phone choices retain the combat-owned effect clock");
        assertEquals(before, contact.sample(1f - effect.ttl / effect.maxTtl));
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(.1f);
        assertTrue(effect.ttl < .12f);
        assertNotEquals(before, contact.sample(1f - effect.ttl / effect.maxTtl));
        world.update(.1f);
        assertFalse(effect.active, "The pooled lifetime clears the sprite instead of leaving its last frame");
    }

    @Test void rotatingGroundSpritesKeepsTheActualIsometricTargetingEnvelope() {
        float radius = 800f;
        for (int point = 0; point < 16; point++) {
            double a = point * Math.PI * 2 / 16;
            float x = radius * (float) Math.cos(a);
            float y = radius * .5f * (float) Math.sin(a);
            for (int turn = 0; turn < 8; turn++) {
                float angle = turn * (float) Math.PI / 4;
                float rotatedX = SpriteEffectArt.groundX(x, y, angle);
                float rotatedY = SpriteEffectArt.groundY(x, y, angle);
                assertEquals(radius, Math.sqrt(rotatedX * rotatedX + 4f * rotatedY * rotatedY), .03,
                    "Turning a slash must rotate on the 2:1 ground plane rather than tilt its ellipse");
            }
        }
    }

    @Test void malformedGeometryOrTimingIsRejectedBeforeAnyGlResourceIsAllocated() throws Exception {
        JsonValue offset = source();
        offset.get("frames").get(0).get("offset").get(0).set(new JsonValue(9999L));
        assertThrows(IllegalArgumentException.class, () -> new SpriteEffectArt.Metadata(offset));
        JsonValue page = source();
        page.get("size").get(0).set(new JsonValue(1024L));
        assertThrows(IllegalArgumentException.class, () -> new SpriteEffectArt.Metadata(page));
        JsonValue timing = source();
        timing.get("animations").get(0).get("durationsMs").get(0).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new SpriteEffectArt.Metadata(timing));
    }

    private static boolean overlap(int x, int y, int width, int height,
                                   int otherX, int otherY, int otherWidth, int otherHeight) {
        return x < otherX + otherWidth && otherX < x + width
            && y < otherY + otherHeight && otherY < y + height;
    }

    private static Path art(String name) {
        Path path = Path.of("../assets/art", name);
        return Files.exists(path) ? path : Path.of("assets/art", name);
    }

    private static JsonValue source() throws Exception {
        return new JsonReader().parse(Files.readString(art("sprite-effects.json")));
    }

    private static SpriteEffectArt.Metadata metadata() throws Exception {
        return new SpriteEffectArt.Metadata(source());
    }
}
