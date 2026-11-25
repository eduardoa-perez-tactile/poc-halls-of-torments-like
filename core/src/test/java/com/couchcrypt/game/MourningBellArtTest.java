package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MourningBellArtTest {
    @Test void authoredFacingsAndJawPosesKeepOneCanvasAndGroundRoot() throws Exception {
        GameplayAtlas atlas = atlas();
        GameplayAtlas.Actor bell = atlas.actors[ActorAnimation.Actor.MOURNING_BELL.ordinal()];
        assertEquals(96, bell.canvasWidth); assertEquals(96, bell.canvasHeight);
        Set<Integer> unique = new HashSet<>();
        for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) {
            var idle = bell.clip(ActorAnimation.State.IDLE, facing);
            var move = bell.clip(ActorAnimation.State.MOVE, facing);
            var attack = bell.clip(ActorAnimation.State.ATTACK, facing);
            var windup = bell.clip(ActorAnimation.State.WINDUP, facing);
            int idleFrame = idle.frame(0), attackFrame = attack.frame(0);
            assertTrue(unique.add(idleFrame)); assertTrue(unique.add(attackFrame));
            assertEquals(idleFrame, move.frame(5));
            assertNotEquals(idleFrame, attackFrame);
            assertEquals(idleFrame, windup.frame(0));
            assertEquals(attackFrame, windup.frame(.2f));
            for (int index : new int[]{idleFrame, attackFrame}) {
                GameplayAtlas.Frame frame = atlas.frames[index];
                assertEquals(4, frame.page); assertEquals(48, frame.pivotX); assertEquals(96, frame.pivotY);
                assertEquals(96, frame.sourceWidth); assertEquals(96, frame.sourceHeight);
                assertTrue(frame.width >= 60 && frame.width <= 80);
                assertTrue(frame.height >= 60 && frame.height <= 80);
            }
        }
        assertEquals(8, unique.size());
    }

    @Test void boundedManagedPageHasNonoverlappingTwoPixelExtrudedGutters() throws Exception {
        GameplayAtlas atlas = atlas();
        var page = atlas.pages[4];
        assertEquals("gameplay-mourning-bell.png", page.file());
        assertEquals(256, page.width()); assertEquals(256, page.height());
        BufferedImage image = ImageIO.read(root().resolve("assets/art/" + page.file()).toFile());
        assertEquals(page.width(), image.getWidth()); assertEquals(page.height(), image.getHeight());
        for (int i = 0; i < atlas.frames.length; i++) {
            GameplayAtlas.Frame a = atlas.frames[i];
            if (a.page != 4) continue;
            for (int j = i + 1; j < atlas.frames.length; j++) {
                GameplayAtlas.Frame b = atlas.frames[j];
                if (b.page == 4) assertFalse(a.x - 2 < b.x + b.width + 2 && a.x + a.width + 2 > b.x - 2
                    && a.y - 2 < b.y + b.height + 2 && a.y + a.height + 2 > b.y - 2);
            }
            for (int y = -2; y < a.height + 2; y++) {
                for (int x = -2; x < a.width + 2; x++) {
                    if (x >= 0 && x < a.width && y >= 0 && y < a.height) continue;
                    int sampleX = a.x + Math.max(0, Math.min(a.width - 1, x));
                    int sampleY = a.y + Math.max(0, Math.min(a.height - 1, y));
                    assertEquals(image.getRGB(sampleX, sampleY), image.getRGB(a.x + x, a.y + y));
                }
            }
        }
    }

    @Test void appendPreservesExistingFrameIdentitiesRegionsAndResidentTextureBytes() throws Exception {
        JsonValue current = source(root().resolve("assets/art/gameplay.json"));
        JsonValue baseline;
        try (ZipFile zip = new ZipFile(root().resolve("art-source/crypt-wall-join-repair/bake-inputs.zip").toFile())) {
            baseline = new JsonReader().parse(new String(zip.getInputStream(zip.getEntry("baseline/gameplay.json")).readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String key : new String[]{"frames", "actors", "pages"}) {
            JsonValue old = baseline.require(key), now = current.require(key);
            for (int i = 0; i < old.size; i++) assertEquals(json(old.get(i)), json(now.get(i)), key + " " + i);
        }
        assertEquals(json(baseline.require("regions")), json(current.require("regions")));
        assertEquals(baseline.require("frames").size + 8, current.require("frames").size);
        JsonValue report = source(root().resolve("art-source/mourning-bell/bake-report.json"));
        for (JsonValue hash = report.require("oldGameplayPageHashes").child; hash != null; hash = hash.next) {
            assertEquals(hash.asString(), sha256(root().resolve("assets/art/" + hash.name)));
        }
        assertEquals(262_144, report.getLong("textureBytesAdded"));
        assertEquals(report.getString("sourceSha256"), sha256(root().resolve("art-source/mourning-bell/generated-facing-sheet.png")));
    }

    private static GameplayAtlas atlas() throws Exception { return new GameplayAtlas(source(root().resolve("assets/art/gameplay.json"))); }
    private static JsonValue source(Path path) throws Exception { return new JsonReader().parse(Files.readString(path)); }
    private static String json(JsonValue value) { return value.toJson(JsonWriter.OutputType.json); }
    private static String sha256(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("."); }
}
