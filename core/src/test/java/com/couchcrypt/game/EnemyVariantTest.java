package com.couchcrypt.game;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyVariantTest {
    private static final float EPSILON = .001f;

    @Test void spawnedRolesComposeWithPhaseCoopAndEliteScaling() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(1);
        world.elapsed = 720;

        ArenaWorld.Enemy moss = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        assertStats(moss, 95 * 2.1f * 1.2f * 4 * 1.4f, 18 * 1.27f, 1.12f * .85f);
        ArenaWorld.Enemy frost = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        assertStats(frost, 32 * 2.1f * 1.2f * .8f, 12 * 1.27f, 1.12f * 1.25f);
        ArenaWorld.Enemy ember = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.EMBER);
        assertStats(ember, 35 * 2.1f * 1.2f * .85f, 12 * 1.27f * 1.25f, 1.12f);
        ArenaWorld.Enemy dusk = spawn(world, ArenaWorld.EnemyKind.WRAITH, false, EnemyVariant.DUSK);
        assertStats(dusk, 18 * 2.1f * 1.2f * 1.15f, 8 * 1.27f * .85f, 1.12f * 1.1f);

        ArenaWorld.Enemy boss = spawn(world, ArenaWorld.EnemyKind.BOSS, false, EnemyVariant.ORIGINAL);
        assertStats(boss, 8200 * 1.6f, 20, 1);
    }

    @Test void frostClosesFasterAndMossSlowerThroughActualMovement() throws Exception {
        EnemyVariant[] variants = {EnemyVariant.ORIGINAL, EnemyVariant.MOSS, EnemyVariant.FROST};
        float[] expectedSpeeds = {57, 57 * .85f, 57 * 1.25f};
        for (int i = 0; i < variants.length; i++) {
            ArenaWorld world = solo(ArenaTimeline.SHORT);
            ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, variants[i]);
            float x = enemy.x;
            world.update(ArenaWorld.STEP);
            assertEquals(expectedSpeeds[i] * ArenaWorld.STEP, x - enemy.x, EPSILON, variants[i].name());
            assertTrue(enemy.moving);
        }
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy elite = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        float x = elite.x;
        world.update(ArenaWorld.STEP);
        assertEquals(37 * .85f * 1.15f * ArenaWorld.STEP, x - elite.x, EPSILON,
            "Elite movement keeps its existing additional speed factor");
    }

    @Test void cultistShotsDealVariantDamageEvenAfterCasterSlotReuse() throws Exception {
        EnemyVariant[] variants = {EnemyVariant.EMBER, EnemyVariant.DUSK};
        float[] expectedDamage = {15, 10.2f};
        for (int i = 0; i < variants.length; i++) {
            ArenaWorld world = solo(ArenaTimeline.SHORT);
            ArenaWorld.Enemy caster = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, variants[i]);
            caster.x = 350; caster.y = 0; caster.attackCooldown = 0;
            for (int tick = 0; tick < 44; tick++) world.update(ArenaWorld.STEP);
            ArenaWorld.Shot shot = world.shots[0];
            assertTrue(shot.active); assertEquals(1, world.hostileShotsFired);
            assertEquals(expectedDamage[i], shot.damage, EPSILON);
            assertEquals(-180, shot.vx, EPSILON, "Variant damage does not speed up the projectile");
            caster.active = false; world.activeEnemies--;
            ArenaWorld.Enemy replacement = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.ORIGINAL);
            assertSame(caster, replacement);
            assertEquals(12, replacement.damage, EPSILON);
            shot.x = world.players[0].x; shot.y = world.players[0].y;
            world.update(ArenaWorld.STEP);
            assertFalse(shot.active);
            assertEquals(100 - expectedDamage[i], world.players[0].health, EPSILON);
            assertEquals(expectedDamage[i], world.players[0].damageTaken, EPSILON);
        }
    }

    @Test void reusedEnemySlotsReplaceModifiersRatherThanCompoundingThem() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        assertStats(enemy, 95 * 4 * 1.4f, 18, .85f);
        enemy.health = 1; enemy.active = false; world.activeEnemies--;
        ArenaWorld.Enemy reused = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        assertSame(enemy, reused);
        assertStats(reused, 32 * .8f, 12, 1.25f);
        reused.active = false; world.activeEnemies--;
        reused = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.ORIGINAL);
        assertSame(enemy, reused);
        assertStats(reused, 35, 12, 1);
        world.reset(); world.activatePlayer(0);
        reused = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.ORIGINAL);
        assertSame(enemy, reused);
        assertStats(reused, 32, 12, 1);
    }

    @Test void choicePauseAndReactivatingParticipantPreserveExistingVariantStats() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        enemy.health -= 3;
        float health = enemy.health, maxHealth = enemy.maxHealth, speed = enemy.speedMultiplier, x = enemy.x;
        world.progression.addXp(world.progression.xpToNext);
        assertTrue(world.choicePending());
        world.update(.1f);
        world.activatePlayer(0); // Existing participant reconnect is a no-op, not a fresh spawn.
        assertEquals(EnemyVariant.FROST, enemy.variant);
        assertEquals(x, enemy.x); assertEquals(health, enemy.health); assertEquals(maxHealth, enemy.maxHealth);
        assertEquals(speed, enemy.speedMultiplier);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(ArenaWorld.STEP);
        assertTrue(enemy.x < x);
        assertEquals(health, enemy.health); assertEquals(maxHealth, enemy.maxHealth);
        assertEquals(speed, enemy.speedMultiplier);
    }

    private static void assertStats(ArenaWorld.Enemy enemy, float health, float damage, float speed) {
        assertEquals(health, enemy.health, EPSILON);
        assertEquals(health, enemy.maxHealth, EPSILON);
        assertEquals(damage, enemy.damage, EPSILON);
        assertEquals(speed, enemy.speedMultiplier, EPSILON);
    }

    private static ArenaWorld solo(ArenaTimeline timeline) {
        ArenaWorld world = new ArenaWorld(timeline);
        world.activatePlayer(0); world.players[0].attackCooldown = 20; world.players[0].invulnerable = 0;
        return world;
    }

    /** Exercise the real spawn initializer without advancing a survival schedule or exposing a test API. */
    private static ArenaWorld.Enemy spawn(ArenaWorld world, ArenaWorld.EnemyKind kind, boolean elite,
                                          EnemyVariant variant) throws Exception {
        Method spawn = ArenaWorld.class.getDeclaredMethod("spawn", ArenaWorld.EnemyKind.class,
            boolean.class, ArenaWorld.Player.class, float.class);
        spawn.setAccessible(true);
        for (int attempt = 0; attempt < 64; attempt++) {
            ArenaWorld.Enemy enemy = (ArenaWorld.Enemy) spawn.invoke(world, kind, elite, world.players[0], 0f);
            assertNotNull(enemy);
            if (enemy.variant == variant) return enemy;
            enemy.active = false; world.activeEnemies--;
        }
        throw new AssertionError("No " + variant + " spawn for " + kind);
    }
}
