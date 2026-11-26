package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilePresentationTest {
    @Test void emergenceIsSmallAtReleaseMonotonicAndFullBeforeASecondAnimationFrame() {
        assertEquals(0, ProjectilePresentation.spawnScale(-.025f), "Unborn trail samples must not appear");
        assertEquals(.2f, ProjectilePresentation.spawnScale(0));
        float previous = 0;
        for (int tick = 0; tick <= 12; tick++) {
            float scale = ProjectilePresentation.spawnScale(tick * ArenaWorld.STEP);
            assertTrue(scale >= previous && scale <= 1, "Emergence must never shrink or overshoot");
            previous = scale;
        }
        assertTrue(ProjectilePresentation.spawnScale(ArenaWorld.STEP) < .3f);
        assertEquals(1, ProjectilePresentation.spawnScale(.1f));
        assertEquals(1, ProjectilePresentation.spawnScale(10));
    }

    @Test void actualPlayerAndEnemyShotsEmergeFromTheirExistingCombatClocksAndFreezeWithChoices() {
        ArenaWorld world = ranger();
        ArenaWorld.Enemy caster = cultist(world);
        world.update(ArenaWorld.STEP);
        ArenaWorld.Bolt bolt = world.bolts[0];
        assertTrue(bolt.active);
        assertEquals(760 * ArenaWorld.STEP, bolt.x, .0001f, "No extra launch delay or reduced flight speed");
        assertTrue(ProjectilePresentation.spawnScale(bolt.age) < .3f);
        world.players[0].attackCooldown = 10;
        for (int i = 0; i < 42; i++) world.update(ArenaWorld.STEP);
        ArenaWorld.Shot shot = world.shots[0];
        assertTrue(shot.active);
        assertEquals(ArenaWorld.STEP, shot.age, .0001f);
        assertEquals(caster.x - 180 * ArenaWorld.STEP, shot.x, .0001f);
        assertEquals(10, shot.radius, "Early missile collisions retain their normal radius");
        assertTrue(ProjectilePresentation.spawnScale(shot.age) < .3f);
        float boltScale = ProjectilePresentation.spawnScale(bolt.age);
        float shotScale = ProjectilePresentation.spawnScale(shot.age), shotX = shot.x;
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(boltScale, ProjectilePresentation.spawnScale(bolt.age));
        assertEquals(shotScale, ProjectilePresentation.spawnScale(shot.age));
        assertEquals(shotX, shot.x);
    }

    @Test void reusedProjectileSlotsStartSmallInsteadOfKeepingThePreviousLaunchAge() {
        ArenaWorld world = ranger(); cultist(world);
        ArenaWorld.Bolt reused = world.bolts[0];
        reused.age = 10; reused.active = false;
        world.update(ArenaWorld.STEP);
        assertTrue(reused.active);
        assertEquals(ArenaWorld.STEP, reused.age, .0001f);
        assertTrue(ProjectilePresentation.spawnScale(reused.age) < .3f);
        // Hostile shots use a separate pool but the same per-launch age contract.
        world.players[0].attackCooldown = 10;
        ArenaWorld.Shot hostile = world.shots[0]; hostile.age = 10;
        for (int i = 0; i < 42; i++) world.update(ArenaWorld.STEP);
        assertTrue(hostile.active);
        assertEquals(ArenaWorld.STEP, hostile.age, .0001f);
        assertTrue(ProjectilePresentation.spawnScale(hostile.age) < .3f);
    }

    private static ArenaWorld ranger() {
        ArenaWorld world = new ArenaWorld();
        world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        return world;
    }

    private static ArenaWorld.Enemy cultist(ArenaWorld world) {
        ArenaWorld.Enemy enemy = world.enemies[0];
        enemy.active = true; enemy.kind = ArenaWorld.EnemyKind.CULTIST;
        enemy.x = 250; enemy.y = 0; enemy.health = enemy.maxHealth = 1000;
        world.activeEnemies = 1;
        return enemy;
    }
}
