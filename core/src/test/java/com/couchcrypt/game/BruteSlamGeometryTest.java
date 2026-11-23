package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BruteSlamGeometryTest {
    @Test void simulatedContactsProjectToMeasuredFistsInEveryFacingAndEliteScale() throws Exception {
        JsonValue attacks = source("enemy-attacks.json"), gameplay = source("gameplay.json");
        JsonValue brute = null, base = null;
        for (JsonValue a = attacks.require("actors").child; a != null; a = a.next)
            if (a.getString("kind").equals("BRUTE")) brute = a;
        for (JsonValue a = gameplay.require("actors").child; a != null; a = a.next)
            if (a.getString("id").equals("BRUTE")) base = a;
        assertNotNull(brute); assertNotNull(base);
        float pixelScale = base.getFloat("worldHeight") / base.get("canvas").getInt(1);
        for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) for (boolean elite : new boolean[]{false, true}) {
            float x = EnemyAttackGeometry.bruteSlamX(facing, elite), y = EnemyAttackGeometry.bruteSlamY(facing, elite);
            float sx = (x - y) * .70710678f, sy = (x + y) * .35355339f;
            JsonValue measured = brute.require("slam").require(facing.name());
            float scale = pixelScale * 1.4f * (elite ? 1.18f : 1f);
            assertEquals(measured.getFloat(0) * scale, sx, .0001f, facing + " fist X");
            assertEquals(measured.getFloat(1) * scale, sy, .0001f, facing + " fist Y");
        }
    }

    @Test void everyFacingUsesTheSamePublishedCircleForDamageAndPreservesItsLockedFacing() {
        for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) for (boolean elite : new boolean[]{false, true}) {
            ArenaWorld inside = encounter(facing, elite), outside = encounter(facing, elite);
            ArenaWorld.Enemy a = inside.enemies[0], b = outside.enemies[0];
            assertEquals(ArenaWorld.BRUTE_SLAM_RADIUS, a.attackRadius);
            inside.players[0].x = a.attackTargetX + a.attackRadius + 20 - .01f;
            inside.players[0].y = a.attackTargetY;
            outside.players[0].x = b.attackTargetX + b.attackRadius + 20 + .01f;
            outside.players[0].y = b.attackTargetY;
            for (int tick = 0; tick < 45; tick++) {
                inside.update(ArenaWorld.STEP); outside.update(ArenaWorld.STEP);
            }
            assertEquals(82, inside.players[0].health, facing + " overlapping body is struck");
            assertEquals(100, outside.players[0].health, facing + " clear body escapes");
            assertEquals(facing, ActorAnimation.facing(a, ActorAnimation.state(a)));
            assertEquals(1, inside.bruteSlams); assertEquals(1, outside.bruteSlams);
        }
    }

    private static ArenaWorld encounter(ActorAnimation.Facing facing, boolean elite) {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        world.players[0].attackCooldown = 20; world.players[0].invulnerable = 0;
        ArenaWorld.Enemy e = world.enemies[0]; e.kind = ArenaWorld.EnemyKind.BRUTE;
        e.active = true; e.elite = elite; e.health = e.maxHealth = 1000; e.damage = 18; e.radius = 31;
        e.x = facing == ActorAnimation.Facing.NE ? -80 : facing == ActorAnimation.Facing.SW ? 80 : 0;
        e.y = facing == ActorAnimation.Facing.NW ? -80 : facing == ActorAnimation.Facing.SE ? 80 : 0;
        world.activeEnemies = 1; world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.EnemyAttack.BRUTE_SLAM, e.attack); return world;
    }

    private static JsonValue source(String name) throws Exception {
        Path path = Path.of("../assets/art/" + name);
        if (!Files.exists(path)) path = Path.of("assets/art/" + name);
        return new JsonReader().parse(Files.readString(path));
    }
}
