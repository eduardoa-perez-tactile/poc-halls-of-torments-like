package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageNumbersTest {
    @Test void lethalCriticalDisplaysActualHealthRemovedAndRetainsSizeAndPositionAfterEnemyReuse() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy enemy = enemy(world, 80, 25);
        enemy.kind = ArenaWorld.EnemyKind.BRUTE; enemy.variant = EnemyVariant.MOSS; enemy.elite = true;
        enemy.stunTime = 10;
        float scale = EnemySizing.renderScale(enemy.kind, enemy.variant, enemy.elite);
        world.players[0].attackCooldown = 10;
        ArenaWorld.Bolt bolt = world.bolts[0];
        bolt.active = true; bolt.x = 70; bolt.vx = 760; bolt.ttl = 1;
        bolt.damage = 200; bolt.critical = true;
        world.update(ArenaWorld.STEP);
        assertFalse(enemy.active);
        DamageNumbers.Entry label = first(world);
        assertEquals(25, label.actual); assertEquals(25, value(label)); assertTrue(label.critical);
        assertEquals(ArenaWorld.EnemyKind.BRUTE, label.enemyKind);
        assertEquals(scale, label.renderScale); assertEquals(38, label.liftPadding);
        assertEquals(80, label.x); assertEquals(0, label.y);
        enemy.kind = ArenaWorld.EnemyKind.BOSS; enemy.variant = EnemyVariant.ORIGINAL; enemy.elite = false;
        enemy.x = 900; enemy.y = 600;
        assertEquals(ArenaWorld.EnemyKind.BRUTE, label.enemyKind);
        assertEquals(scale, label.renderScale); assertEquals(80, label.x); assertEquals(0, label.y);
    }

    @Test void choiceFreezeRetainsLabelsUntilCombatResumesAndResetClearsThem() {
        ArenaWorld world = solo(); enemy(world, 80, 1000);
        advanceToSwordHit(world);
        DamageNumbers.Entry label = first(world);
        assertEquals(28, value(label)); assertFalse(label.critical);
        world.players[0].attackCooldown = 10;
        world.update(.1f);
        float ttl = label.ttl, rise = label.rise(), alpha = label.alpha();
        world.progression.addXp(world.progression.xpToNext); world.update(.1f);
        assertEquals(ttl, label.ttl); assertEquals(rise, label.rise()); assertEquals(alpha, label.alpha());
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        for (int i = 0; i < 7; i++) world.update(.1f);
        assertEquals(0, active(world));
        world.damageNumbers.record(world.enemies[0], 12, false);
        assertTrue(active(world) > 0);
        world.reset(); assertEquals(0, active(world));
    }

    @Test void fullIndependentNumberAndEffectPoolsDoNotChangeDamageOrSuppressTheNewestLabel() {
        ArenaWorld world = solo(); ArenaWorld.Enemy enemy = enemy(world, 80, 1000);
        for (int i = 0; i < DamageNumbers.CAPACITY; i++) world.damageNumbers.record(enemy, 9, true);
        for (ArenaWorld.Effect effect : world.effects) {
            effect.active = true; effect.kind = ArenaWorld.EffectKind.AURA; effect.ttl = 10;
        }
        advanceToSwordHit(world);
        assertEquals(972, enemy.health); assertEquals(28, world.players[0].damageDealt);
        assertEquals(1, world.swordHits); assertEquals(DamageNumbers.CAPACITY, active(world));
        DamageNumbers.Entry newest = world.damageNumbers.entries[0];
        assertEquals(28, value(newest)); assertFalse(newest.critical);
        assertEquals(ArenaWorld.MAX_EFFECTS, world.effects.length);
        assertEquals(DamageNumbers.LIFETIME, newest.ttl);
    }

    @Test void playerDamageDoesNotCreateEnemyDamageLabels() {
        ArenaWorld world = solo(); world.players[0].attackCooldown = 10; world.players[0].invulnerable = 0;
        enemy(world, 0, 1000);
        world.update(ArenaWorld.STEP);
        assertTrue(world.players[0].health < world.players[0].maxHealth);
        assertEquals(0, active(world));
    }

    private static int active(ArenaWorld world) {
        int count = 0;
        for (DamageNumbers.Entry entry : world.damageNumbers.entries) if (entry.ttl > 0) count++;
        return count;
    }

    private static void advanceToSwordHit(ArenaWorld world) {
        for (int i = 0; i < 15 && world.swordHits == 0; i++) world.update(ArenaWorld.STEP);
        assertEquals(1, world.swordHits);
    }

    private static int value(DamageNumbers.Entry entry) {
        int value = 0;
        for (int i = entry.digitCount - 1; i >= 0; i--) value = value * 10 + entry.digits[i];
        return value;
    }

    private static DamageNumbers.Entry first(ArenaWorld world) {
        for (DamageNumbers.Entry entry : world.damageNumbers.entries) if (entry.ttl > 0) return entry;
        throw new AssertionError("No damage label");
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world;
    }

    private static ArenaWorld.Enemy enemy(ArenaWorld world, float x, float health) {
        ArenaWorld.Enemy enemy = world.enemies[0];
        enemy.active = true; enemy.x = x; enemy.health = enemy.maxHealth = health;
        enemy.attackCooldown = 10; world.activeEnemies = 1;
        return enemy;
    }
}
