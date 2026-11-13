package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Direct catch-up boundaries, without a survival simulation. */
class FastLateJoinIntegrationTest {
    @Test void eitherHeroGetsAFoundationAndCompletesAllInheritedChoicesWithinSeventyFiveSeconds() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = cappedParty(hero);
            world.activatePlayer(1);
            var skills = world.progression; var gear = world.equipment;
            assertEquals(16, skills.foundationCount(1));
            assertEquals(16, skills.appliedUpgradeCount(1));
            assertEquals(6, gear.foundationCount(1));
            assertEquals(6, gear.appliedCount(1));
            assertEquals(3, skills.catchUpRemaining(1));
            assertEquals(2, gear.catchUpRemaining(1));
            assertEquals(gear.maxHealth(world.players[1]), world.players[1].health);
            assertEquals(0, skills.appliedUpgradeCount(0), "Joining never overwrites the companion's personal choices");
            assertEquals(0, gear.appliedCount(0));
            var offer = skills.offers[1];
            for (int i = 0; i < 3; i++)
                assertEquals(skills.preview(1, offer.options[i]), offer.cards[i], "Preview must include granted gear");

            float time = 0;
            int skillChoices = 0, gearChoices = 0;
            while (skills.catchingUp(1)) {
                assertTrue(skills.offers[1].catchUp);
                assertEquals(15, skills.offers[1].durationSeconds);
                float gearTime = gear.offers[1].secondsLeft;
                gear.update(15); // The inherited skill is still visible for this interval.
                assertEquals(gearTime, gear.offers[1].secondsLeft);
                skills.update(15);
                time += 15; skillChoices++;
                assertTrue(skillChoices <= 3);
            }
            while (gear.catchingUp(1)) {
                assertFalse(skills.offers[1].pending);
                assertTrue(gear.offers[1].catchUp);
                assertEquals(15, gear.offers[1].durationSeconds);
                gear.update(15);
                time += 15; gearChoices++;
                assertTrue(gearChoices <= 2);
            }
            assertEquals(3, skillChoices); assertEquals(2, gearChoices);
            assertEquals(75, time);
            assertEquals(19, skills.appliedUpgradeCount(1));
            assertEquals(8, gear.appliedCount(1));
            assertFalse(skills.offers[1].pending); assertFalse(gear.offers[1].pending);
        }
    }

    @Test void reconnectPreservesFoundationHealthAndCurrentChoiceInsteadOfGrantingAgain() {
        ArenaWorld world = cappedParty(ArenaWorld.Hero.RANGER); world.activatePlayer(1);
        var player = world.players[1]; player.health -= 17;
        world.progression.update(4);
        int skillId = world.progression.offers[1].id, gearId = world.equipment.offers[1].id;
        float health = player.health, remaining = world.progression.offers[1].secondsLeft;
        world.activatePlayer(1);
        assertEquals(health, player.health);
        assertEquals(skillId, world.progression.offers[1].id);
        assertEquals(gearId, world.equipment.offers[1].id);
        assertEquals(remaining, world.progression.offers[1].secondsLeft);
        assertEquals(16, world.progression.foundationCount(1));
        assertEquals(6, world.equipment.foundationCount(1));
    }

    @Test void newlyEarnedRewardsWaitBehindInheritedGearThenUseNormalThirtySecondChoices() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0);
        while (world.progression.level < 10)
            world.progression.addXp(world.progression.xpToNext - world.progression.xp);
        for (int i = 0; i < 4; i++) world.equipment.grantReward();
        world.activatePlayer(1);
        world.progression.addXp(world.progression.xpToNext - world.progression.xp);
        world.equipment.grantReward();
        assertEquals(3, world.progression.catchUpRemaining(1));
        assertEquals(2, world.equipment.catchUpRemaining(1));
        for (int i = 0; i < 3; i++) {
            var offer = world.progression.offers[1];
            assertTrue(world.progression.choose(1, offer.id, 1));
        }
        assertFalse(world.progression.offers[1].pending, "A new skill must not preempt inherited gear");
        for (int i = 0; i < 2; i++) {
            var offer = world.equipment.offers[1];
            assertTrue(world.equipment.choose(1, offer.id, i == 0 ? -1 : 1));
        }
        world.progression.update(ArenaWorld.STEP);
        assertTrue(world.progression.offers[1].pending);
        assertFalse(world.progression.offers[1].catchUp);
        assertEquals(30, world.progression.offers[1].durationSeconds);
        assertTrue(world.equipment.offers[1].pending);
        assertFalse(world.equipment.offers[1].catchUp);
        assertEquals(30, world.equipment.offers[1].durationSeconds);
        assertEquals(0, world.progression.catchUpRemaining(1));
        assertEquals(0, world.equipment.catchUpRemaining(1));
    }

    private static ArenaWorld cappedParty(ArenaWorld.Hero joiningHero) {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.setHero(1, joiningHero); world.activatePlayer(0);
        world.progression.addXp(1991);
        for (int i = 0; i < 8; i++) world.equipment.grantReward();
        return world;
    }
}
