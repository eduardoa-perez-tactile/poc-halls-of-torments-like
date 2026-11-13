package com.couchcrypt.game;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyProgressionTest {
    @Test void longRunMilestonesLeaveLateUpgradesAndCapAtNineteenChoices() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(0);
        PartyProgression progression = world.progression;
        assertEquals(12, progression.xpToNext);
        progression.addXp(11);
        assertEquals(1, progression.level);
        assertFalse(progression.offers[0].pending);
        progression.addXp(1);
        assertEquals(2, progression.level);
        int firstOffer = progression.offers[0].id;
        int[] cumulativeXp = {116, 516, 1166, 1646, 1990, 1991};
        int[] levels = {5, 10, 15, 18, 19, 20};
        for (int i = 0; i < cumulativeXp.length; i++) {
            progression.addXp(cumulativeXp[i] - progression.totalXp);
            assertEquals(levels[i], progression.level, "Long-run collected XP milestone " + cumulativeXp[i]);
            assertEquals(firstOffer, progression.offers[0].id, "Level bursts must retain the pending personal choice");
        }
        assertEquals(0, progression.xp);
        assertEquals(0, progression.xpToNext);
        progression.addXp(Integer.MAX_VALUE);
        assertEquals(PartyProgression.MAX_LEVEL, progression.level);
        assertEquals(Integer.MAX_VALUE, progression.totalXp);
    }

    @Test void lateJoinAtLongRunCapReceivesAllPersonalChoicesWithFreshTimersAndClassCards() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(0);
        world.progression.addXp(1991);
        world.setHero(1, ArenaWorld.Hero.RANGER);
        world.activatePlayer(1);
        PartyProgression progression = world.progression;
        int firstId = progression.offers[1].id;
        progression.update(PartyProgression.CHOICE_SECONDS + 90);
        assertEquals(1, progression.appliedUpgradeCount(1), "A delayed update cannot skip catch-up decisions");
        assertEquals(3, progression.offers[1].level);
        assertEquals(PartyProgression.CHOICE_SECONDS, progression.offers[1].secondsLeft);
        assertFalse(progression.choose(1, firstId, 0));
        int hostApplied = progression.appliedUpgradeCount(0);
        while (progression.offers[1].pending) {
            PartyProgression.Offer offer = progression.offers[1];
            assertEquals(PartyProgression.CHOICE_SECONDS, offer.secondsLeft);
            Set<PartyProgression.Upgrade> distinct = new HashSet<>();
            for (int i = 0; i < offer.options.length; i++) {
                assertTrue(distinct.add(offer.options[i]));
                assertTrue(offer.options[i].supports(world.players[1].hero));
                assertEquals(progression.rank(1, offer.options[i]) + 1, offer.cards[i].rank());
            }
            assertTrue(progression.choose(1, offer.id, 0));
        }
        assertEquals(19, progression.appliedUpgradeCount(1));
        assertEquals(0, progression.rank(1, PartyProgression.Upgrade.SWEEP));
        assertEquals(hostApplied, progression.appliedUpgradeCount(0), "The late joiner's choices do not consume the host's queue");
        assertTrue(progression.offers[0].pending);
    }

    @Test void eitherHeroCanSpendEveryLongRunEntitlementWithoutExhaustingDistinctEligibleChoices() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
            world.setHero(0, hero); world.activatePlayer(0);
            PartyProgression progression = world.progression;
            progression.addXp(1991);
            for (int entitlement = 0; entitlement < 19; entitlement++) {
                PartyProgression.Offer offer = progression.offers[0];
                assertTrue(offer.pending, hero + " choice " + entitlement);
                Set<PartyProgression.Upgrade> distinct = new HashSet<>();
                int leastRank = 0;
                for (int i = 0; i < offer.options.length; i++) {
                    assertTrue(distinct.add(offer.options[i]));
                    assertTrue(offer.options[i].supports(hero));
                    if (progression.rank(0, offer.options[i]) < progression.rank(0, offer.options[leastRank])) leastRank = i;
                }
                assertTrue(progression.choose(0, offer.id, leastRank));
            }
            assertEquals(19, progression.appliedUpgradeCount(0));
            assertFalse(progression.offers[0].pending);
        }
    }

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

    @Test void cardPreviewsMatchActualAppliedTraitsForBothHeroesAndPartialCaps() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            for (boolean capped : new boolean[]{false, true}) {
                for (PartyProgression.Upgrade upgrade : PartyProgression.Upgrade.values()) {
                    if (!upgrade.supports(hero)) continue;
                    ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0);
                    ArenaWorld.Player player = world.players[0];
                    if (capped) {
                        player.damageMultiplier = 3.9f; player.attackRateMultiplier = 2.95f;
                        player.moveMultiplier = 2.15f; player.maxHealth = 290; player.health = 83;
                        player.dodgeCooldownMultiplier = .3f; player.cleaveMultiplier = 1.75f;
                        player.projectileCount = 4; player.pierce = player.auraLevel = player.stormLevel = 2;
                        player.pickupMultiplier = 2.4f; player.critChance = .45f;
                    }
                    float[] beforeState = previewState(player);
                    PartyProgression.CardPreview card = world.progression.preview(0, upgrade);
                    assertArrayEquals(beforeState, previewState(player), "Preview must not mutate " + upgrade);
                    assertEquals(upgrade.ordinal(), card.icon()); assertEquals(1, card.rank());
                    assertTrue(Set.of("WEAPON", "OFFENSE", "DEFENSE", "MOBILITY", "UTILITY").contains(card.category()));
                    assertTrue(card.category().length() <= 16);
                    assertTrue(card.headline().length() <= 40 && card.before().length() <= 40 && card.after().length() <= 40);
                    assertPreviewValue(card.before(), player, upgrade);
                    selectFixture(world.progression, 0, upgrade);
                    assertPreviewValue(card.after(), player, upgrade);
                    if (capped) {
                        String expected = switch (upgrade) {
                            case POWER -> "+10% BASE DAMAGE";
                            case HASTE -> "+5% BASE ATTACK SPEED";
                            case FLEET -> "+5% BASE MOVE SPEED";
                            case VITALITY -> "+10 MAX HP · HEAL UP TO 20";
                            case EVASION -> "-0.12s DODGE COOLDOWN";
                            case SWEEP -> "+5% BASE SWORD REACH";
                            case MAGNET -> "+10% BASE PICKUP RANGE";
                            case CRITICAL -> "+5 PTS CRIT CHANCE";
                            default -> card.headline();
                        };
                        assertEquals(expected, card.headline(), upgrade + " partial-cap delta");
                    }
                }
            }
        }
    }

    @Test void newOffersCacheImmutableCardsAndAdvancePersonalRanksWithoutRecomputingWhilePending() {
        ArenaWorld world = solo(); PartyProgression progression = world.progression;
        progression.addXp(6);
        PartyProgression.Offer offer = progression.offers[0];
        PartyProgression.CardPreview[] cards = offer.cards.clone();
        for (int i = 0; i < 3; i++) {
            assertEquals(offer.options[i].ordinal(), cards[i].icon()); assertEquals(1, cards[i].rank());
        }
        progression.update(1); progression.addXp(10);
        for (int i = 0; i < 3; i++) assertSame(cards[i], offer.cards[i], "Pending cards must be cached");
        assertEquals(PartyProgression.Upgrade.POWER, offer.options[0]);
        assertTrue(progression.choose(0, offer.id, 0));
        assertNotSame(cards[0], offer.cards[0]); assertEquals(2, offer.cards[0].rank());
        assertEquals("125% DAMAGE", offer.cards[0].before()); assertEquals("150% DAMAGE", offer.cards[0].after());
        world.deactivatePlayer(0);
        for (PartyProgression.CardPreview card : offer.cards) assertNull(card);
    }

    private static float[] previewState(ArenaWorld.Player p) {
        return new float[]{p.damageMultiplier, p.attackRateMultiplier, p.moveMultiplier, p.maxHealth, p.health,
            p.dodgeCooldownMultiplier, p.cleaveMultiplier, p.projectileCount, p.pierce, p.auraLevel, p.stormLevel,
            p.pickupMultiplier, p.critChance, p.attackCooldown, p.dodgeCooldown};
    }

    private static void assertPreviewValue(String text, ArenaWorld.Player p, PartyProgression.Upgrade upgrade) {
        java.util.regex.Matcher numbers = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(text);
        java.util.List<Float> values = new java.util.ArrayList<>();
        while (numbers.find()) values.add(Float.parseFloat(numbers.group()));
        if ((upgrade == PartyProgression.Upgrade.AURA && p.auraLevel == 0)
            || (upgrade == PartyProgression.Upgrade.STORM && p.stormLevel == 0)) {
            assertEquals("NONE", text); return;
        }
        float actual = switch (upgrade) {
            case POWER -> p.damageMultiplier * 100;
            case HASTE -> p.attackRateMultiplier * 100;
            case FLEET -> p.moveMultiplier * 100;
            case VITALITY -> p.maxHealth;
            case EVASION -> p.hero.dodgeCooldown * p.dodgeCooldownMultiplier;
            case SWEEP -> p.cleaveMultiplier * 100;
            case MULTISHOT -> p.projectileCount;
            case PIERCE -> p.pierce;
            case AURA -> (4 + p.auraLevel * 3) * p.damageMultiplier;
            case STORM -> (12 + p.stormLevel * 5) * p.damageMultiplier;
            case MAGNET -> p.pickupMultiplier * 100;
            case CRITICAL -> p.critChance * 100;
        };
        assertEquals(actual, values.get(0), .0051f, upgrade + " displayed value");
        if (upgrade == PartyProgression.Upgrade.AURA) {
            assertEquals(.7f, values.get(1)); assertEquals(100 + p.auraLevel * 20, values.get(2));
        } else if (upgrade == PartyProgression.Upgrade.STORM) {
            assertEquals(p.stormLevel + 1, values.get(1)); assertEquals(2f, values.get(2));
        }
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
