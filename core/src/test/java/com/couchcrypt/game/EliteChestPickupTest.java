package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Short combat fixtures: elite gear is earned on contact, separately from XP/healing. */
class EliteChestPickupTest {
    @Test void eliteDropsStationaryChestAndSoulMagnetDoesNotOpenIt() {
        ArenaWorld world = solo();
        world.players[0].pickupMultiplier = 20;
        defeatElite(world, 120);
        ArenaWorld.Chest chest = world.chests[0];
        assertTrue(chest.active); assertEquals(120, chest.x); assertEquals(0, chest.y);
        assertEquals(1, world.elitesKilled); assertEquals(0, world.equipment.rewardsEarned());
        assertFalse(world.choicePending());
        steps(world, 24);
        assertTrue(chest.active); assertEquals(120, chest.x); assertEquals(0, chest.y);
        assertEquals(0, world.equipment.rewardsEarned()); assertFalse(world.lootPending());
        world.players[0].x = 120 - ArenaWorld.CHEST_PICKUP_RADIUS - 1;
        world.update(ArenaWorld.STEP); assertTrue(chest.active);
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertFalse(chest.active); assertTrue(world.lootPending());
        assertEquals("elite", world.equipment.choiceOrigin().source());
        assertEquals("ELITE DEFEATED", world.equipment.choiceOrigin().label());
    }

    @Test void landingIsVisibleBeforeContactAndEitherPlayerOpensOnePersonalOfferEach() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        world.players[1].x = 30;
        defeatElite(world, 30);
        assertTrue(world.chests[0].active); assertFalse(world.lootPending());
        assertTrue(world.chests[0].pickupDelay > 0);
        world.players[0].x = -300; // Player 2 alone collects the shared chest.
        steps(world, 24);
        assertFalse(world.chests[0].active); assertEquals(1, world.equipment.rewardsEarned());
        assertTrue(world.equipment.offers[0].pending); assertTrue(world.equipment.offers[1].pending);
        assertSame(world.equipment.offers[0].origin, world.equipment.offers[1].origin);
        assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, -1));
        assertTrue(world.equipment.choose(1, world.equipment.offers[1].id, -1));
        steps(world, 3);
        assertEquals(1, world.equipment.rewardsEarned()); assertFalse(world.lootPending());
    }

    @Test void uncollectedChestSurvivesFullOrdinaryPickupPoolAndLateJoinDoesNotInheritIt() {
        ArenaWorld world = solo();
        for (ArenaWorld.Pickup pickup : world.pickups) {
            pickup.active = true; pickup.value = 1; pickup.x = -1100; pickup.y = -800;
        }
        defeatElite(world, 120);
        steps(world, 24);
        assertTrue(world.chests[0].active); assertEquals(0, world.equipment.rewardsEarned());
        int soulRewards = 0;
        for (ArenaWorld.Pickup pickup : world.pickups) if (pickup.active && !pickup.healing) soulRewards += pickup.value;
        assertEquals(ArenaWorld.MAX_PICKUPS + 8, soulRewards, "Chest storage cannot consume XP storage");
        world.activatePlayer(1);
        assertEquals(0, world.equipment.foundationCount(1)); assertFalse(world.equipment.offers[1].pending);
        world.players[1].x = 120; world.update(ArenaWorld.STEP);
        assertTrue(world.equipment.offers[0].pending); assertTrue(world.equipment.offers[1].pending);
        assertEquals(1, world.equipment.rewardsEarned());
    }

    @Test void collectedRewardStillCatchesUpFreshPlayersAndReconnectPreservesPendingIdentity() {
        ArenaWorld world = solo(); defeatElite(world, 120); steps(world, 24);
        world.players[0].x = 120; world.update(ArenaWorld.STEP);
        int offerId = world.equipment.offers[0].id;
        world.update(.1f); float remaining = world.equipment.choiceSecondsLeft();
        world.activatePlayer(0);
        assertEquals(offerId, world.equipment.offers[0].id);
        assertEquals(remaining, world.equipment.choiceSecondsLeft());
        world.activatePlayer(1);
        assertEquals(1, world.equipment.foundationCount(1)); assertEquals(1, world.equipment.appliedCount(1));
        assertFalse(world.equipment.offers[1].pending);
        assertEquals(offerId, world.equipment.offers[0].id);
    }

    @Test void skillsAndLootFreezeChestLandingAndStackedDropsOpenSequentially() {
        ArenaWorld world = solo();
        defeatElite(world, 120); defeatElite(world, 120);
        world.progression.addXp(world.progression.xpToNext);
        float delay = world.chests[0].pickupDelay;
        world.players[0].x = 120;
        world.update(.1f);
        assertEquals(delay, world.chests[0].pickupDelay); assertEquals(0, world.equipment.rewardsEarned());
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        // Remove unrelated XP drops so this fixture isolates the chest/choice sequence.
        for (ArenaWorld.Pickup pickup : world.pickups) pickup.active = false;
        steps(world, 24);
        assertEquals(1, world.equipment.rewardsEarned()); assertTrue(world.lootPending());
        assertTrue(world.chests[1].active);
        float secondDelay = world.chests[1].pickupDelay;
        world.update(.1f);
        assertEquals(secondDelay, world.chests[1].pickupDelay);
        assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, -1));
        steps(world, 3);
        assertEquals(2, world.equipment.rewardsEarned()); assertTrue(world.lootPending());
        assertFalse(world.chests[1].active);
    }

    @Test void downedAndInactivePlayersCannotCollectAndResetReusesChestPool() {
        ArenaWorld world = solo(); defeatElite(world, 120); world.activatePlayer(1);
        world.players[0].alive = false; world.players[0].health = 0; world.players[0].x = 120;
        world.players[1].x = -300;
        steps(world, 24);
        assertTrue(world.chests[0].active); assertFalse(world.lootPending());
        world.deactivatePlayer(0); world.players[0].x = 120;
        world.update(ArenaWorld.STEP);
        assertTrue(world.chests[0].active); assertEquals(0, world.equipment.rewardsEarned());
        ArenaWorld.Chest pooled = world.chests[0];
        world.reset();
        assertSame(pooled, world.chests[0]); assertFalse(pooled.active); assertEquals(0, pooled.pickupDelay);
        world.activatePlayer(0); defeatElite(world, 120);
        assertSame(pooled, world.chests[0]); assertTrue(pooled.active);
    }

    @Test void eliteChestOpportunitiesStayBoundedAndDoNotCompeteWithDiscoveries() {
        ArenaWorld world = solo();
        for (int i = 0; i < ArenaWorld.MAX_ELITE_CHESTS + 1; i++) {
            defeatElite(world, 120);
            for (ArenaWorld.Pickup pickup : world.pickups) pickup.active = false;
        }
        int active = 0; for (ArenaWorld.Chest chest : world.chests) if (chest.active) active++;
        assertEquals(5, active); assertEquals(0, world.equipment.rewardsEarned());
        steps(world, 24); world.players[0].x = 120;
        for (int i = 0; i < 5; i++) {
            world.update(ArenaWorld.STEP);
            assertEquals(i + 1, world.equipment.rewardsEarned());
            assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, -1));
        }
        world.elapsed = 1140; world.update(ArenaWorld.STEP);
        assertEquals(5, world.equipment.rewardsEarned()); assertFalse(world.lootPending());
        for (int i = ArenaWorld.MAX_ELITE_CHESTS; i < ArenaWorld.MAX_CHESTS; i++)
            assertTrue(world.chests[i].active, "Each discovery retains space after all five elite drops");
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0); return world;
    }

    private static void defeatElite(ArenaWorld world, float x) {
        ArenaWorld.Enemy elite = world.enemies[0];
        elite.active = elite.elite = true; elite.x = x; elite.y = 0;
        elite.health = elite.maxHealth = 1; elite.kind = ArenaWorld.EnemyKind.BRUTE;
        elite.spawnId++; elite.speedMultiplier = 0; elite.stunTime = 0;
        elite.attack = ArenaWorld.EnemyAttack.NONE; elite.windup = 0; elite.attackCooldown = 10;
        world.activeEnemies = 1; world.players[0].attackCooldown = 0;
        // Each manually reused slot is a fresh elite; wait for an actual wave contact.
        for (int tick = 0; tick < 30 && elite.active; tick++) world.update(ArenaWorld.STEP);
        assertFalse(elite.active);
    }

    private static void steps(ArenaWorld world, int count) {
        for (int i = 0; i < count; i++) world.update(ArenaWorld.STEP);
    }
}
