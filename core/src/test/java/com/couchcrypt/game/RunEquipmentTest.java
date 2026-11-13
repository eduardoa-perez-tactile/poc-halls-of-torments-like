package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunEquipmentTest {
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

    @Test void rewardsArePersonalBoundedAndRestoredForLateJoining() {
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
        assertEquals(6, gear.appliedCount(1));
        assertEquals(6, gear.foundationCount(1));
        var foundation = gear.foundationItems(1);
        assertEquals(2, gear.catchUpRemaining(1));
        assertTrue(gear.offers[1].pending);
        for (int i = 0; i < 2; i++) {
            var offer = gear.offers[1];
            assertTrue(offer.pending);
            assertTrue(offer.catchUp);
            assertEquals(15, offer.durationSeconds);
            for (var item : offer.options) assertTrue(item.supports(world.players[1].hero));
            assertTrue(gear.choose(1, offer.id, 0));
        }
        assertFalse(gear.offers[1].pending);
        assertEquals(8, gear.appliedCount(1));
        assertSame(foundation, gear.foundationItems(1));
        assertEquals(6, foundation.size(), "Personal gear must not leak into the starting-build snapshot");
        assertEquals(1, gear.appliedCount(0));
        world.reset(); world.activatePlayer(0); gear.grantReward();
        assertFalse(gear.choose(0, old, 0));
        assertEquals(0, gear.appliedCount(0));
        assertTrue(gear.foundationItems(1).isEmpty());
        for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) assertNull(gear.equipped(0, position));
    }

    @Test void lateJoinFoundationIsDistinctUsefulGearForEitherHeroAndReconnectDoesNotGrantAgain() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = solo(); RunEquipment gear = world.equipment;
            for (int i = 0; i < 8; i++) gear.grantReward();
            world.setHero(1, hero); world.activatePlayer(1);
            assertEquals(6, gear.appliedCount(1));
            assertEquals(160, gear.maxHealth(world.players[1]));
            assertEquals(160, world.players[1].health);
            java.util.Set<ItemCatalog.Item> items = new java.util.HashSet<>();
            for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) {
                var item = gear.equipped(1, position);
                if (item == null) continue;
                assertTrue(items.add(item)); assertTrue(item.supports(hero));
                assertTrue(gear.foundationSummary(1).contains(item.title()));
            }
            assertEquals(6, items.size());
            var foundation = gear.foundationItems(1);
            assertEquals(6, foundation.size());
            for (var entry : foundation) {
                var item = ItemCatalog.byId(entry.id());
                assertTrue(items.contains(item)); assertEquals(item.title(), entry.title());
                assertEquals(ItemCatalog.artIndex(item), entry.icon()); assertEquals(item.slot().name(), entry.slot());
            }
            assertThrows(UnsupportedOperationException.class, () -> foundation.clear());
            assertNull(gear.equipped(1, 3), "The second ring remains a personal equipment decision");
            int id = gear.offers[1].id;
            gear.update(4);
            world.players[1].health = 80;
            world.activatePlayer(1); gear.activatePlayer(1);
            assertEquals(id, gear.offers[1].id); assertEquals(11, gear.offers[1].secondsLeft);
            assertEquals(6, gear.appliedCount(1)); assertEquals(80, world.players[1].health);
            assertSame(foundation, gear.foundationItems(1));
        }
    }

    @Test void joiningWithOneRewardKeepsOnePersonalChoiceAndNewRewardsUseNormalTimers() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        gear.grantReward(); world.activatePlayer(1);
        assertEquals(0, gear.foundationCount(1));
        assertEquals("", gear.foundationSummary(1));
        assertEquals(1, gear.catchUpRemaining(1));
        assertEquals(15, gear.offers[1].secondsLeft);
        gear.grantReward();
        int catchUpId = gear.offers[1].id;
        assertTrue(gear.choose(1, catchUpId, -1), "Keep Current Gear consumes the inherited choice");
        assertFalse(gear.catchingUp(1)); assertEquals(0, gear.catchUpRemaining(1));
        assertTrue(gear.offers[1].pending); assertFalse(gear.offers[1].catchUp);
        assertEquals(30, gear.offers[1].secondsLeft); assertEquals(30, gear.offers[1].durationSeconds);
        assertFalse(gear.choose(1, catchUpId, 0));
        assertEquals(0, gear.appliedCount(0), "The established participant's choices are untouched");
    }

    @Test void catchUpOfferRefreshPreservesItsShortTimerAndResetClearsMetadata() {
        ArenaWorld world = solo(); RunEquipment gear = world.equipment;
        for (int i = 0; i < 8; i++) gear.grantReward();
        world.activatePlayer(1);
        var offer = gear.offers[1]; gear.update(8);
        int oldId = offer.id;
        offer.options[0] = ItemCatalog.byId("gatekeeper_helm"); // Now invalid because foundation already owns it.
        assertFalse(gear.choose(1, oldId, 0));
        assertNotEquals(oldId, offer.id); assertTrue(offer.catchUp);
        assertEquals(15, offer.durationSeconds); assertEquals(7, offer.secondsLeft);
        assertEquals(2, gear.catchUpRemaining(1));
        world.deactivatePlayer(1);
        assertFalse(gear.catchingUp(1)); assertEquals(0, gear.foundationCount(1));
        assertEquals("", gear.foundationSummary(1)); assertEquals(0, offer.durationSeconds);
        assertTrue(gear.foundationItems(1).isEmpty());
        assertFalse(gear.choose(1, offer.id, 0));
        world.activatePlayer(1);
        assertTrue(offer.id > oldId); assertEquals(6, gear.foundationCount(1));
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
}
