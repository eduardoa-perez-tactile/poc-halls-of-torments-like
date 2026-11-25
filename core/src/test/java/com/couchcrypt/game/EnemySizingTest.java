package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemySizingTest {
    @Test void approvedProportionsUseTheActualOpaqueFramesAndPreserveFourFacings() throws Exception {
        GameplayAtlas atlas = atlas();
        var kinds = new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH,
            ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.BRUTE};
        float[] ratios = {.89f, .96f, 1.02f, 1.35f};
        for (int i = 0; i < kinds.length; i++) {
            var actor = atlas.actors[ActorAnimation.actor(kinds[i]).ordinal()];
            float scale = EnemySizing.renderScale(kinds[i], EnemyVariant.ORIGINAL, false);
            var se = atlas.frames[actor.clip(ActorAnimation.State.IDLE, ActorAnimation.Facing.SE).frames[0]];
            assertEquals(103.75f * ratios[i], se.height * actor.pixelScale * scale, .0001f);
            for (var facing : ActorAnimation.Facing.values()) {
                var frame = atlas.frames[actor.clip(ActorAnimation.State.IDLE, facing).frames[0]];
                assertTrue(frame.height * actor.pixelScale * scale > 85f,
                    "Other authored facings must not shrink back to the old small silhouettes");
                assertTrue(2 * EnemySizing.groundRadius(kinds[i], EnemyVariant.ORIGINAL, false)
                    <= frame.width * actor.pixelScale * scale + 1f,
                    "Ground hit circles should fit the opaque width of thin bodies: " + kinds[i] + " " + facing);
            }
        }
    }

    @Test void eliteReplacesMossBulkWhileOtherVariantsKeepTheirSpeciesSize() {
        for (var kind : ArenaWorld.EnemyKind.values()) {
            float normal = EnemySizing.renderScale(kind, EnemyVariant.ORIGINAL, false);
            if (ArenaWorld.isBoss(kind)) {
                assertEquals(kind == ArenaWorld.EnemyKind.MOURNING_BELL ? 1.7f : 2.2f, normal);
                assertEquals(normal, EnemySizing.renderScale(kind, EnemyVariant.MOSS, true));
                assertEquals(54f, EnemySizing.groundRadius(kind, EnemyVariant.MOSS, true));
                continue;
            }
            assertEquals(normal * 1.1f, EnemySizing.renderScale(kind, EnemyVariant.MOSS, false), .00001f);
            for (var variant : EnemyVariant.values()) {
                assertEquals(normal * 1.22f, EnemySizing.renderScale(kind, variant, true), .00001f);
                if (variant != EnemyVariant.MOSS)
                    assertEquals(normal, EnemySizing.renderScale(kind, variant, false));
            }
        }
    }

    @Test void bruteWarningAndDamageAnchorMeetThePaintedFistAtEverySizeAndFacing() throws Exception {
        Path source = art("enemy-attacks.json");
        var metadata = new EnemyAttackArt.Metadata(new JsonReader().parse(Files.readString(source)));
        var actor = metadata.actors[ArenaWorld.EnemyKind.BRUTE.ordinal()];
        for (var variant : new EnemyVariant[]{EnemyVariant.ORIGINAL, EnemyVariant.MOSS, EnemyVariant.FROST})
            for (boolean elite : new boolean[]{false, true}) for (var facing : ActorAnimation.Facing.values()) {
                float x = EnemyAttackGeometry.bruteSlamX(facing, variant, elite);
                float y = EnemyAttackGeometry.bruteSlamY(facing, variant, elite);
                float scale = 84f / 64f * EnemySizing.renderScale(ArenaWorld.EnemyKind.BRUTE, variant, elite);
                assertEquals(actor.slam[facing.ordinal() * 2] * scale, (x - y) * .70710678f, .0001f);
                assertEquals(actor.slam[facing.ordinal() * 2 + 1] * scale, (x + y) * .35355339f, .0001f);
            }
        assertEquals(55f, ArenaWorld.BRUTE_SLAM_RADIUS);
    }

    private static Path art(String name) {
        Path path = Path.of("../assets/art/" + name);
        return Files.exists(path) ? path : Path.of("assets/art/" + name);
    }
    private static GameplayAtlas atlas() throws Exception {
        return new GameplayAtlas(new JsonReader().parse(Files.readString(art("gameplay.json"))));
    }
}
