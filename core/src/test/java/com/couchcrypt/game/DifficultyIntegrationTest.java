package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyIntegrationTest {
    @Test void reconnectPreservesHealingTelemetryAndHostRemovalClearsItForAFreshPlayer() {
        ArenaWorld world = quiet(20);
        var player = world.players[0];
        player.healingFromDrops = 12; player.healingFromRegeneration = 3; player.healingFromSustain = 5;
        world.activatePlayer(0);
        assertEquals(12, player.healingFromDrops); assertEquals(3, player.healingFromRegeneration);
        assertEquals(5, player.healingFromSustain);
        world.deactivatePlayer(0); world.activatePlayer(0);
        assertEquals(0, player.healingFromDrops); assertEquals(0, player.healingFromRegeneration);
        assertEquals(0, player.healingFromSustain);
    }

    @Test void realKillsGenerateTheNewOpeningAndLateHealingAmounts() {
        for (boolean late : new boolean[]{false, true}) {
            ArenaWorld world = quiet(late ? 181 : 20);
            int count = late ? 20 : 14;
            for (int i = 0; i < count; i++) {
                var enemy = world.enemies[0];
                enemy.active = true; enemy.kind = ArenaWorld.EnemyKind.GHOUL;
                enemy.elite = false; enemy.health = 1; enemy.x = 30; enemy.y = 0;
                world.activeEnemies = 1; world.players[0].attackCooldown = 0;
                world.update(ArenaWorld.STEP); finishChoices(world);
            }
            assertEquals(count, world.kills);
            assertEquals(1, world.healthDrops.generated);
            for (var pickup : world.pickups) if (pickup.active && pickup.healing) {
                assertEquals(late ? 12 : 16, pickup.value);
                assertTrue(pickup.ttl > 34 && pickup.ttl <= 35);
            }
            assertEquals(0, world.healsCollected, "Full-health players leave a bounded temporary pickup");
        }
    }

    @Test void combatChoicesAndReconnectFreezeHealingAgeAndCollectionCountsEffectiveRecovery() {
        ArenaWorld world = quiet(20);
        for (int i = 0; i < 14; i++) world.healthDrops.defeated(world.pickups, false, 20, 1, 500, 0);
        var pickup = world.pickups[0];
        float ttl = pickup.ttl, elapsed = world.elapsed;
        world.equipment.grantReward(); world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(ttl, pickup.ttl); assertEquals(elapsed, world.elapsed);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        assertTrue(world.lootPending()); world.update(.1f);
        assertEquals(ttl, pickup.ttl); assertEquals(elapsed, world.elapsed);
        world.activatePlayer(0); assertEquals(ttl, pickup.ttl);
        finishChoices(world); world.update(.1f);
        assertEquals(ttl - .1f, pickup.ttl, .001f);
        world.update(0); assertEquals(ttl - .1f, pickup.ttl, .001f);
        world.players[0].health = 95; pickup.x = pickup.y = 0;
        world.update(ArenaWorld.STEP);
        assertFalse(pickup.active); assertEquals(100, world.players[0].health);
        assertEquals(5, world.players[0].healingFromDrops);
        assertEquals(16, world.healthDrops.generatedValue);
    }

    @Test void actualXpDropsReclaimHealingThenConserveAllXpWhenThePoolIsFull() {
        ArenaWorld world = quiet(20);
        for (var pickup : world.pickups) {
            pickup.active = true; pickup.healing = false; pickup.value = 1;
            pickup.x = -1000; pickup.y = -800;
        }
        var heal = world.pickups[5]; heal.healing = true; heal.value = 16;
        heal.ttl = 20; heal.maxTtl = 35;
        killOne(world);
        assertFalse(heal.healing); assertEquals(1, heal.value);
        assertEquals(96, xpOnFloor(world) + world.souls);
        killOne(world);
        assertEquals(97, xpOnFloor(world) + world.souls);
    }

    @Test void fullRunPacksApproachFromDifferentDirectionsAndRespectBothPlayersSafety() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0);
        world.players[0].attackCooldown = 1000; world.elapsed = 100;
        world.activatePlayer(1); world.players[1].attackCooldown = 1000;
        for (int tick = 0; tick < 180 && world.activeEnemies == 0; tick++) {
            finishChoices(world); world.update(ArenaWorld.STEP);
        }
        assertTrue(world.activeEnemies >= 3);
        boolean opposed = false;
        for (var a : world.enemies) if (a.active) {
            for (var player : world.players)
                assertTrue(Math.hypot(a.x - player.x, a.y - player.y) >= 300);
            for (var b : world.enemies) if (b.active && a != b)
                opposed |= a.x * b.x + a.y * b.y < 0;
        }
        assertTrue(opposed, "A pack must create simultaneous approaches rather than one trailing clump");
    }

    private static ArenaWorld quiet(float elapsed) {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0);
        world.players[0].attackCooldown = 1000; world.players[0].invulnerable = 1000;
        world.elapsed = elapsed; world.update(ArenaWorld.STEP); finishChoices(world);
        world.debugSceneryView(0, 0);
        return world;
    }
    private static void killOne(ArenaWorld world) {
        var enemy = world.enemies[0]; enemy.active = true; enemy.health = 1;
        enemy.kind = ArenaWorld.EnemyKind.GHOUL; enemy.elite = false;
        enemy.x = 30; enemy.y = 0; world.activeEnemies = 1;
        world.players[0].attackCooldown = 0; world.update(ArenaWorld.STEP); finishChoices(world);
    }
    private static int xpOnFloor(ArenaWorld world) {
        int xp = 0; for (var pickup : world.pickups) if (pickup.active && !pickup.healing) xp += pickup.value; return xp;
    }
    private static void finishChoices(ArenaWorld world) {
        for (int round = 0; round < 32 && world.choicePending(); round++) for (int slot = 0; slot < 2; slot++) {
            if (world.progression.offers[slot].pending)
                assertTrue(world.progression.choose(slot, world.progression.offers[slot].id, 0));
            if (!world.levelUpPending() && world.equipment.offers[slot].pending)
                assertTrue(world.equipment.choose(slot, world.equipment.offers[slot].id, -1));
        }
        assertFalse(world.choicePending());
    }
}
