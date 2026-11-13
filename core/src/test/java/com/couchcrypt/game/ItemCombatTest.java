package com.couchcrypt.game;

import java.util.List;
import org.junit.jupiter.api.Test;
import static com.couchcrypt.game.ItemCatalog.Stat.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemCombatTest {
    @Test void weaponGearChangesDamageCadenceAndArrowsWithoutIncreasingPools() {
        ArenaWorld world = new ArenaWorld(); world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        var player = world.players[0];
        world.equipment.equip(0, 0, ItemCatalog.byId("fletchers_hood"));
        world.equipment.equip(0, 2, ItemCatalog.byId("quickpulse_loop"));
        enemy(world, 0, 300, 100);
        world.update(ArenaWorld.STEP);
        assertEquals(2, world.arrowsFired);
        assertEquals(16 * .92f, world.bolts[0].damage, .0001f);
        assertEquals(.55f / 1.1f, player.attackCooldown, .0001f);
        assertEquals(1, player.projectileCount); assertEquals(1, player.attackRateMultiplier);
        assertEquals(ArenaWorld.MAX_BOLTS, world.bolts.length);
    }

    @Test void armorRetaliatesOnlyOnContactAndHealingNeverRevivesAFallenHero() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        var player = world.players[0]; player.attackCooldown = 100;
        world.equipment.equip(0, 4, ItemCatalog.byId("briar_mail"));
        var enemy = enemy(world, 0, 30, 100); enemy.attackCooldown = 100;
        player.invulnerable = 0; float before = player.health;
        world.update(ArenaWorld.STEP);
        assertEquals(before - 12 * .93f, player.health, .001f);
        assertEquals(88, enemy.health, .001f);
        world.equipment.equip(0, 1, ItemCatalog.byId("bloodglass_talisman"));
        player.alive = false; player.health = 0;
        world.equipment.drain(player, 1000); world.equipment.killHeal(player);
        assertEquals(0, player.health); assertFalse(player.alive);
    }

    @Test void conditionalPowerAndRegenerationUseLiveSimulationState() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); var player = world.players[0];
        world.equipment.equip(0, 1, ItemCatalog.byId("last_light_charm"));
        world.equipment.equip(0, 5, ItemCatalog.byId("marching_spurs"));
        player.health = 30;
        assertEquals(1.22f, world.equipment.damageMultiplier(player), .0001f);
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertEquals(1.16f, world.equipment.attackRate(player), .0001f);
        world.equipment.equip(0, 1, ItemCatalog.byId("emberheart_pendant"));
        float health = player.health;
        world.update(.1f);
        assertEquals(health + .025f, player.health, .0001f);
        world.equipment.grantReward(); float x = player.x;
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertEquals(x, player.x); assertEquals(0, player.dodgeTime);
    }

    @Test void lifeStealAndKillHealingShareBoundedPerSecondBudget() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); var player = world.players[0];
        world.equipment.equip(0, 1, ItemCatalog.byId("bloodglass_talisman"));
        world.equipment.equip(0, 2, ItemCatalog.byId("gravebloom_band"));
        player.health = 20;
        for (int i = 0; i < 100; i++) { world.equipment.drain(player, 1000); world.equipment.killHeal(player); }
        assertEquals(22, player.health, .0001f);
        world.equipment.update(1);
        for (int i = 0; i < 100; i++) world.equipment.drain(player, 1000);
        assertEquals(24, player.health, .0001f);
    }

    @Test void allEffectiveStatsHaveHardBoundsAndXpFractionsAreConserved() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); var player = world.players[0];
        world.equipment.equip(0, 0, item("caps", DAMAGE, 100, ATTACK_SPEED, 100, DAMAGE_REDUCTION, 100));
        world.equipment.equip(0, 1, item("defense", REGENERATION, 100, BLOCK_CHANCE, 100, CRIT_DAMAGE, 100));
        world.equipment.equip(0, 2, item("ranged", PROJECTILES, 100, PIERCE, 100, XP_GAIN, 100));
        assertEquals(1.6f, world.equipment.damageMultiplier(player), .0001f);
        assertEquals(1.5f, world.equipment.attackRate(player), .0001f);
        assertEquals(.4f, world.equipment.damageReduction(player), .0001f);
        assertEquals(.2f, world.equipment.blockChance(player), .0001f);
        assertEquals(1.5f, world.equipment.regeneration(player), .0001f);
        assertEquals(2.75f, world.equipment.critDamage(player), .0001f);
        assertEquals(5, world.equipment.projectileCount(player)); assertEquals(3, world.equipment.pierce(player));
        int xp = 0; for (int i = 0; i < 100; i++) xp += world.equipment.collectedXp(player, 1);
        assertEquals(125, xp);
    }

    @Test void secondaryDamageReachAndPickupGearChangeTheirActualCombatPaths() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); var player = world.players[0];
        player.attackCooldown = 100; player.auraLevel = player.stormLevel = 1; player.stormCooldown = 100;
        world.equipment.equip(0, 1, item("secondary", AURA_DAMAGE, .5f, STORM_DAMAGE, .5f, DAMAGE, .1f));
        var close = enemy(world, 0, 70, 100);
        world.update(ArenaWorld.STEP);
        assertEquals(100 - 7 * 1.1f * 1.5f, close.health, .001f);
        close.active = false; world.activeEnemies = 0;
        var distant = enemy(world, 1, 300, 100); player.auraCooldown = 100; player.stormCooldown = 0;
        world.update(ArenaWorld.STEP);
        assertEquals(100 - 17 * 1.1f * 1.5f, distant.health, .001f);
        world.reset(); world.activatePlayer(0); player = world.players[0];
        world.equipment.equip(0, 6, ItemCatalog.byId("farreach_gauntlets"));
        var extended = enemy(world, 0, 175, 100);
        world.update(ArenaWorld.STEP);
        assertEquals(100 - 28 * 1.05f, extended.health, .001f);
        world.equipment.equip(0, 1, ItemCatalog.byId("hunters_compass"));
        var soul = world.pickups[0]; soul.active = true; soul.x = 250; soul.y = 0; soul.value = 1;
        world.update(ArenaWorld.STEP);
        assertTrue(soul.x < 250, "Attraction reaches the soul outside the base 200-unit radius");
    }

    @Test void criticalEquipmentAffectsActualCriticalHitsAndLateHealingIsReduced() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); var player = world.players[0];
        player.critChance = .5f;
        world.equipment.equip(0, 0, ItemCatalog.byId("duelist_visor"));
        var target = enemy(world, 0, 100, 100000);
        boolean critical = false;
        for (int i = 0; i < 30; i++) {
            player.attackCooldown = 0; target.x = 100; float before = player.damageDealt;
            world.update(ArenaWorld.STEP);
            if (player.damageDealt - before > 40) {
                assertEquals(28 * 2.12f, player.damageDealt - before, .001f); critical = true;
            }
        }
        assertTrue(critical);
        for (boolean late : new boolean[]{false, true}) {
            ArenaWorld full = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES); full.activatePlayer(0);
            full.elapsed = late ? 181 : 179; full.kills = late ? 11 : 6;
            enemy(full, 0, 30, 1);
            full.update(ArenaWorld.STEP);
            boolean healing = false;
            for (var pickup : full.pickups) if (pickup.active && pickup.healing) {
                assertEquals(late ? 16 : 24, pickup.value); healing = true;
            }
            assertTrue(healing);
        }
    }

    private static ItemCatalog.Item item(String id, ItemCatalog.Stat a, float av, ItemCatalog.Stat b, float bv, ItemCatalog.Stat c, float cv) {
        return new ItemCatalog.Item(id, id, ItemCatalog.Slot.HELMET, 0, "Fixture", List.of(
            new ItemCatalog.Modifier(a, av), new ItemCatalog.Modifier(b, bv), new ItemCatalog.Modifier(c, cv)));
    }
    private static ArenaWorld.Enemy enemy(ArenaWorld world, int index, float x, float hp) {
        var enemy = world.enemies[index]; enemy.active = true; enemy.x = x; enemy.y = 0;
        enemy.health = enemy.maxHealth = hp; world.activeEnemies++; return enemy;
    }
}
