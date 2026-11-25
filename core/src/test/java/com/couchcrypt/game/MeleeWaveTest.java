package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWaveTest {
    @Test void overlappingTargetUsesLastFacingAndProducesFiniteDamageAndKnockback() {
        ArenaWorld world = solo();
        world.players[0].facingX = 1; world.players[0].facingY = 0;
        ArenaWorld.Enemy target = enemy(world, 0, 0, 0);
        step(world, 1);
        MeleeWave wave = first(world, 0);
        assertEquals(1, wave.dirX); assertEquals(0, wave.dirY); assertEquals(0, wave.angle);
        assertEquals(972, target.health); assertEquals(1, world.swordHits);
        assertTrue(Float.isFinite(target.x)); assertTrue(Float.isFinite(target.y));
        assertTrue(Float.isFinite(target.hitDirectionX)); assertTrue(Float.isFinite(target.hitDirectionY));
        assertEquals(1, target.hitDirectionX); assertEquals(0, target.hitDirectionY);
    }

    @Test void damagesOnlyWhenLeadingRimReachesEachEnemyInsideTheCone() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy near = enemy(world, 0, 40, 0), far = enemy(world, 1, 140, 0);
        ArenaWorld.Enemy behind = enemy(world, 2, -80, 0);
        ArenaWorld.Enemy side = enemy(world, 3, 40, 90);
        ArenaWorld.Enemy beyond = enemy(world, 4, 170, 0);
        step(world, 1);
        assertEquals(1, world.swordSwings);
        assertEquals(0, world.swordHits);
        assertEquals(1000, near.health);
        step(world, 3);
        assertEquals(972, near.health);
        assertEquals(1000, far.health);
        step(world, 9);
        assertEquals(972, far.health);
        assertEquals(2, world.swordHits);
        step(world, 11);
        assertEquals(1000, behind.health);
        assertEquals(1000, side.health);
        assertEquals(1000, beyond.health);
        assertEquals(2, world.swordHits, "The swept interior and fading edge cannot repeat damage");
    }

    @Test void collisionUsesEnemyGroundRadiusAtTheVisibleRim() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy target = enemy(world, 0, 80, 0); target.radius = 20;
        step(world, 5);
        assertEquals(1000, target.health); // Wave radius 55.56, near edge at 60.
        step(world, 1);
        assertEquals(972, target.health);
    }

    @Test void releasedWaveKeepsItsOriginReachAimAndDamageAfterThePlayerMovesOrBuildChanges() {
        ArenaWorld world = solo();
        world.players[0].cleaveMultiplier = 1.6f;
        ArenaWorld.Enemy target = enemy(world, 0, 240, 0);
        step(world, 1);
        MeleeWave wave = first(world, 0);
        assertEquals(256, wave.reach, .001f);
        world.players[0].x = 500; world.players[0].y = 500;
        world.players[0].facingX = -1; world.players[0].facingY = 0;
        world.players[0].cleaveMultiplier = 1; world.players[0].damageMultiplier = 10;
        step(world, 13);
        assertEquals(972, target.health);
        assertEquals(0, wave.x); assertEquals(0, wave.y);
        assertEquals(256, wave.reach, .001f);
        assertEquals(1, wave.dirX); assertEquals(0, wave.dirY);
    }

    @Test void cosmeticPoolExhaustionCannotSuppressWaveOrDamage() {
        ArenaWorld world = solo();
        for (ArenaWorld.Effect effect : world.effects) {
            effect.active = true; effect.kind = ArenaWorld.EffectKind.COLLECT; effect.ttl = effect.maxTtl = 10;
        }
        ArenaWorld.Enemy target = enemy(world, 0, 80, 0);
        step(world, 10);
        assertEquals(972, target.health);
        assertEquals(1, world.swordHits);
        assertNotNull(first(world, 0));
        for (ArenaWorld.Effect effect : world.effects) assertNotEquals(ArenaWorld.EffectKind.SLASH, effect.kind);
    }

    @Test void aNewSpawnGenerationAheadOfTheRimCanBeHitButSweptInteriorCannot() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy target = enemy(world, 0, 40, 0);
        step(world, 4);
        assertEquals(972, target.health);
        target.spawnId++; target.health = 1000; target.x = 140; target.stunTime = 0;
        step(world, 9);
        assertEquals(972, target.health, "A new enemy reusing a slot ahead is a new collision identity");
        target.spawnId++; target.health = 1000; target.x = 20; target.stunTime = 0;
        step(world, 2);
        assertEquals(1000, target.health, "A new enemy behind the wave receives no retroactive hit");
    }

    @Test void fadingWaveCannotDamageFreshEnemiesEvenAtItsFullRadius() {
        ArenaWorld world = solo();
        enemy(world, 0, 80, 0);
        step(world, 15);
        MeleeWave wave = first(world, 0);
        assertTrue(wave.age > MeleeWave.EXPAND_SECONDS);
        ArenaWorld.Enemy fresh = enemy(world, 1, 160, 0);
        step(world, 7);
        assertEquals(1000, fresh.health);
    }

    @Test void skillAndLootChoicesFreezeWaveAgeAndCollisionUntilAllChoicesResolve() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy target = enemy(world, 0, 140, 0);
        step(world, 3);
        MeleeWave wave = first(world, 0); float age = wave.age;
        world.progression.addXp(world.progression.xpToNext);
        step(world, 3);
        assertEquals(age, wave.age); assertEquals(1000, target.health);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.equipment.grantReward();
        step(world, 3);
        assertEquals(age, wave.age); assertEquals(1000, target.health);
        assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, 0));
        step(world, 10);
        assertEquals(972, target.health, "Pending wave retains release stats across choices");
    }

    @Test void reconnectPreservesWaveWhileRemovalAndResetClearIt() {
        ArenaWorld world = solo(); enemy(world, 0, 140, 0);
        step(world, 1);
        MeleeWave wave = first(world, 0); float age = wave.age;
        world.activatePlayer(0);
        assertTrue(wave.active); assertEquals(age, wave.age);
        world.deactivatePlayer(0);
        assertFalse(wave.active);
        world.activatePlayer(0); step(world, 1);
        assertNotNull(first(world, 0));
        world.reset();
        for (MeleeWave melee : world.meleeWaves) assertFalse(melee.active);
    }

    @Test void legalMaximumHasteRetainsAnOlderFadingWaveAlongsideTheNextAttack() {
        ArenaWorld world = solo();
        enemy(world, 0, 100, 0);
        world.players[0].attackRateMultiplier = 2.4f;
        step(world, 19);
        assertEquals(2, world.swordSwings);
        int active = 0; for (MeleeWave wave : world.meleeWaves) if (wave.active) active++;
        assertEquals(2, active);
        assertEquals(1, world.swordHits);
        step(world, 12);
        assertEquals(2, world.swordHits);
    }

    @Test void coopWavesHaveIndependentOwnersAndDirections() {
        ArenaWorld world = solo();
        world.setHero(1, ArenaWorld.Hero.WARDEN); world.activatePlayer(1);
        world.players[1].x = 500;
        ArenaWorld.Enemy first = enemy(world, 0, 100, 0), second = enemy(world, 1, 400, 0);
        step(world, 11);
        assertEquals(972, first.health); assertEquals(972, second.health);
        assertEquals(28, world.players[0].damageDealt); assertEquals(28, world.players[1].damageDealt);
        assertEquals(1, first(world, 0).dirX); assertEquals(-1, first(world, 1).dirX);
    }

    private static ArenaWorld solo() { ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world; }
    private static ArenaWorld.Enemy enemy(ArenaWorld world, int slot, float x, float y) {
        ArenaWorld.Enemy enemy = world.enemies[slot];
        enemy.active = true; enemy.x = x; enemy.y = y; enemy.health = enemy.maxHealth = 1000;
        enemy.radius = 0; enemy.speedMultiplier = 0; enemy.spawnId = slot + 1;
        world.activeEnemies++;
        return enemy;
    }
    private static MeleeWave first(ArenaWorld world, int owner) {
        for (MeleeWave wave : world.meleeWaves) if (wave.active && wave.owner == owner) return wave;
        return null;
    }
    private static void step(ArenaWorld world, int count) { for (int i = 0; i < count; i++) world.update(ArenaWorld.STEP); }
}
