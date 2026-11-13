package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunEquipmentTest {
    @Test void partyLootSharesOneClockAndEarlyChooserWaitsBeforeQueuedReward() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        var gear = world.equipment; gear.grantReward();
        int firstId = gear.offers[0].id, secondId = gear.offers[1].id;
        gear.updateChoices(5);
        assertEquals(25, gear.choiceSecondsLeft());
        assertEquals(25, gear.offers[0].secondsLeft); assertEquals(25, gear.offers[1].secondsLeft);
        assertTrue(gear.choose(0, firstId, -1));
        gear.grantReward();
        assertFalse(gear.offers[0].pending, "An early finisher must wait for the current party round");
        assertEquals(secondId, gear.offers[1].id); assertEquals(25, gear.choiceSecondsLeft());
        assertFalse(gear.choose(0, firstId, 0));
        gear.updateChoices(4);
        assertEquals(21, gear.choiceSecondsLeft()); assertEquals(21, gear.offers[1].secondsLeft);
        assertTrue(gear.choose(1, secondId, 2));
        assertEquals(1, gear.appliedCount(0)); assertEquals(1, gear.appliedCount(1));
        assertTrue(gear.offers[0].pending); assertTrue(gear.offers[1].pending);
        assertTrue(gear.offers[0].id > firstId); assertTrue(gear.offers[1].id > secondId);
        assertEquals(30, gear.choiceSecondsLeft());
        assertEquals(30, gear.offers[0].secondsLeft); assertEquals(30, gear.offers[1].secondsLeft);
    }

    @Test void timeoutDefaultsOnlyUnansweredLootAndStartsOneFreshQueuedRound() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        var gear = world.equipment; gear.grantReward(); gear.grantReward();
        var personal = gear.offers[0].options[1]; var defaultItem = gear.offers[1].options[0];
        int old = gear.offers[1].id;
        assertTrue(gear.choose(0, gear.offers[0].id, 1));
        gear.updateChoices(90);
        assertEquals(1, gear.appliedCount(0)); assertEquals(1, gear.appliedCount(1));
        assertTrue(wearing(gear, 0, personal)); assertTrue(wearing(gear, 1, defaultItem));
        assertTrue(gear.offers[0].pending); assertTrue(gear.offers[1].pending);
        assertTrue(gear.offers[1].id > old); assertFalse(gear.choose(1, old, 0));
        assertEquals(30, gear.choiceSecondsLeft(), "A long delta must not spend the queued reward's new window");
        gear.updateChoices(30);
        assertEquals(2, gear.appliedCount(0)); assertEquals(2, gear.appliedCount(1));
        assertEquals(0, gear.choiceSecondsLeft());
        assertFalse(gear.offers[0].pending); assertFalse(gear.offers[1].pending);
    }

    @Test void skillPriorityDefersNewLootAndFreezesWholeExistingLootRound() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        var gear = world.equipment;
        world.progression.addXp(world.progression.xpToNext);
        gear.grantReward();
        assertFalse(gear.offers[0].pending); assertFalse(gear.offers[1].pending);
        assertEquals(0, gear.choiceSecondsLeft());
        for (int slot = 0; slot < 2; slot++)
            assertTrue(world.progression.choose(slot, world.progression.offers[slot].id, 0));
        gear.updateChoices(0);
        assertTrue(gear.offers[0].pending); assertTrue(gear.offers[1].pending);
        assertEquals(30, gear.choiceSecondsLeft());
        gear.updateChoices(5);
        int firstId = gear.offers[0].id;
        world.progression.addXp(world.progression.xpToNext);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        assertTrue(world.levelUpPending());
        assertFalse(gear.choose(0, firstId, -1), "A player finished with skills still waits for the companion");
        gear.updateChoices(20);
        assertEquals(25, gear.choiceSecondsLeft()); assertEquals(25, gear.offers[0].secondsLeft);
        assertEquals(25, gear.offers[1].secondsLeft);
    }

    @Test void choiceOnlyUpdatesDoNotGrantTimedRewardsOrRefillCombatSustainBudget() {
        ArenaWorld world = solo(); var gear = world.equipment;
        gear.equip(0, 6, ItemCatalog.byId("reapers_wrappings"));
        world.players[0].health = 50;
        for (int i = 0; i < 3; i++) gear.drain(world.players[0], 100);
        assertEquals(52, world.players[0].health, .0001f);
        gear.grantReward(); world.elapsed = 1140;
        gear.updateChoices(5);
        gear.drain(world.players[0], 100);
        assertEquals(52, world.players[0].health, .0001f);
        assertEquals(1, gear.rewardsEarned(), "Reliquary scheduling belongs to combat updates");
        assertEquals(25, gear.choiceSecondsLeft());
    }

    @Test void equipmentPreviewsKeepExactFractionalHealingAndTradeoffValues() {
        assertEquals("+0.25 HP/s / +10 HP", RunEquipment.modifiers(ItemCatalog.byId("emberheart_pendant")));
        assertEquals("+0.35 HP/s / -5% DMG", RunEquipment.modifiers(ItemCatalog.byId("penitents_habit")));
        assertEquals("+6% XP / +0.15 HP/KILL", RunEquipment.modifiers(ItemCatalog.byId("soulkeepers_locket")));
        assertEquals("+0.8% LIFE STEAL / +6% DMG", RunEquipment.modifiers(ItemCatalog.byId("reapers_wrappings")));
    }

    @Test void keepingCurrentEquipmentConsumesOnlyTheLootChoiceAndRejectsReplays() {
        ArenaWorld world = solo(); var gear = world.equipment;
        gear.equip(0, 0, ItemCatalog.byId("gatekeeper_helm"));
        gear.grantReward(); int id = gear.offers[0].id;
        assertFalse(gear.choose(0, id, -2));
        assertTrue(gear.choose(0, id, -1));
        assertEquals(1, gear.appliedCount(0));
        assertFalse(gear.offers[0].pending);
        assertSame(ItemCatalog.byId("gatekeeper_helm"), gear.equipped(0, 0));
        assertEquals(120, gear.maxHealth(world.players[0]));
        assertFalse(gear.choose(0, id, 0));
    }

    @Test void rewardsArePersonalBoundedAndFullyDefaultedForLateJoining() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        for (int i = 0; i < 20; i++) gear.grantReward();
        assertEquals(8, gear.rewardsEarned());
        assertTrue(gear.offers[0].pending);
        assertFalse(gear.offers[1].pending);
        int old = gear.offers[0].id;
        assertFalse(gear.choose(1, old, 0));
        assertTrue(gear.choose(0, old, 0));
        assertFalse(gear.choose(0, old, 0));
        world.activatePlayer(1);
        assertEquals(8, gear.appliedCount(1));
        assertEquals(8, gear.foundationCount(1));
        assertEquals(0, gear.catchUpRemaining(1));
        assertFalse(gear.offers[1].pending);
        assertFalse(gear.choosing(1));
        assertEquals(7, gear.foundationItems(1).size(), "Eight defaults produce a bounded seven-slot final build");
        assertSame(ItemCatalog.byId("marching_spurs"), gear.equipped(1, 5));
        assertFalse(gear.foundationSummary(1).contains("Pilgrim's Stride"), "Replaced gear is not shown as equipped");
        assertEquals(1, gear.appliedCount(0));
        world.reset(); world.activatePlayer(0); gear.grantReward();
        assertFalse(gear.choose(0, old, 0));
        assertEquals(0, gear.appliedCount(0));
        assertTrue(gear.foundationItems(1).isEmpty());
        for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) assertNull(gear.equipped(0, position));
    }

    @Test void everyInheritedRewardCountGetsUsefulDistinctDefaultsForEitherHeroWithoutAnyChooser() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) for (int rewards = 0; rewards <= 8; rewards++) {
            ArenaWorld world = solo(); RunEquipment gear = world.equipment;
            for (int i = 0; i < rewards; i++) gear.grantReward();
            world.setHero(1, hero); world.activatePlayer(1);
            assertEquals(rewards, gear.appliedCount(1));
            assertEquals(rewards, gear.foundationCount(1));
            assertFalse(gear.offers[1].pending);
            assertFalse(gear.choosing(1));
            assertEquals(gear.maxHealth(world.players[1]), world.players[1].health);
            java.util.Set<ItemCatalog.Item> items = new java.util.HashSet<>();
            for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) {
                var item = gear.equipped(1, position);
                if (item == null) continue;
                assertTrue(items.add(item)); assertTrue(item.supports(hero));
                assertTrue(gear.foundationSummary(1).contains(item.title()));
            }
            assertEquals(Math.min(rewards, 7), items.size());
            var foundation = gear.foundationItems(1);
            assertEquals(items.size(), foundation.size());
            for (var entry : foundation) {
                var item = ItemCatalog.byId(entry.id());
                assertTrue(items.contains(item)); assertEquals(item.title(), entry.title());
                assertEquals(ItemCatalog.artIndex(item), entry.icon()); assertEquals(item.slot().name(), entry.slot());
            }
            assertThrows(UnsupportedOperationException.class, () -> foundation.clear());
            world.players[1].health = 80;
            world.activatePlayer(1); gear.activatePlayer(1);
            assertFalse(gear.offers[1].pending);
            assertEquals(rewards, gear.appliedCount(1)); assertEquals(80, world.players[1].health);
            assertSame(foundation, gear.foundationItems(1));
        }
    }

    @Test void joiningDefaultsEarlierRewardAndFutureRewardsUsePersonalThirtySecondChoices() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        gear.grantReward(); world.activatePlayer(1);
        assertEquals(1, gear.foundationCount(1));
        assertSame(ItemCatalog.byId("gatekeeper_helm"), gear.equipped(1, 0));
        assertEquals(0, gear.catchUpRemaining(1));
        assertFalse(gear.offers[1].pending);
        var foundation = gear.foundationItems(1);
        assertTrue(gear.choose(0, gear.offers[0].id, -1));
        gear.grantReward();
        assertTrue(gear.offers[1].pending); assertFalse(gear.offers[1].catchUp);
        assertEquals(30, gear.offers[1].secondsLeft); assertEquals(30, gear.offers[1].durationSeconds);
        int personalId = gear.offers[1].id;
        gear.update(5);
        world.players[1].health = 73;
        world.activatePlayer(1); gear.activatePlayer(1);
        assertEquals(personalId, gear.offers[1].id);
        assertEquals(25, gear.offers[1].secondsLeft);
        assertEquals(73, world.players[1].health);
        assertSame(foundation, gear.foundationItems(1));
        assertTrue(gear.choose(1, personalId, -1));
        assertFalse(gear.offers[1].pending);
        assertEquals(2, gear.appliedCount(1));
        assertFalse(gear.choose(1, personalId, 0));
        assertEquals(1, gear.appliedCount(0), "Joining player's choices never consume the host's current reward");
    }

    @Test void futureOfferRefreshPreservesNormalTimerAndRemovalRebuildsDefaults() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        gear.grantReward(); world.activatePlayer(1);
        assertTrue(gear.choose(0, gear.offers[0].id, -1));
        gear.grantReward();
        var offer = gear.offers[1]; gear.update(8);
        int oldId = offer.id;
        offer.options[0] = ItemCatalog.byId("gatekeeper_helm"); // Invalid because defaults already own it.
        assertFalse(gear.choose(1, oldId, 0));
        assertNotEquals(oldId, offer.id); assertFalse(offer.catchUp);
        assertEquals(30, offer.durationSeconds); assertEquals(22, offer.secondsLeft);
        assertEquals(0, gear.catchUpRemaining(1));
        world.deactivatePlayer(1);
        assertFalse(gear.catchingUp(1)); assertEquals(0, gear.foundationCount(1));
        assertEquals("", gear.foundationSummary(1)); assertEquals(0, offer.durationSeconds);
        assertTrue(gear.foundationItems(1).isEmpty());
        assertFalse(gear.choose(1, oldId, 0));
        world.activatePlayer(1);
        assertFalse(offer.pending); assertEquals(2, gear.foundationCount(1));
        assertEquals(22, gear.choiceSecondsLeft(), "Removing a player preserves the other player's deadline");
        assertTrue(gear.choose(0, gear.offers[0].id, -1));
        gear.grantReward();
        assertTrue(offer.id > oldId); assertEquals(30, offer.secondsLeft);
    }

    @Test void levelChoicesFreezeEquipmentCountdownAndPreventHiddenSelection() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        gear.grantReward(); int id = gear.offers[0].id;
        world.progression.addXp(12);
        gear.update(29);
        assertEquals(30, gear.offers[0].secondsLeft);
        assertFalse(gear.choose(0, id, 0));
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        gear.update(29);
        assertEquals(1, gear.offers[0].secondsLeft);
        var defaultItem = gear.offers[0].options[0];
        gear.update(1);
        assertFalse(gear.offers[0].pending);
        boolean found = false;
        for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) found |= gear.equipped(0, position) == defaultItem;
        assertTrue(found);
    }

    @Test void directCacheBoundariesAndEliteRewardsCannotExceedEight() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        world.elapsed = 89.99f; gear.update(.01f); assertEquals(0, gear.rewardsEarned());
        world.elapsed = 90; gear.update(.01f); assertEquals(1, gear.rewardsEarned());
        gear.update(.01f); assertEquals(1, gear.rewardsEarned());
        world.elapsed = 600; gear.update(.01f); assertEquals(2, gear.rewardsEarned());
        world.elapsed = 1140; gear.update(.01f); assertEquals(3, gear.rewardsEarned());
        for (int i = 0; i < 10; i++) gear.rewardElite();
        assertEquals(8, gear.rewardsEarned());
        assertTrue(world.elapsed < world.durationSeconds());
    }

    @Test void replacementRemovesBonusesAndKeepsSkillStateAndVitalityPreviewAccurate() {
        ArenaWorld world = solo(); var player = world.players[0]; var gear = world.equipment;
        gear.equip(0, 4, ItemCatalog.byId("cryptguard_plate"));
        assertEquals(100, player.maxHealth);
        assertEquals(130, gear.maxHealth(player));
        assertEquals(.92f, gear.moveMultiplier(player), .0001f);
        var preview = world.progression.preview(0, PartyProgression.Upgrade.VITALITY);
        assertEquals("130 MAX HP", preview.before()); assertEquals("150 MAX HP", preview.after());
        player.health = 125;
        gear.equip(0, 4, ItemCatalog.byId("wayfarer_leathers"));
        assertEquals(100, gear.maxHealth(player)); assertEquals(100, player.health);
        assertEquals(1.08f, gear.moveMultiplier(player), .0001f);
        assertEquals(1, player.moveMultiplier);
        gear.equip(0, 2, ItemCatalog.byId("rustbound_signet"));
        gear.equip(0, 3, ItemCatalog.byId("quickpulse_loop"));
        assertEquals(1.12f, gear.damageMultiplier(player), .0001f);
        assertEquals(1.1f, gear.attackRate(player), .0001f);
        gear.equip(0, 2, ItemCatalog.byId("pale_root_ring"));
        assertEquals(1, gear.damageMultiplier(player));
        assertSame(ItemCatalog.byId("quickpulse_loop"), gear.equipped(0, 3));
    }

    @Test void everyOfferPreservesFullModifierTradeoffsAndNoUnsupportedAbilityCards() {
        ArenaWorld world = solo(); world.equipment.grantReward();
        var offer = world.equipment.offers[0];
        for (int i = 0; i < 3; i++) {
            var item = offer.options[i]; var card = offer.cards[i];
            assertEquals(0, item.amount(ItemCatalog.Stat.AURA_DAMAGE));
            assertEquals(0, item.amount(ItemCatalog.Stat.STORM_DAMAGE));
            assertEquals("EMPTY", card.before());
            assertFalse(card.after().isBlank());
            assertTrue(card.headline().startsWith("EQUIP "));
            assertTrue(card.after().length() <= 128);
        }
    }

    @Test void skillCapsRefreshHiddenEquipmentAndCannotOfferZeroArrowSkill() {
        ArenaWorld world = new ArenaWorld(); world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        var player = world.players[0]; player.projectileCount = 4;
        world.equipment.equip(0, 0, ItemCatalog.byId("fletchers_hood"));
        world.progression.addXp(6);
        for (var option : world.progression.offers[0].options) assertNotEquals(PartyProgression.Upgrade.MULTISHOT, option);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.equipment.grantReward();
        var offer = world.equipment.offers[0];
        int old = offer.id;
        offer.options[0] = ItemCatalog.byId("fletchers_hood"); // Simulates an old hidden offer before the cap changed.
        assertFalse(world.equipment.choose(0, old, 0));
        assertNotEquals(old, offer.id);
        assertEquals(30, offer.secondsLeft);
        for (var item : offer.options) assertNotEquals(ItemCatalog.byId("fletchers_hood"), item);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); world.activatePlayer(0); return world;
    }

    private static boolean wearing(RunEquipment gear, int slot, ItemCatalog.Item item) {
        for (int position = 0; position < RunEquipment.SLOT_COUNT; position++)
            if (gear.equipped(slot, position) == item) return true;
        return false;
    }
}
