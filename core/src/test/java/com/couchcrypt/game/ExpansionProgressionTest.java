package com.couchcrypt.game;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.couchcrypt.game.PartyProgression.Upgrade.*;

class ExpansionProgressionTest {
    @Test void supportingStatsRejectIneffectiveChoicesAndDoNotConsumeEntitlement() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        world.progression.addXp(6);
        assertRejected(world, PROJECTILE_SPEED);
        assertRejected(world, DURATION);
        world.players[0].glaiveLevel = 1;
        assertChosen(world, PROJECTILE_SPEED);
        assertEquals(1, world.players[0].projectileSpeedLevel);
        world.progression.addXp(world.progression.xpToNext);
        world.players[0].consecrationLevel = 1;
        assertChosen(world, DURATION);
        assertEquals(1, world.players[0].durationLevel);
    }

    @Test void evolutionsRequireTheirBuildAndCannotBePurchasedTwice() {
        for (var evolution : new PartyProgression.Upgrade[]{ECHO_STORM, BINDING_CIRCLE, SHATTER_BOLT, REAPING_STROKE}) {
            ArenaWorld world = solo(evolution == SHATTER_BOLT ? ArenaWorld.Hero.RANGER : ArenaWorld.Hero.WARDEN);
            world.progression.addXp(6);
            assertRejected(world, evolution);
            var player = world.players[0];
            switch (evolution) {
                case ECHO_STORM -> player.stormLevel = 3;
                case BINDING_CIRCLE -> player.auraLevel = 3;
                case SHATTER_BOLT -> player.pierce = 3;
                case REAPING_STROKE -> player.cleaveMultiplier = 1.8f;
                default -> fail();
            }
            assertChosen(world, evolution);
            assertEquals(1, world.progression.rank(0, evolution));
            world.progression.addXp(world.progression.xpToNext);
            assertRejected(world, evolution);
            assertEquals(1, world.progression.appliedUpgradeCount(0));
        }
    }

    @Test void pierceEquipmentCanCompleteTheShatterPrerequisiteWithoutBlockingTheBuild() {
        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        world.players[0].pierce = 2;
        world.equipment.equip(0, 6, ItemCatalog.byId("needlegrip_gloves"));
        assertEquals(3, world.equipment.pierce(world.players[0]));
        world.progression.addXp(6);
        assertEquals(SHATTER_BOLT, world.progression.offers[0].options[0]);
        assertChosen(world, SHATTER_BOLT);
        assertTrue(world.players[0].shatterBolt);
        assertEquals(2, world.players[0].pierce);
    }

    @Test void skippedEvolutionsRemainDiscoverableAndDoNotHideEachOther() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        world.players[0].stormLevel = world.players[0].auraLevel = 3;
        world.players[0].cleaveMultiplier = 1.8f;
        EnumSet<PartyProgression.Upgrade> seen = EnumSet.noneOf(PartyProgression.Upgrade.class);
        for (int i = 0; i < 4; i++) {
            world.progression.addXp(world.progression.xpToNext - world.progression.xp);
            var offer = world.progression.offers[0];
            assertTrue(offer.options[0].evolution(), "Eligible evolution must have a protected slot");
            for (var option : offer.options) if (option.evolution()) seen.add(option);
            offer.options[2] = POWER;
            assertTrue(world.progression.choose(0, offer.id, 2));
        }
        assertTrue(seen.containsAll(EnumSet.of(ECHO_STORM, BINDING_CIRCLE, REAPING_STROKE)));
    }

    @Test void everyNewClassEligibleMechanicGetsAnOpeningDiscoveryOpportunity() {
        for (var hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = solo(hero);
            EnumSet<PartyProgression.Upgrade> seen = EnumSet.noneOf(PartyProgression.Upgrade.class);
            for (int level = 2; level <= 9; level++) {
                world.progression.addXp(world.progression.xpToNext - world.progression.xp);
                var offer = world.progression.offers[0];
                for (var option : offer.options) {
                    seen.add(option); assertTrue(option.supports(hero));
                }
                // Declining a new attack must not prevent the other opening discoveries.
                offer.options[0] = POWER;
                assertTrue(world.progression.choose(0, offer.id, 0));
            }
            assertTrue(seen.containsAll(EnumSet.of(CONSECRATION, GLAIVE, WARD, FROST)));
            assertTrue(seen.contains(hero == ArenaWorld.Hero.WARDEN ? SENTINEL : RICOCHET));
        }
    }

    @Test void expandedLateJoinBuildSatisfiesItsEvolutionsAtEveryPrefix() {
        for (var hero : ArenaWorld.Hero.values()) for (int choices = 0; choices <= 19; choices++) {
            ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
            while (world.progression.level <= choices) world.progression.addXp(world.progression.xpToNext);
            world.setHero(1, hero); world.activatePlayer(1);
            var player = world.players[1];
            assertEquals(choices, world.progression.appliedUpgradeCount(1));
            assertFalse(world.progression.offers[1].pending);
            if (player.echoStorm) assertEquals(3, player.stormLevel);
            if (player.bindingCircle) assertEquals(3, player.auraLevel);
            if (player.reapingStroke) assertEquals(1.8f, player.cleaveMultiplier, .0001f);
            if (player.shatterBolt) assertEquals(3, world.equipment.pierce(player));
            if (choices == 19) {
                assertTrue(player.consecrationLevel > 0 && player.glaiveLevel > 0 && player.wardLevel > 0 && player.frostLevel > 0);
                assertTrue(hero == ArenaWorld.Hero.WARDEN ? player.sentinelLevel > 0 : player.ricochetLevel > 0);
                assertTrue(player.bindingCircle);
            }
        }
    }

    @Test void expandedCardsUseEffectiveDamageAndSupportingStatsWithoutMutatingBuild() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        var player = world.players[0];
        player.damageMultiplier = 2;
        player.durationLevel = 2;
        var sentinel = world.progression.preview(0, SENTINEL);
        assertEquals("NONE", sentinel.before());
        assertTrue(sentinel.after().contains("24 DMG"));
        var field = world.progression.preview(0, CONSECRATION);
        assertTrue(field.after().contains("22 DMG"));
        assertTrue(field.after().contains("3.9s"));
        var frost = world.progression.preview(0, FROST);
        assertTrue(frost.after().contains("15% SLOW / 1.3s"));
        assertEquals(0, player.sentinelLevel); assertEquals(0, player.consecrationLevel); assertEquals(0, player.frostLevel);
        assertEquals(2, player.durationLevel);
        for (var upgrade : PartyProgression.Upgrade.values()) {
            if (!upgrade.supports(player.hero)) continue;
            var card = world.progression.preview(0, upgrade);
            assertTrue(card.headline().length() <= 40, upgrade + " headline too long");
            assertTrue(card.before().length() <= 40, upgrade + " before too long");
            assertTrue(card.after().length() <= 40, upgrade + " after too long");
        }
    }

    @Test void menuDamagePreviewsExcludeTransientEquipmentConditions() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        var player = world.players[0];
        world.equipment.equip(0, 1, ItemCatalog.byId("last_light_charm"));
        var healthy = world.progression.preview(0, SENTINEL);
        player.health = 10;
        assertTrue(world.equipment.damageMultiplier(player)
            > world.equipment.baseDamageMultiplier(player, player.damageMultiplier));
        assertEquals(healthy, world.progression.preview(0, SENTINEL));
        assertTrue(world.progression.preview(0, CONSECRATION).after().startsWith("11 DMG"));
        assertTrue(world.progression.preview(0, GLAIVE).after().startsWith("20 DMG"));
    }

    @Test void reconnectKeepsExpandedRanksAndPendingIdentityWithoutAnotherChoiceOrHeal() {
        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        world.progression.addXp(6); assertChosen(world, RICOCHET);
        world.progression.addXp(10); world.progression.update(7);
        var offer = world.progression.offers[0]; int id = offer.id;
        world.players[0].health = 61;
        world.activatePlayer(0);
        assertEquals(1, world.players[0].ricochetLevel);
        assertEquals(id, offer.id); assertEquals(23, offer.secondsLeft);
        assertEquals(61, world.players[0].health);
    }

    private static void assertRejected(ArenaWorld world, PartyProgression.Upgrade upgrade) {
        var offer = world.progression.offers[0];
        int count = world.progression.appliedUpgradeCount(0), oldId = offer.id;
        offer.options[0] = upgrade;
        assertFalse(world.progression.choose(0, oldId, 0));
        assertEquals(count, world.progression.appliedUpgradeCount(0));
        assertNotEquals(oldId, offer.id);
    }
    private static void assertChosen(ArenaWorld world, PartyProgression.Upgrade upgrade) {
        var offer = world.progression.offers[0]; offer.options[0] = upgrade;
        assertTrue(world.progression.choose(0, offer.id, 0), upgrade.toString());
    }
    private static ArenaWorld solo(ArenaWorld.Hero hero) {
        ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0); return world;
    }
}
