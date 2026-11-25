package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoverableChestTest {
    private static final int FIRST = ArenaWorld.MAX_ELITE_CHESTS;

    @Test void scheduledSlotsPlaceChestsWithoutChoicesCreditOrCountdowns() {
        ArenaWorld world = solo();
        int[] boundaries = {90, 600, 1140};
        for (int index = 0; index < boundaries.length; index++) {
            world.elapsed = boundaries[index] - 2 * ArenaWorld.STEP;
            world.update(ArenaWorld.STEP);
            assertFalse(world.chests[FIRST + index].active);
            world.update(ArenaWorld.STEP);
            for (int present = 0; present <= index; present++) {
                ArenaWorld.Chest chest = world.chests[FIRST + present];
                assertTrue(chest.active); assertEquals(boundaries[present], chest.scheduledSeconds);
                assertEquals("arena-chest", chest.source); assertEquals("CHEST DISCOVERED", chest.sourceLabel);
            }
            assertEquals(0, world.equipment.rewardsEarned()); assertEquals(0, world.equipment.choiceSecondsLeft());
            assertFalse(world.choicePending());
        }
        world.update(ArenaWorld.STEP);
        assertEquals(3, activeDiscoveries(world)); assertEquals(0, world.equipment.rewardsEarned());
        for (int i = 0; i < FIRST; i++) assertFalse(world.chests[i].active, "Discovery placement cannot occupy an elite slot");
    }

    @Test void shortTimelineKeepsProportionalPlacementWithoutFreeEquipment() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.SHORT); world.activatePlayer(0);
        world.elapsed = 13.5f; world.update(ArenaWorld.STEP);
        assertEquals(1, activeDiscoveries(world)); assertEquals(14, world.chests[FIRST].scheduledSeconds);
        world.elapsed = 90; world.update(ArenaWorld.STEP);
        assertEquals(2, activeDiscoveries(world));
        world.elapsed = 171; world.update(ArenaWorld.STEP);
        assertEquals(3, activeDiscoveries(world)); assertEquals(0, world.equipment.rewardsEarned());
        assertFalse(world.choicePending());
    }

    @Test void replayPlacementUsesBoundedInteriorClearancesAndVariesWithRunSeed() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        float previousX = Float.NaN, previousY = Float.NaN;
        for (int run = 0; run < 12; run++) {
            if (run > 0) { world.reset(); world.activatePlayer(0); world.activatePlayer(1); }
            world.elapsed = 1140; world.update(ArenaWorld.STEP);
            assertEquals(3, activeDiscoveries(world));
            for (int i = FIRST; i < world.chests.length; i++) {
                ArenaWorld.Chest chest = world.chests[i];
                assertTrue(Math.abs(chest.x) <= ArenaWorld.HALF_WIDTH - 160);
                assertTrue(Math.abs(chest.y) <= ArenaWorld.HALF_HEIGHT - 160);
                assertTrue(ArenaWorld.chestLandmarkClear(chest.x, chest.y));
                for (ArenaWorld.Player player : world.players)
                    assertTrue(Math.hypot(player.x - chest.x, player.y - chest.y) >= 320);
                for (int earlier = FIRST; earlier < i; earlier++)
                    assertTrue(Math.hypot(world.chests[earlier].x - chest.x, world.chests[earlier].y - chest.y) >= 140);
            }
            ArenaWorld.Chest first = world.chests[FIRST];
            if (run > 0) assertTrue(first.x != previousX || first.y != previousY);
            previousX = first.x; previousY = first.y;
            assertEquals(0, world.equipment.rewardsEarned());
        }
    }

    @Test void rejectedChestPlacementSamplesDoNotChangeTheNextCombatRandomEvents() {
        ArenaWorld reference = solo(); reference.elapsed = 90; reference.update(ArenaWorld.STEP);
        float rejectedX = reference.chests[FIRST].x, rejectedY = reference.chests[FIRST].y;
        ArenaWorld ordinary = solo(), displaced = solo();
        ordinary.debugSceneryView(0, 0);
        displaced.debugSceneryView(rejectedX, rejectedY); // Force at least one additional candidate.
        ordinary.elapsed = displaced.elapsed = 90;
        ordinary.update(ArenaWorld.STEP); displaced.update(ArenaWorld.STEP);
        assertTrue(ordinary.chests[FIRST].x != displaced.chests[FIRST].x
            || ordinary.chests[FIRST].y != displaced.chests[FIRST].y);
        // Return both runs to the same safe corner and inspect just their next authored pack.
        ordinary.debugSceneryView(-1180, -880); displaced.debugSceneryView(-1180, -880);
        for (int tick = 0; tick < 740 && ordinary.activeEnemies == 0; tick++) {
            ordinary.update(ArenaWorld.STEP); displaced.update(ArenaWorld.STEP);
        }
        assertTrue(ordinary.activeEnemies > 0); assertEquals(ordinary.activeEnemies, displaced.activeEnemies);
        for (int i = 0; i < ordinary.enemies.length; i++) {
            ArenaWorld.Enemy first = ordinary.enemies[i], second = displaced.enemies[i];
            assertEquals(first.active, second.active);
            if (!first.active) continue;
            assertEquals(first.kind, second.kind); assertEquals(first.variant, second.variant);
            assertEquals(first.x, second.x); assertEquals(first.y, second.y);
            assertEquals(first.health, second.health); assertEquals(first.animTime, second.animTime);
        }
    }

    @Test void aLaterJoiningPlayerCollectsForBothAndTheOriginalDiscoverySourceSurvivesReconnect() {
        ArenaWorld world = solo(); world.elapsed = 600; world.update(ArenaWorld.STEP);
        steps(world, 24);
        world.activatePlayer(1);
        assertEquals(0, world.equipment.foundationCount(1)); assertFalse(world.equipment.offers[1].pending);
        ArenaWorld.Chest chest = world.chests[FIRST];
        world.players[1].x = chest.x; world.players[1].y = chest.y;
        world.update(ArenaWorld.STEP);
        assertFalse(chest.active); assertTrue(world.chests[FIRST + 1].active);
        assertEquals(1, world.equipment.rewardsEarned());
        assertTrue(world.equipment.offers[0].pending); assertTrue(world.equipment.offers[1].pending);
        var origin = world.equipment.offers[1].origin;
        assertEquals("arena-chest", origin.source()); assertEquals("CHEST DISCOVERED", origin.label());
        assertEquals(90, origin.scheduledSeconds()); assertSame(origin, world.equipment.offers[0].origin);
        int id = world.equipment.offers[1].id;
        world.update(.1f); float seconds = world.equipment.choiceSecondsLeft();
        world.activatePlayer(1);
        assertEquals(id, world.equipment.offers[1].id); assertSame(origin, world.equipment.offers[1].origin);
        assertEquals(seconds, world.equipment.choiceSecondsLeft());
    }

    @Test void skillsAndEquipmentFreezeDiscoverySchedulingAndLanding() {
        ArenaWorld world = solo(); world.elapsed = 90 - ArenaWorld.STEP;
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(0, activeDiscoveries(world)); assertEquals(0, world.equipment.rewardsEarned());
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(ArenaWorld.STEP);
        ArenaWorld.Chest chest = world.chests[FIRST]; assertTrue(chest.active);
        float delay = chest.pickupDelay;
        world.equipment.grantReward(); world.update(.1f);
        assertEquals(delay, chest.pickupDelay);
        world.elapsed = 1140;
        world.equipment.updateChoices(.1f);
        assertEquals(1, activeDiscoveries(world), "Choice clocks cannot place scheduled world loot");
        assertEquals(1, world.equipment.rewardsEarned());
    }

    @Test void freshLateJoinInheritsOnlyCollectedGearAndResetReusesAllEightSlots() {
        ArenaWorld world = solo(); world.elapsed = 600; world.update(ArenaWorld.STEP); steps(world, 24);
        ArenaWorld.Chest chest = world.chests[FIRST];
        world.players[0].x = chest.x; world.players[0].y = chest.y; world.update(ArenaWorld.STEP);
        assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, 0));
        world.activatePlayer(1);
        assertEquals(1, world.equipment.foundationCount(1)); assertFalse(world.equipment.offers[1].pending);
        assertTrue(world.chests[FIRST + 1].active);
        ArenaWorld.Chest[] slots = world.chests.clone();
        world.reset(); world.activatePlayer(0);
        assertEquals(0, world.equipment.rewardsEarned());
        for (int i = 0; i < slots.length; i++) {
            assertSame(slots[i], world.chests[i]); assertFalse(slots[i].active); assertEquals(0, slots[i].pickupDelay);
        }
        world.elapsed = 1140; world.update(ArenaWorld.STEP);
        assertEquals(3, activeDiscoveries(world)); assertEquals(0, world.equipment.rewardsEarned());
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0);
        world.players[0].attackCooldown = 100; return world;
    }
    private static int activeDiscoveries(ArenaWorld world) {
        int count = 0; for (int i = FIRST; i < world.chests.length; i++) if (world.chests[i].active) count++; return count;
    }
    private static void steps(ArenaWorld world, int count) {
        for (int step = 0; step < count; step++) world.update(ArenaWorld.STEP);
    }
}
