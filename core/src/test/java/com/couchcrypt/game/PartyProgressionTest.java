package com.couchcrypt.game;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyProgressionTest {
    @Test void collectedSoulXpLevelsBothPlayersTogether() {
        ArenaWorld world = duo();
        ArenaWorld.Pickup reward = world.pickups[0];
        reward.active = true; reward.value = 6; reward.x = reward.y = 0;
        world.update(ArenaWorld.STEP);
        assertEquals(6, world.souls);
        assertEquals(6, world.progression.totalXp);
        assertEquals(2, world.progression.level);
        assertEquals(0, world.progression.xp);
        assertEquals(10, world.progression.xpToNext);
        assertTrue(world.progression.offers[0].pending && world.progression.offers[1].pending);
        assertEquals(2, world.progression.offers[0].level);
        assertEquals(2, world.progression.offers[1].level);
    }

    @Test void playersChooseIndependentlyAndOnlyConsumeTheirCurrentOfferOnce() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        int firstId = progression.offers[0].id, secondId = progression.offers[1].id;
        assertFalse(progression.choose(0, secondId, 0));
        assertFalse(progression.choose(0, firstId, -1));
        assertTrue(progression.choose(0, firstId, 0));
        assertEquals(1.25f, world.players[0].damageMultiplier);
        assertEquals(1f, world.players[1].damageMultiplier);
        assertFalse(progression.offers[0].pending);
        assertTrue(progression.offers[1].pending);
        assertFalse(progression.choose(0, firstId, 0));
        assertFalse(progression.choose(2, secondId, 0));
        assertEquals(1, progression.appliedUpgradeCount(0));
        assertEquals(1, progression.rank(0, PartyProgression.Upgrade.POWER));
        assertEquals(0, progression.rank(1, PartyProgression.Upgrade.POWER));
    }

    @Test void thirtySecondTimeoutAppliesOnlyFirstChoiceAndKeepsCombatRunning() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        int oldId = progression.offers[0].id;
        progression.update(29f);
        assertEquals(1f, progression.offers[0].secondsLeft, .001f);
        assertEquals(1f, world.players[0].damageMultiplier);
        progression.update(1f);
        assertFalse(progression.offers[0].pending);
        assertFalse(progression.offers[1].pending);
        assertEquals(1.25f, world.players[0].damageMultiplier);
        assertEquals(1.25f, world.players[1].damageMultiplier);
        assertFalse(progression.choose(0, oldId, 0));
        float elapsed = world.elapsed;
        world.update(ArenaWorld.STEP);
        assertTrue(world.elapsed > elapsed);
    }

    @Test void unreadOffersRetainTheirIdentityAndTimeWhileLaterLevelsQueue() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        progression.addXp(6); progression.update(5);
        int firstId = progression.offers[0].id, otherId = progression.offers[1].id;
        progression.addXp(24);
        assertEquals(4, progression.level);
        assertEquals(firstId, progression.offers[0].id);
        assertEquals(25f, progression.offers[0].secondsLeft);
        assertTrue(progression.choose(0, firstId, 0));
        assertEquals(3, progression.offers[0].level);
        assertEquals(30f, progression.offers[0].secondsLeft);
        assertNotEquals(firstId, progression.offers[0].id);
        assertEquals(otherId, progression.offers[1].id);
        progression.update(100f);
        assertEquals(4, progression.offers[0].level);
        assertEquals(3, progression.offers[1].level);
        assertEquals(30f, progression.offers[0].secondsLeft);
    }

    @Test void lateJoinReceivesCatchupAndExistingParticipantKeepsRanksOnReconnect() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        progression.addXp(16);
        assertTrue(progression.choose(0, progression.offers[0].id, 0));
        world.activatePlayer(1);
        assertEquals(3, progression.level);
        assertEquals(2, progression.offers[1].level);
        int secondId = progression.offers[1].id;
        world.activatePlayer(0);
        assertEquals(1, progression.rank(0, PartyProgression.Upgrade.POWER));
        world.deactivatePlayer(1);
        assertFalse(progression.choose(1, secondId, 0));
        world.activatePlayer(1);
        assertNotEquals(secondId, progression.offers[1].id);
        assertEquals(2, progression.offers[1].level);
        assertEquals(0, progression.appliedUpgradeCount(1));
    }

    @Test void resetClearsBuildsRejectsEarlierPacketsAndBoundsXpAndInvalidTime() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        int oldId = progression.offers[0].id;
        progression.choose(0, oldId, 0);
        world.reset(); world.activatePlayer(0);
        assertEquals(1f, world.players[0].damageMultiplier);
        assertEquals(0, progression.rank(0, PartyProgression.Upgrade.POWER));
        assertEquals("No upgrades", progression.buildSummary(0));
        progression.addXp(6);
        assertTrue(progression.offers[0].id > oldId);
        assertFalse(progression.choose(0, oldId, 0));
        progression.update(Float.NaN); progression.update(Float.POSITIVE_INFINITY); progression.update(-1);
        assertEquals(30f, progression.offers[0].secondsLeft);
        progression.addXp(-1);
        assertEquals(6, progression.totalXp);
        progression.addXp(Integer.MAX_VALUE); progression.addXp(Integer.MAX_VALUE);
        assertEquals(PartyProgression.MAX_LEVEL, progression.level);
        assertEquals(0, progression.xpToNext);
        assertEquals(Integer.MAX_VALUE, progression.totalXp);
    }

    @Test void classSpecificCardsAndAutomaticAbilitiesAppearWithoutWrongClassChoices() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        assertOffered(progression, 0, PartyProgression.Upgrade.SWEEP);
        assertOffered(progression, 1, PartyProgression.Upgrade.MULTISHOT);
        progression.addXp(24);
        for (int entitlement = 0; entitlement < 3; entitlement++) {
            for (int slot = 0; slot < 2; slot++) {
                PartyProgression.Offer offer = progression.offers[slot];
                Set<PartyProgression.Upgrade> distinct = new HashSet<>();
                for (PartyProgression.Upgrade option : offer.options) {
                    assertTrue(distinct.add(option));
                    if (slot == 0) {
                        assertNotEquals(PartyProgression.Upgrade.MULTISHOT, option);
                        assertNotEquals(PartyProgression.Upgrade.PIERCE, option);
                    } else assertNotEquals(PartyProgression.Upgrade.SWEEP, option);
                }
                if (offer.level == 3) assertOffered(progression, slot, PartyProgression.Upgrade.AURA);
                if (offer.level == 4) assertOffered(progression, slot, PartyProgression.Upgrade.STORM);
                assertTrue(progression.choose(slot, offer.id, 0));
            }
        }
        assertEquals(0, progression.rank(0, PartyProgression.Upgrade.AURA));
    }

    @Test void classFilteringUsesChosenHeroInsteadOfPhoneSlotAndReplaysVary() {
        ArenaWorld world = new ArenaWorld();
        world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        world.progression.addXp(6);
        assertOffered(world.progression, 0, PartyProgression.Upgrade.MULTISHOT);
        for (PartyProgression.Upgrade option : world.progression.offers[0].options)
            assertNotEquals(PartyProgression.Upgrade.SWEEP, option);
        Set<String> offers = new HashSet<>();
        for (int seed = 0; seed < 16; seed++) {
            ArenaWorld replay = solo(); replay.runSeed = seed; replay.progression.addXp(6);
            offers.add(replay.progression.offers[0].options[2].id);
        }
        assertTrue(offers.size() > 1);
    }

    @Test void chosenWeaponTraitsAbilitiesAndPickupTraitsApplyAndAppearInSummary() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        selectFixture(progression, 0, PartyProgression.Upgrade.SWEEP);
        selectFixture(progression, 1, PartyProgression.Upgrade.MULTISHOT);
        selectFixture(progression, 1, PartyProgression.Upgrade.PIERCE);
        selectFixture(progression, 0, PartyProgression.Upgrade.AURA);
        selectFixture(progression, 0, PartyProgression.Upgrade.STORM);
        selectFixture(progression, 0, PartyProgression.Upgrade.MAGNET);
        selectFixture(progression, 0, PartyProgression.Upgrade.CRITICAL);
        assertEquals(1.2f, world.players[0].cleaveMultiplier, .0001f);
        assertEquals(2, world.players[1].projectileCount);
        assertEquals(1, world.players[1].pierce);
        assertEquals(1, world.players[0].auraLevel);
        assertEquals(1, world.players[0].stormLevel);
        assertEquals(1.3f, world.players[0].pickupMultiplier, .0001f);
        assertEquals(.1f, world.players[0].critChance, .0001f);
        String build = progression.buildSummary(0);
        assertTrue(build.contains("Wide Sweep") && build.contains("Soul Circle") && build.contains("Chain Storm"));
        assertFalse(build.contains("Split Shot"));
        assertTrue(build.lines().count() <= 2);
        world.deactivatePlayer(0);
        assertEquals("No upgrades", progression.buildSummary(0));
        assertEquals(1, progression.rank(1, PartyProgression.Upgrade.MULTISHOT));
    }

    @Test void cappedAndExternallyInvalidatedCardsNeverApplyOrConsumeTheSameIdentity() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        world.players[0].damageMultiplier = 4f;
        world.players[0].moveMultiplier = 2.2f;
        world.players[0].auraLevel = 3;
        progression.addXp(6);
        for (PartyProgression.Upgrade option : progression.offers[0].options) {
            assertNotEquals(PartyProgression.Upgrade.POWER, option);
            assertNotEquals(PartyProgression.Upgrade.FLEET, option);
            assertNotEquals(PartyProgression.Upgrade.AURA, option);
        }
        int oldId = progression.offers[0].id;
        PartyProgression.Offer offer = progression.offers[0];
        offer.options[0] = PartyProgression.Upgrade.POWER;
        assertFalse(progression.choose(0, oldId, 0));
        assertNotEquals(oldId, offer.id);
        assertEquals(0, progression.appliedUpgradeCount(0));
        assertFalse(progression.choose(0, oldId, 0));
        world.players[0].alive = false; world.players[0].health = 0;
        selectFixture(progression, 0, PartyProgression.Upgrade.VITALITY);
        assertFalse(world.players[0].alive);
        assertEquals(0, world.players[0].health);
        assertEquals(120f, world.players[0].maxHealth);
    }

    @Test void baseStatTraitsChangeActualMovementArrowSnapshotAndCooldowns() {
        ArenaWorld world = new ArenaWorld();
        world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        PartyProgression progression = world.progression;
        selectFixture(progression, 0, PartyProgression.Upgrade.POWER);
        ArenaWorld.Enemy enemy = world.enemies[0];
        enemy.active = true; enemy.x = 300; enemy.y = 0; enemy.health = enemy.maxHealth = 100;
        world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertTrue(world.bolts[0].active);
        float damage = world.bolts[0].damage;
        assertTrue(damage > 16f);
        world.players[0].damageMultiplier = 2f;
        assertEquals(damage, world.bolts[0].damage);
        selectFixture(progression, 0, PartyProgression.Upgrade.HASTE);
        selectFixture(progression, 0, PartyProgression.Upgrade.FLEET);
        selectFixture(progression, 0, PartyProgression.Upgrade.EVASION);
        assertEquals(1.15f, world.players[0].attackRateMultiplier, .0001f);
        float before = world.players[0].x;
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertTrue(world.players[0].x - before > 198f * ArenaWorld.STEP);
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertEquals(2.4f * .85f, world.players[0].dodgeCooldown, .001f);
    }

    private static void selectFixture(PartyProgression progression, int slot, PartyProgression.Upgrade upgrade) {
        if (!progression.offers[slot].pending) progression.addXp(progression.xpToNext - progression.xp);
        PartyProgression.Offer offer = progression.offers[slot];
        offer.options[0] = upgrade;
        assertTrue(progression.choose(slot, offer.id, 0), "Fixture upgrade " + upgrade);
    }
    private static void assertOffered(PartyProgression progression, int slot, PartyProgression.Upgrade upgrade) {
        for (PartyProgression.Upgrade option : progression.offers[slot].options) if (option == upgrade) return;
        fail("Expected offered upgrade " + upgrade);
    }
    private static ArenaWorld solo() { ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world; }
    private static ArenaWorld duo() { ArenaWorld world = solo(); world.activatePlayer(1); return world; }
}
