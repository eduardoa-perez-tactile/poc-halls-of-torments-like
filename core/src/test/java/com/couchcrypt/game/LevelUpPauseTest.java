package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Short direct fixtures: choosing time advances, combat time does not. */
class LevelUpPauseTest {
    @Test void skillChoicesFreezeCombatObjectsHealthCooldownsRevivalAndBothGearClocks() {
        ArenaWorld world = solo();
        for (int i = 0; i < 4; i++) world.equipment.grantReward();
        world.activatePlayer(1); // The automatic pendant grants real regeneration.
        world.progression.addXp(world.progression.xpToNext);
        var first = world.players[0]; var second = world.players[1];
        first.alive = false; first.health = 0; first.reviveProgress = .8f;
        second.x = 50; second.health = 80; second.dodgeCooldown = 1.5f;
        second.attackCooldown = 2; second.auraCooldown = 1; second.stormCooldown = 3;
        assertTrue(world.equipment.regeneration(second) > 0);
        var enemy = world.enemies[0];
        enemy.active = true; enemy.x = 300; enemy.y = 20; enemy.health = 100;
        enemy.attackCooldown = .5f; enemy.stunTime = .6f; world.activeEnemies = 1;
        var bolt = world.bolts[0]; bolt.active = true; bolt.x = 200; bolt.vx = 100; bolt.ttl = 2;
        var shot = world.shots[0]; shot.active = true; shot.x = 240; shot.vx = 100; shot.ttl = 3;
        var hazard = world.hazards[0]; hazard.active = true; hazard.warning = .6f; hazard.ttl = 2;
        var effect = world.effects[0]; effect.active = true; effect.ttl = .8f;
        var pickup = world.pickups[0]; pickup.active = true; pickup.x = 50; pickup.value = 2;
        world.equipment.grantReward();
        float firstGearTime = world.equipment.offers[0].secondsLeft;
        float secondGearTime = world.equipment.offers[1].secondsLeft;
        world.setInput(1, 1, 0, true);
        world.update(.1f);
        assertTrue(world.levelUpPending());
        assertEquals(0, world.elapsed); assertEquals(1, world.activeEnemies); assertEquals(0, world.souls);
        assertEquals(50, second.x); assertEquals(80, second.health);
        assertEquals(1.5f, second.dodgeCooldown); assertEquals(0, second.dodgeTime);
        assertEquals(2, second.attackCooldown); assertEquals(1, second.auraCooldown); assertEquals(3, second.stormCooldown);
        assertEquals(.8f, first.reviveProgress); assertFalse(first.alive);
        assertEquals(300, enemy.x); assertEquals(20, enemy.y);
        assertEquals(.5f, enemy.attackCooldown); assertEquals(.6f, enemy.stunTime);
        assertEquals(200, bolt.x); assertEquals(2, bolt.ttl);
        assertEquals(240, shot.x); assertEquals(3, shot.ttl);
        assertEquals(.6f, hazard.warning); assertEquals(2, hazard.ttl); assertEquals(.8f, effect.ttl);
        assertTrue(pickup.active);
        assertEquals(firstGearTime, world.equipment.offers[0].secondsLeft);
        assertEquals(secondGearTime, world.equipment.offers[1].secondsLeft);
        assertEquals(29.9f, world.progression.offers[0].secondsLeft, .0001f);
        assertEquals(29.9f, world.progression.offers[1].secondsLeft, .0001f);
    }

    @Test void partyWaitsForLastPlayerThenResumesWithoutReplayingMovementOrDodge() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        world.progression.addXp(world.progression.xpToNext);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        assertTrue(world.levelUpPending());
        world.setInput(0, 1, 0, true);
        world.update(ArenaWorld.STEP);
        assertEquals(0, world.elapsed); assertEquals(0, world.players[0].x);
        assertTrue(world.progression.choose(1, world.progression.offers[1].id, 0));
        assertFalse(world.levelUpPending());
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.STEP, world.elapsed, .00001f);
        assertEquals(0, world.players[0].x); assertEquals(0, world.players[0].dodgeCooldown);
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertTrue(world.players[0].x > 0);
    }

    @Test void timeoutAppliesFirstOptionAndQueuedLevelKeepsCombatFrozenWithFreshTimer() {
        ArenaWorld world = solo(); world.progression.addXp(16); // Two earned levels in the short fixture.
        var first = world.progression.offers[0];
        int firstId = first.id; var defaultUpgrade = first.options[0];
        first.secondsLeft = ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.progression.rank(0, defaultUpgrade));
        assertEquals(1, world.progression.appliedUpgradeCount(0));
        assertTrue(world.levelUpPending()); assertTrue(first.id > firstId);
        assertEquals(30, first.secondsLeft); assertEquals(0, world.elapsed);
        first.secondsLeft = ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertEquals(2, world.progression.appliedUpgradeCount(0));
        assertFalse(world.levelUpPending()); assertEquals(0, world.elapsed);
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.STEP, world.elapsed, .00001f);
    }

    @Test void gearOnlyChoicesKeepExistingCombatAndTimerBehavior() {
        ArenaWorld world = solo(); world.equipment.grantReward();
        assertFalse(world.levelUpPending());
        world.update(ArenaWorld.STEP);
        assertTrue(world.elapsed > 0); assertTrue(world.equipment.offers[0].pending);
        assertEquals(30 - ArenaWorld.STEP, world.equipment.offers[0].secondsLeft, .00001f);
    }

    @Test void pendingLevelUpDefersFinalBossBoundaryWithoutBankingCombatTime() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0);
        world.elapsed = world.durationSeconds() - ArenaWorld.STEP;
        float before = world.elapsed;
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(before, world.elapsed); assertFalse(world.bossSpawned);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(ArenaWorld.STEP);
        assertEquals(before + ArenaWorld.STEP, world.elapsed, .0001f);
        assertTrue(world.bossSpawned);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world;
    }
}
