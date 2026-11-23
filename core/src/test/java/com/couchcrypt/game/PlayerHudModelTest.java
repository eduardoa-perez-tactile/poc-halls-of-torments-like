package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.couchcrypt.game.PartyProgression.Upgrade.*;

class PlayerHudModelTest {
    @Test void sparseBuildsUseActualRanksAndChosenHeroInsteadOfPhoneSlot() {
        ArenaWorld world = new ArenaWorld();
        world.setHero(0, ArenaWorld.Hero.RANGER); world.setHero(1, ArenaWorld.Hero.WARDEN);
        world.reset();
        world.activatePlayer(0); world.activatePlayer(1);
        PlayerHudModel ranger = new PlayerHudModel(), warden = new PlayerHudModel();
        ranger.refresh(world, 0); warden.refresh(world, 1);
        assertEmpty(ranger); assertEmpty(warden); // Automatic base weapons do not invent upgrade ranks.
        // Every party round waits for both players before another earned offer opens.
        choose(world, 0, POWER); choose(world, 1, HASTE);
        choose(world, 0, POWER); choose(world, 1, SWEEP);
        choose(world, 0, MULTISHOT);
        ranger.refresh(world, 0); warden.refresh(world, 1);
        assertBuild(ranger, new PartyProgression.Upgrade[]{MULTISHOT, POWER}, new int[]{1, 2}, 0);
        assertBuild(warden, new PartyProgression.Upgrade[]{SWEEP, HASTE}, new int[]{1, 1}, 0);
        assertEquals(-1, ranger.iconIndices[2]); assertEquals(0, ranger.ranks[2]);
        assertTrue(world.progression.offers[1].pending); // An unchosen offer adds no HUD emblem.
    }

    @Test void denseBuildsPrioritizeWeaponTraitsAndCountUniqueOverflowRatherThanRanks() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0);
            for (PartyProgression.Upgrade upgrade : PartyProgression.Upgrade.values())
                if (upgrade.supports(hero)) choose(world, 0, upgrade);
            choose(world, 0, POWER); choose(world, 0, POWER);
            PlayerHudModel model = new PlayerHudModel(); model.refresh(world, 0);
            if (hero == ArenaWorld.Hero.WARDEN)
                assertBuild(model, new PartyProgression.Upgrade[]{SWEEP, POWER, HASTE, FLEET, VITALITY}, new int[]{1, 3, 1, 1, 1}, 5);
            else assertBuild(model, new PartyProgression.Upgrade[]{MULTISHOT, PIERCE, POWER, HASTE, FLEET}, new int[]{1, 1, 3, 1, 1}, 6);
        }
    }

    @Test void removalAndRunResetClearPreviouslyShownSkillsWithoutReplacingStorage() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        choose(world, 0, SWEEP); choose(world, 0, POWER);
        PlayerHudModel model = new PlayerHudModel(); model.refresh(world, 0);
        int[] icons = model.iconIndices, ranks = model.ranks;
        world.deactivatePlayer(0); model.refresh(world, 0);
        assertEmpty(model);
        world.activatePlayer(0); model.refresh(world, 0);
        // A fresh mid-run participant receives earned foundation ranks immediately.
        assertBuild(model, new PartyProgression.Upgrade[]{SWEEP, POWER}, new int[]{1, 1}, 0);
        assertFalse(world.progression.offers[0].pending);
        choose(world, 0, AURA); model.refresh(world, 0);
        assertBuild(model, new PartyProgression.Upgrade[]{SWEEP, POWER, AURA}, new int[]{1, 1, 1}, 0);
        world.reset(); model.refresh(world, 0); assertEmpty(model);
        world.activatePlayer(0); model.refresh(world, 0); assertEmpty(model);
        assertSame(icons, model.iconIndices); assertSame(ranks, model.ranks);
    }

    @Test void lateJoinShowsAutomaticClassFoundationAndLaterPersonalChoicesWhilePartyLevelStaysShared() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        choose(world, 0, POWER); choose(world, 0, POWER);
        int partyLevel = world.progression.level;
        world.activatePlayer(1);
        PlayerHudModel first = new PlayerHudModel(), second = new PlayerHudModel();
        first.refresh(world, 0); second.refresh(world, 1);
        assertBuild(second, new PartyProgression.Upgrade[]{MULTISHOT, POWER}, new int[]{1, 1}, 0);
        assertBuild(first, new PartyProgression.Upgrade[]{POWER}, new int[]{2}, 0);
        assertEquals(2, world.progression.foundationCount(1));
        assertFalse(world.progression.offers[1].pending);
        assertFalse(world.progression.catchingUp(1));
        assertEquals(0f, world.progression.choiceSecondsLeft());
        assertEquals(partyLevel, world.progression.level);

        world.progression.addXp(world.progression.xpToNext - world.progression.xp);
        assertTrue(world.progression.offers[0].pending);
        assertTrue(world.progression.offers[1].pending);
        second.refresh(world, 1);
        assertBuild(second, new PartyProgression.Upgrade[]{MULTISHOT, POWER}, new int[]{1, 1}, 0);
        choose(world, 1, PIERCE); second.refresh(world, 1);
        assertBuild(second, new PartyProgression.Upgrade[]{MULTISHOT, PIERCE, POWER}, new int[]{1, 1, 1}, 0);
        assertFalse(world.progression.offers[1].pending);
        assertEquals(partyLevel + 1, world.progression.level);
        first.refresh(world, 0);
        assertBuild(first, new PartyProgression.Upgrade[]{POWER}, new int[]{2}, 0);
    }

    private static void choose(ArenaWorld world, int slot, PartyProgression.Upgrade upgrade) {
        PartyProgression progression = world.progression;
        if (!progression.offers[slot].pending) progression.addXp(progression.xpToNext - progression.xp);
        PartyProgression.Offer offer = progression.offers[slot];
        offer.options[0] = upgrade;
        assertTrue(progression.choose(slot, offer.id, 0), "Fixture choice " + upgrade);
    }

    private static void assertBuild(PlayerHudModel model, PartyProgression.Upgrade[] expected, int[] ranks, int overflow) {
        assertEquals(expected.length, model.count); assertEquals(overflow, model.overflow);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].ordinal(), model.iconIndices[i]);
            assertEquals(ranks[i], model.ranks[i]);
        }
    }

    private static void assertEmpty(PlayerHudModel model) {
        assertEquals(0, model.count); assertEquals(0, model.overflow);
        for (int i = 0; i < PlayerHudModel.MAX_SKILLS; i++) {
            assertEquals(-1, model.iconIndices[i]); assertEquals(0, model.ranks[i]);
        }
    }
}
