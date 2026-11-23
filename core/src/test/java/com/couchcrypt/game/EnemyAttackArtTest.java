package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyAttackArtTest {
    @Test void allFourFacingAttacksHaveStableRootsAndIdleReturnsWhileAuraLoops() throws Exception {
        EnemyAttackArt.Metadata m = new EnemyAttackArt.Metadata(source());
        for (ArenaWorld.EnemyKind kind : new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST}) {
            EnemyAttackArt.Actor a = m.actors[kind.ordinal()];
            assertNotNull(a); assertEquals(4, a.clips.length);
            for (EnemyAttackArt.Clip c : a.clips) {
                assertEquals(c.frames[0], c.frames[7], "Exact shipped idle is reused at both ends");
                assertEquals(c.frames[0], c.sample(0, false));
                assertEquals(c.frames[7], c.sample(10, false));
                assertEquals(c.frames[4], c.sample(c.ends[3] + .0001f, false));
            }
        }
        assertEquals(1.3f, m.actors[ArenaWorld.EnemyKind.WRAITH.ordinal()].clips[0].duration, .0001f);
        assertEquals(1.45f, m.actors[ArenaWorld.EnemyKind.BRUTE.ordinal()].clips[0].duration, .0001f);
        assertEquals(1.44f, m.actors[ArenaWorld.EnemyKind.CULTIST.ordinal()].clips[0].duration, .0001f);
        assertEquals(1f, m.aura.duration, .0001f);
        assertEquals(m.aura.frames[0], m.aura.sample(1f, true));
        assertEquals(m.aura.frames[0], m.aura.frames[7]);
        assertTrue(m.auraRadius > m.auraGroundRadius);
    }

    @Test void borrowedSpritesNeverOverlapGameplayFramesOrEachOther() throws Exception {
        EnemyAttackArt.Metadata m = new EnemyAttackArt.Metadata(source());
        JsonValue base = new JsonReader().parse(Files.readString(art("gameplay.json")));
        for (int i = 0; i < m.frames.length; i++) {
            EnemyAttackArt.Frame f = m.frames[i];
            for (int j = i + 1; j < m.frames.length; j++) {
                EnemyAttackArt.Frame g = m.frames[j];
                if (f.page == g.page) assertFalse(overlap(f.x, f.y, f.width, f.height, g.x, g.y, g.width, g.height));
            }
            for (JsonValue g = base.require("frames").child; g != null; g = g.next) {
                if (f.page != g.getInt("page")) continue;
                int[] r = g.get("rect").asIntArray();
                assertFalse(overlap(f.x - 2, f.y - 2, f.width + 4, f.height + 4, r[0], r[1], r[2], r[3]));
            }
        }
    }

    @Test void invalidRootsTimingsOrNewTexturesAreRejectedBeforeGl() throws Exception {
        JsonValue root = source(); root.get("frames").get(0).get("pivot").get(1).set(new JsonValue(999L));
        assertThrows(IllegalArgumentException.class, () -> new EnemyAttackArt.Metadata(root));
        JsonValue timing = source(); timing.get("actors").get(0).get("durationsMs").get(0).set(new JsonValue(0L));
        assertThrows(IllegalArgumentException.class, () -> new EnemyAttackArt.Metadata(timing));
        JsonValue memory = source(); memory.get("textureBytesAdded").set(new JsonValue(524288L));
        assertThrows(IllegalArgumentException.class, () -> new EnemyAttackArt.Metadata(memory));
    }

    private static boolean overlap(int x, int y, int w, int h, int xx, int yy, int ww, int hh) {
        return x < xx + ww && xx < x + w && y < yy + hh && yy < y + h;
    }
    private static Path art(String name) {
        Path p = Path.of("../assets/art/" + name); return Files.exists(p) ? p : Path.of("assets/art/" + name);
    }
    private static JsonValue source() throws Exception { return new JsonReader().parse(Files.readString(art("enemy-attacks.json"))); }
}
