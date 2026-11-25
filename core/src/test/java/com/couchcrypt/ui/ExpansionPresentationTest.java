package com.couchcrypt.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.PartyProgression;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpansionPresentationTest {
    @Test void heroReferencePagesRetainEverySkillAndEvolutionExactlyOnce() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            List<PartyProgression.Upgrade> found = new ArrayList<>();
            assertEquals(3, CharacterInfoView.pageCount(hero));
            for (int page = 0; page < CharacterInfoView.pageCount(hero); page++) {
                var skills = CharacterInfoView.pageSkills(hero, page);
                assertTrue(skills.size() <= 8);
                assertTrue(skills.stream().allMatch(upgrade -> upgrade.supports(hero)));
                found.addAll(skills);
            }
            assertEquals(com.couchcrypt.game.HeroCatalog.profile(hero).skills(), found);
            assertEquals(found.size(), found.stream().distinct().count());
            assertEquals(3, found.stream().filter(PartyProgression.Upgrade::evolution).count());
        }
    }

    @Test void expandedAtlasPreservesLegacyPixelsAndMatchesManagedUpload() throws Exception {
        BufferedImage atlas = ImageIO.read(art("runtime-ui/skill-icons.png").toFile());
        BufferedImage legacy = ImageIO.read(root().resolve("art-source/survivor-expansion/legacy-skill-icons.png").toFile());
        assertEquals(512, atlas.getWidth()); assertEquals(512, atlas.getHeight());
        byte[] ktx = Files.readAllBytes(art("runtime-ui/skill-icons.ktx"));
        assertEquals(512 * 512 * 4, ByteBuffer.wrap(ktx).order(ByteOrder.LITTLE_ENDIAN).getInt(64));
        int offset = 68;
        for (int y = 0; y < atlas.getHeight(); y++) for (int x = 0; x < atlas.getWidth(); x++) {
            int pixel = atlas.getRGB(x, y);
            if (y < 384) assertEquals(legacy.getRGB(x, y), pixel, "Existing icon pixels are immutable");
            assertEquals(pixel >>> 16 & 255, ktx[offset++] & 255);
            assertEquals(pixel >>> 8 & 255, ktx[offset++] & 255);
            assertEquals(pixel & 255, ktx[offset++] & 255);
            assertEquals(pixel >>> 24, ktx[offset++] & 255);
        }
        // Sixteen glyph cells have isolated transparent gutters for linear filtering.
        for (int cell = 0; cell < 16; cell++) {
            int left = cell % 8 * 64, top = 384 + cell / 8 * 64, occupied = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int alpha = atlas.getRGB(left + x, top + y) >>> 24;
                if (x == 0 || y == 0 || x == 63 || y == 63) assertEquals(0, alpha);
                if (alpha > 128) occupied++;
            }
            assertTrue(occupied > 80, "Every skill/world sprite contains a readable painted silhouette");
        }
        assertArrayEquals(Files.readAllBytes(art("runtime-ui/skill-icons.png")),
            Files.readAllBytes(root().resolve("assets/web/art/skill-icons.png")));
    }

    @Test void currentResidentTexturesIncludingExpansionAndMaximumQrFitCombinedBudget() throws Exception {
        // Read current actual upload dimensions instead of relying on historical allocation totals.
        long total = 360L * 360 * 4 + 3 * 4 + 12 * 12 * 4; // QR, three UI fills, focus rim.
        try (var paths = Files.list(art("runtime-ui"))) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".ktx")).toList()) {
                ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
                total += (long) header.getInt(36) * header.getInt(40) * 4;
            }
        }
        List<String> pngs = new ArrayList<>(List.of("hero-portraits.png", "hud-atlas.png", "item-icons.png"));
        JsonValue gameplay = new JsonReader().parse(Files.readString(art("gameplay.json")));
        for (JsonValue page : gameplay.require("pages")) pngs.add(page.getString("file"));
        for (String metadata : List.of("combat-vfx.json", "sprite-effects.json", "arena-border.json"))
            pngs.add(new JsonReader().parse(Files.readString(art(metadata))).getString("file"));
        for (String file : pngs) {
            BufferedImage image = ImageIO.read(art(file).toFile());
            total += (long) image.getWidth() * image.getHeight() * 4;
        }
        assertEquals(33_483_596L, total);
        assertTrue(total <= 32L * 1_048_576, "All shared art and a live QR must fit 32 MiB together");
    }

    private static Path root() {
        return Files.exists(Path.of("assets/art")) ? Path.of("") : Path.of("..");
    }
    private static Path art(String name) { return root().resolve("assets/art").resolve(name); }
}
