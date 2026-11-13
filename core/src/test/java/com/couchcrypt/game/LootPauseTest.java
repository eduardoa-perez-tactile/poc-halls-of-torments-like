package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Direct reward fixtures; choice time advances without advancing the arena. */
class LootPauseTest {
    @Test void lootChoicesFreezeCombatHealthCooldownsRevivalAndPickupsForTheWholeParty() {
        ArenaWorld world = party(); var gear = world.equipment;
        gear.equip(0, 1, ItemCatalog.byId("emberheart_pendant"));
        var first = world.players[0]; var second = world.players[1];
        first.health = 80; first.dodgeCooldown = 1.5f;
        first.attackCooldown = 2; first.auraCooldown = 1; first.stormCooldown = 3;
        second.alive = false; second.health = 0; second.reviveProgress = .8f;
        assertTrue(gear.regeneration(first) > 0);
        var enemy = world.enemies[0];
        enemy.active = true; enemy.x = 300; enemy.y = 20; enemy.health = 100;
        enemy.attackCooldown = .5f; enemy.stunTime = .6f; world.activeEnemies = 1;
        var bolt = world.bolts[0]; bolt.active = true; bolt.x = 200; bolt.vx = 100; bolt.ttl = 2;
        var shot = world.shots[0]; shot.active = true; shot.x = 240; shot.vx = 100; shot.ttl = 3;
        var hazard = world.hazards[0]; hazard.active = true; hazard.warning = .6f; hazard.ttl = 2;
        var effect = world.effects[0]; effect.active = true; effect.ttl = .8f;
        var pickup = world.pickups[0]; pickup.active = true; pickup.x = 0; pickup.value = 2;
        gear.grantReward();
        world.setInput(0, 1, 0, true); world.update(.1f);
        assertFalse(world.levelUpPending()); assertTrue(world.lootPending()); assertTrue(world.choicePending());
        assertEquals(0, world.elapsed); assertEquals(0, first.x); assertEquals(80, first.health);
        assertEquals(1.5f, first.dodgeCooldown); assertEquals(0, first.dodgeTime);
        assertEquals(2, first.attackCooldown); assertEquals(1, first.auraCooldown); assertEquals(3, first.stormCooldown);
        assertEquals(.8f, second.reviveProgress); assertFalse(second.alive);
        assertEquals(300, enemy.x); assertEquals(20, enemy.y);
        assertEquals(.5f, enemy.attackCooldown); assertEquals(.6f, enemy.stunTime);
        assertEquals(200, bolt.x); assertEquals(2, bolt.ttl);
        assertEquals(240, shot.x); assertEquals(3, shot.ttl);
        assertEquals(.6f, hazard.warning); assertEquals(2, hazard.ttl); assertEquals(.8f, effect.ttl);
        assertTrue(pickup.active); assertEquals(0, world.souls); assertEquals(1, world.activeEnemies);
        assertEquals(29.9f, gear.choiceSecondsLeft(), .0001f);
        assertEquals(gear.choiceSecondsLeft(), gear.offers[0].secondsLeft);
        assertEquals(gear.choiceSecondsLeft(), gear.offers[1].secondsLeft);
    }

    @Test void earlyDeclineWaitsForCompanionAndResumeDoesNotReplayHeldInput() {
        ArenaWorld world = party(); var gear = world.equipment; gear.grantReward();
        assertTrue(gear.choose(0, gear.offers[0].id, -1));
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertTrue(world.lootPending()); assertEquals(0, world.elapsed); assertEquals(0, world.players[0].x);
        assertFalse(gear.offers[0].pending);
        assertTrue(gear.choose(1, gear.offers[1].id, 0));
        assertFalse(world.choicePending());
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.STEP, world.elapsed, .00001f);
        assertEquals(0, world.players[0].x); assertEquals(0, world.players[0].dodgeCooldown);
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertTrue(world.players[0].x > 0);
    }

    @Test void timeoutDefaultsOnlyUnansweredLootAndQueuedRoundGetsFreshTimeWithoutCombatCatchup() {
        ArenaWorld world = party(); var gear = world.equipment;
        gear.grantReward(); gear.grantReward();
        int firstId = gear.offers[0].id; var expected = gear.offers[1].options[0];
        assertTrue(gear.choose(0, firstId, -1));
        assertFalse(gear.offers[0].pending, "An early finisher must not enter the next reward round alone");
        gear.updateChoices(RunEquipment.CHOICE_SECONDS - ArenaWorld.STEP);
        world.update(ArenaWorld.STEP);
        assertEquals(1, gear.appliedCount(0)); assertEquals(1, gear.appliedCount(1));
        for (int slot = 0; slot < RunEquipment.SLOT_COUNT; slot++) assertNull(gear.equipped(0, slot));
        boolean found = false;
        for (int slot = 0; slot < RunEquipment.SLOT_COUNT; slot++) found |= gear.equipped(1, slot) == expected;
        assertTrue(found, "The unanswered player receives the first offered item");
        assertTrue(world.lootPending()); assertTrue(gear.offers[0].id > firstId);
        assertEquals(30, gear.choiceSecondsLeft()); assertEquals(0, world.elapsed);
        gear.updateChoices(RunEquipment.CHOICE_SECONDS - ArenaWorld.STEP);
        world.update(ArenaWorld.STEP);
        assertEquals(2, gear.appliedCount(0)); assertEquals(2, gear.appliedCount(1));
        assertFalse(world.choicePending()); assertEquals(0, world.elapsed);
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.STEP, world.elapsed, .00001f);
    }

    @Test void skillsPreemptLootWithoutSpendingItsWindowAndDeferredLootStartsBeforeCombat() {
        ArenaWorld world = party(); var gear = world.equipment;
        gear.grantReward(); gear.updateChoices(4);
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertTrue(world.levelUpPending()); assertEquals(26, gear.choiceSecondsLeft());
        assertEquals(29.9f, world.progression.choiceSecondsLeft(), .0001f); assertEquals(0, world.elapsed);
        choosePartySkills(world);
        world.update(.1f);
        assertFalse(world.levelUpPending()); assertTrue(world.lootPending());
        assertEquals(25.9f, gear.choiceSecondsLeft(), .0001f); assertEquals(0, world.elapsed);

        ArenaWorld deferred = party();
        deferred.progression.addXp(deferred.progression.xpToNext);
        deferred.equipment.grantReward();
        assertFalse(deferred.lootPending());
        choosePartySkills(deferred);
        deferred.update(ArenaWorld.STEP);
        assertTrue(deferred.lootPending()); assertEquals(0, deferred.elapsed);
        assertEquals(30 - ArenaWorld.STEP, deferred.equipment.choiceSecondsLeft(), .0001f);
    }

    @Test void existingParticipantReconnectKeepsLootChoiceClockBuildAndHealth() {
        ArenaWorld world = party(); var gear = world.equipment; gear.grantReward();
        assertTrue(gear.choose(0, gear.offers[0].id, -1)); gear.updateChoices(4);
        int id = gear.offers[1].id;
        var first = gear.offers[1].options[0]; var second = gear.offers[1].options[1]; var third = gear.offers[1].options[2];
        var player = world.players[1]; player.health = 73; player.dodgeCooldown = .7f;
        world.activatePlayer(1); // Reconciliation of the same reserved participant must be a no-op.
        assertTrue(gear.offers[1].pending); assertEquals(id, gear.offers[1].id);
        assertEquals(26, gear.choiceSecondsLeft()); assertEquals(26, gear.offers[1].secondsLeft);
        assertSame(first, gear.offers[1].options[0]); assertSame(second, gear.offers[1].options[1]);
        assertSame(third, gear.offers[1].options[2]);
        assertEquals(73, player.health); assertEquals(.7f, player.dodgeCooldown);
        assertEquals(0, gear.appliedCount(1)); assertEquals(0, world.progression.appliedUpgradeCount(1));
    }

    private static void choosePartySkills(ArenaWorld world) {
        while (world.levelUpPending()) for (int slot = 0; slot < 2; slot++) {
            var offer = world.progression.offers[slot];
            if (offer.pending) assertTrue(world.progression.choose(slot, offer.id, 0));
        }
    }

    private static ArenaWorld party() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.activatePlayer(1); return world;
    }
}
