package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyProgressionTest {
    @Test void oneCollectedSoulRewardLevelsBothPlayersTogether() {
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

    @Test void playersPickIndependentlyAndOnlyCurrentOfferCanBeConsumed() {
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
    }

    @Test void timeoutSelectsFirstOptionWithoutPausingCombat() {
        ArenaWorld world = duo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        int expiredId = progression.offers[0].id;
        progression.update(29f);
        assertEquals(1f, progression.offers[0].secondsLeft, .001f);
        assertEquals(1f, world.players[0].damageMultiplier);
        progression.update(1f);
        assertFalse(progression.offers[0].pending);
        assertFalse(progression.offers[1].pending);
        assertEquals(1.25f, world.players[0].damageMultiplier);
        assertEquals(1.15f, world.players[1].attackRateMultiplier);
        assertFalse(progression.choose(0, expiredId, 0));
        float before = world.elapsed;
        world.update(ArenaWorld.STEP);
        assertTrue(world.elapsed > before);
    }

    @Test void queuedLevelsNeverReplaceAnUnreadOfferOrResetItsTimer() {
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

    @Test void lateJoinCatchesUpAndReconnectKeepsExistingBuild() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        progression.addXp(16);
        int firstId = progression.offers[0].id;
        assertTrue(progression.choose(0, firstId, 0));
        world.activatePlayer(1);
        assertEquals(3, progression.level);
        assertTrue(progression.offers[1].pending);
        assertEquals(2, progression.offers[1].level);
        int secondId = progression.offers[1].id;
        world.activatePlayer(0);
        assertEquals(1.25f, world.players[0].damageMultiplier);
        world.deactivatePlayer(1);
        assertFalse(progression.choose(1, secondId, 0));
        world.activatePlayer(1);
        assertNotEquals(secondId, progression.offers[1].id);
        assertEquals(2, progression.offers[1].level);
    }

    @Test void resetRejectsPacketsFromAnEarlierRunAndBoundsBadXpAndTime() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        int oldId = progression.offers[0].id;
        progression.choose(0, oldId, 0);
        world.reset(); world.activatePlayer(0);
        assertEquals(1f, world.players[0].damageMultiplier);
        progression.addXp(6);
        assertTrue(progression.offers[0].id > oldId);
        assertFalse(progression.choose(0, oldId, 0));
        progression.update(Float.NaN); progression.update(Float.POSITIVE_INFINITY); progression.update(-1);
        assertEquals(30f, progression.offers[0].secondsLeft);
        progression.addXp(-1);
        assertEquals(6, progression.totalXp);
        progression.addXp(Integer.MAX_VALUE);
        progression.addXp(Integer.MAX_VALUE);
        assertEquals(PartyProgression.MAX_LEVEL, progression.level);
        assertEquals(0, progression.xpToNext);
        assertEquals(Integer.MAX_VALUE, progression.totalXp);
    }

    @Test void upgradesChangeActualMovementProjectileDamageAndCooldowns() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        progression.addXp(6);
        pick(progression, 0, PartyProgression.Upgrade.POWER);
        ArenaWorld.Enemy enemy = world.enemies[0];
        enemy.active = true; enemy.x = 100; enemy.y = 0; enemy.health = enemy.maxHealth = 100;
        world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertTrue(world.bolts[0].active);
        assertEquals(15f, world.bolts[0].damage);
        float damageBefore = world.bolts[0].damage;
        world.players[0].damageMultiplier = 2f;
        assertEquals(damageBefore, world.bolts[0].damage);
        progression.addXp(10);
        pick(progression, 0, PartyProgression.Upgrade.VITALITY);
        assertEquals(120f, world.players[0].maxHealth);
        assertEquals(120f, world.players[0].health);
        progression.addXp(14);
        pick(progression, 0, PartyProgression.Upgrade.EVASION);
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertEquals(2.4f * .85f, world.players[0].dodgeCooldown, .001f);
        world.players[0].dodgeTime = 0;
        progression.addXp(18);
        // The fourth offer rotates through Vitality, Evasion, then Power.
        pick(progression, 0, PartyProgression.Upgrade.POWER);
        progression.addXp(22);
        // The fifth offer includes Evasion, Power and Haste.
        pick(progression, 0, PartyProgression.Upgrade.HASTE);
        assertEquals(1.15f, world.players[0].attackRateMultiplier);
        progression.addXp(26);
        pick(progression, 0, PartyProgression.Upgrade.FLEET);
        float x = world.players[0].x;
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertEquals(3f * 1.12f, world.players[0].x - x, .001f);
    }

    @Test void cappedUpgradesAreNotOfferedAgainAndDownedVitalityDoesNotRevive() {
        ArenaWorld world = solo();
        PartyProgression progression = world.progression;
        world.players[0].damageMultiplier = 4f;
        world.players[0].moveMultiplier = 2.2f;
        progression.addXp(6);
        for (PartyProgression.Upgrade option : progression.offers[0].options) {
            assertNotEquals(PartyProgression.Upgrade.POWER, option);
            assertNotEquals(PartyProgression.Upgrade.FLEET, option);
        }
        world.players[0].alive = false; world.players[0].health = 0;
        pick(progression, 0, PartyProgression.Upgrade.VITALITY);
        assertFalse(world.players[0].alive);
        assertEquals(0, world.players[0].health);
        assertEquals(120f, world.players[0].maxHealth);
        world.deactivatePlayer(0);
        assertEquals(100f, world.players[0].maxHealth);
        assertEquals(1f, world.players[0].damageMultiplier);
    }

    private static void pick(PartyProgression progression, int slot, PartyProgression.Upgrade upgrade) {
        PartyProgression.Offer offer = progression.offers[slot];
        for (int i = 0; i < offer.options.length; i++) if (offer.options[i] == upgrade) {
            assertTrue(progression.choose(slot, offer.id, i)); return;
        }
        fail("Expected offered upgrade " + upgrade);
    }
    private static ArenaWorld solo() { ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world; }
    private static ArenaWorld duo() { ArenaWorld world = solo(); world.activatePlayer(1); return world; }
}
