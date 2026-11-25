package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaWorldDiagnosticsTest {
    @Test void countsMeleeChecksSeparatelyAndReportsItsIndependentPoolSaturation() {
        ArenaWorld world = solo(); world.setDiagnosticsEnabled(true);
        enemy(world, 0, 30, 1000);
        world.update(ArenaWorld.STEP);
        assertTrue(world.diagnostics().meleeCollisionCandidates > 0);
        assertEquals(0, world.diagnostics().boltCollisionCandidates);
        assertEquals(0, world.diagnostics().expansionCollisionCandidates);
        world.reset(); world.activatePlayer(0); enemy(world, 0, 30, 1000);
        for (int i = 0; i < MeleeWave.PER_PLAYER; i++) {
            world.meleeWaves[i].active = true; world.meleeWaves[i].owner = 0;
        }
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.diagnostics().meleeAcquireMisses);
        assertEquals(0, world.swordSwings, "Saturation cannot create another wave");
        world.reset();
        assertEquals(0, world.diagnostics().meleeAcquireMisses);
        assertEquals(0, world.diagnostics().meleeCollisionCandidates);
    }

    @Test void expansionCountingIsOptInAndDoesNotChangeCombatResults() {
        ArenaWorld measured = expansionFixture(), ordinary = expansionFixture();
        measured.setDiagnosticsEnabled(true);
        measured.update(ArenaWorld.STEP); ordinary.update(ArenaWorld.STEP);
        ArenaWorld.Diagnostics counters = measured.diagnostics();
        assertTrue(counters.expansionCollisionCandidates > 0);
        assertEquals(0, counters.boltCollisionCandidates);
        assertEquals(ordinary.enemies[0].health, measured.enemies[0].health);
        assertEquals(ordinary.players[0].damageDealt, measured.players[0].damageDealt);
        long candidates = counters.expansionCollisionCandidates;
        measured.setDiagnosticsEnabled(false);
        measured.update(ArenaWorld.STEP); ordinary.update(ArenaWorld.STEP);
        assertEquals(candidates, counters.expansionCollisionCandidates);
        assertEquals(ordinary.enemies[0].health, measured.enemies[0].health);
        measured.setDiagnosticsEnabled(true); measured.reset();
        assertEquals(0, counters.expansionCollisionCandidates);
    }

    @Test void delayedStrikeSaturationRetainsFixedStorageAndReportsMisses() {
        ArenaWorld world = solo();
        SkillExpansion.DelayedStrike first = world.skills.delayed[0];
        for (int i = 0; i <= SkillExpansion.MAX_DELAYED; i++) world.skills.echo(0, 0, 0, 10);
        assertEquals(1, world.skills.delayedAcquireMisses);
        assertEquals(SkillExpansion.MAX_DELAYED, world.skills.delayed.length);
        assertSame(first, world.skills.delayed[0]);
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) assertTrue(strike.active);
        world.reset();
        assertEquals(0, world.skills.delayedAcquireMisses);
    }

    @Test void relicDiagnosticsDistinguishFullPoolFromFailedPlacementAndStopWhenDisabled() {
        ArenaWorld world = solo(); world.setDiagnosticsEnabled(true);
        world.elapsed = world.durationSeconds() * .14f;
        for (SkillExpansion.WorldRelic relic : world.skills.relics) {
            relic.active = true; relic.x = 700;
        }
        world.skills.update();
        assertEquals(1, world.diagnostics().relicPoolBlockedTicks);
        assertEquals(0, world.diagnostics().relicPlacementMisses);
        for (SkillExpansion.WorldRelic relic : world.skills.relics) relic.active = false;
        // Cover every candidate position in the 240–500-unit placement annulus.
        for (int i = 0; i < world.enemies.length; i++) {
            enemy(world, i, -437.5f + i % 8 * 125, 1000);
            world.enemies[i].y = -437.5f + i / 8 * 125;
        }
        world.skills.update();
        ArenaWorld.Diagnostics counters = world.diagnostics();
        assertEquals(1, counters.relicPlacementMisses);
        assertEquals(0, world.skills.relicsPlaced);
        world.setDiagnosticsEnabled(false); world.skills.update();
        assertEquals(1, counters.relicPlacementMisses);
        world.setDiagnosticsEnabled(true); world.reset();
        assertEquals(0, counters.relicPoolBlockedTicks);
        assertEquals(0, counters.relicPlacementMisses);
    }

    @Test void stepCountDescribesOnlyTheLatestUpdateIncludingEarlyReturns() {
        ArenaWorld world = solo();
        world.update(20);
        assertEquals(6, world.lastUpdateSteps);
        world.update(0);
        assertEquals(0, world.lastUpdateSteps);
        world.update(Float.NaN);
        assertEquals(0, world.lastUpdateSteps);
        world.update(ArenaWorld.STEP / 2);
        assertEquals(0, world.lastUpdateSteps);
        world.update(ArenaWorld.STEP / 2);
        assertEquals(1, world.lastUpdateSteps);
        world.outcome = ArenaWorld.Outcome.DEFEAT;
        world.update(ArenaWorld.STEP);
        assertEquals(0, world.lastUpdateSteps);
        world.reset();
        world.update(ArenaWorld.STEP);
        assertEquals(0, world.lastUpdateSteps, "An empty world does not simulate");
    }

    @Test void countsGeometricCandidatesButSkipsInactiveAndAlreadyHitEnemies() {
        ArenaWorld world = solo();
        world.setDiagnosticsEnabled(true);
        enemy(world, 0, 400, 1000);
        enemy(world, 2, 500, 1000);
        ArenaWorld.Bolt bolt = world.bolts[0];
        bolt.active = true; bolt.ttl = 10; bolt.hitMask = 1L << 2;
        world.players[0].attackCooldown = 10;
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.diagnostics().boltCollisionCandidates);
        assertEquals(0, world.boltHits);

        ArenaWorld.Diagnostics counters = world.diagnostics();
        world.setDiagnosticsEnabled(false);
        assertNull(world.diagnostics());
        world.update(ArenaWorld.STEP);
        assertEquals(1, counters.boltCollisionCandidates);
        world.setDiagnosticsEnabled(true);
        assertSame(counters, world.diagnostics());
        world.update(ArenaWorld.STEP);
        assertEquals(2, counters.boltCollisionCandidates);
        world.reset();
        assertSame(counters, world.diagnostics());
        assertEquals(0, counters.boltCollisionCandidates);
        assertEquals(0, world.lastUpdateSteps);
    }

    @Test void reportsFullBoltPoolWithoutCreatingAnExtraProjectile() {
        ArenaWorld world = new ArenaWorld();
        world.setHero(0, ArenaWorld.Hero.RANGER); world.activatePlayer(0);
        world.setDiagnosticsEnabled(true);
        enemy(world, 0, 400, 1000);
        for (ArenaWorld.Bolt bolt : world.bolts) {
            bolt.active = true; bolt.x = -1000; bolt.y = -800; bolt.ttl = 10;
        }
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.diagnostics().boltAcquireMisses);
        assertEquals(0, world.arrowsFired);
        assertEquals(1000, world.enemies[0].health);
    }

    @Test void reportsFullEffectsAndPickupMergingWithoutChangingRewardsOrDamage() {
        ArenaWorld measured = saturatedRewardFixture(true);
        ArenaWorld ordinary = saturatedRewardFixture(false);
        measured.update(ArenaWorld.STEP); ordinary.update(ArenaWorld.STEP);
        assertEquals(2, measured.diagnostics().effectAcquireMisses,
            "Contact and death cannot acquire effects; the primary wave has its own pool");
        assertTrue(measured.meleeWaves[0].active, "Effect saturation must not suppress the separate primary wave");
        assertEquals(1, measured.diagnostics().pickupMerges);
        assertEquals(1, measured.kills);
        assertEquals(2, measured.pickups[0].value);
        assertEquals(ordinary.kills, measured.kills);
        assertEquals(ordinary.players[0].damageDealt, measured.players[0].damageDealt);
        assertEquals(ordinary.players[0].attackCooldown, measured.players[0].attackCooldown);
        assertEquals(ordinary.pickups[0].value, measured.pickups[0].value);
        measured.reset();
        assertEquals(0, measured.diagnostics().effectAcquireMisses);
        assertEquals(0, measured.diagnostics().pickupMerges);
    }

    @Test void reportsFullHostilePoolsAtTheSovereignAttack() {
        ArenaWorld world = solo(); world.setDiagnosticsEnabled(true); world.debugStartSovereign();
        world.players[0].attackCooldown = 10; world.sovereign.attackCooldown = 0;
        for (ArenaWorld.Shot shot : world.shots) { shot.active = true; shot.x = -1000; shot.y = -800; shot.ttl = 10; }
        for (ArenaWorld.Hazard hazard : world.hazards) { hazard.active = true; hazard.warning = 10; hazard.ttl = 11; }
        world.update(ArenaWorld.STEP);
        assertEquals(5, world.diagnostics().shotAcquireMisses);
        assertEquals(1, world.diagnostics().hazardAcquireMisses);
        assertEquals(0, world.hostileShotsFired);
    }

    @Test void reportsFullEnemyPoolWhenARequiredEliteCannotSpawn() {
        ArenaWorld world = solo(); world.setDiagnosticsEnabled(true);
        world.elapsed = 60; world.players[0].attackCooldown = 10;
        for (int i = 0; i < world.enemies.length; i++) enemy(world, i, 800, 1000);
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.diagnostics().enemySpawnMisses);
        assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        world.reset();
        assertEquals(0, world.diagnostics().enemySpawnMisses);
    }

    @Test void profilingLeavesShortDeterministicCombatUnchanged() {
        ArenaWorld measured = solo(), ordinary = solo();
        measured.setDiagnosticsEnabled(true);
        measured.debugVfxEncounter(); ordinary.debugVfxEncounter();
        for (int tick = 0; tick < 45; tick++) {
            measured.setInput(0, .4f, -.2f, tick == 2);
            ordinary.setInput(0, .4f, -.2f, tick == 2);
            measured.update(ArenaWorld.STEP); ordinary.update(ArenaWorld.STEP);
            assertEquals(ordinary.elapsed, measured.elapsed);
            assertEquals(ordinary.outcome, measured.outcome);
            assertEquals(ordinary.kills, measured.kills);
            assertEquals(ordinary.souls, measured.souls);
            assertEquals(ordinary.players[0].x, measured.players[0].x);
            assertEquals(ordinary.players[0].y, measured.players[0].y);
            assertEquals(ordinary.players[0].health, measured.players[0].health);
            assertEquals(ordinary.players[0].damageDealt, measured.players[0].damageDealt);
            for (int i = 0; i < measured.enemies.length; i++) {
                assertEquals(ordinary.enemies[i].active, measured.enemies[i].active);
                assertEquals(ordinary.enemies[i].x, measured.enemies[i].x);
                assertEquals(ordinary.enemies[i].y, measured.enemies[i].y);
                assertEquals(ordinary.enemies[i].health, measured.enemies[i].health);
            }
        }
    }

    private static ArenaWorld saturatedRewardFixture(boolean diagnostics) {
        ArenaWorld world = solo(); world.setDiagnosticsEnabled(diagnostics);
        enemy(world, 0, 30, 10);
        for (ArenaWorld.Effect effect : world.effects) {
            effect.active = true; effect.kind = ArenaWorld.EffectKind.AURA; effect.ttl = 10;
        }
        for (ArenaWorld.Pickup pickup : world.pickups) {
            pickup.active = true; pickup.value = 1; pickup.x = -1000; pickup.y = -800;
        }
        return world;
    }

    private static ArenaWorld solo() { ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world; }

    private static ArenaWorld expansionFixture() {
        ArenaWorld world = solo(); enemy(world, 0, 400, 1000);
        world.players[0].attackCooldown = 10;
        world.players[0].sentinelLevel = world.players[0].consecrationLevel = world.players[0].glaiveLevel = 1;
        return world;
    }

    private static void enemy(ArenaWorld world, int index, float x, float health) {
        ArenaWorld.Enemy enemy = world.enemies[index];
        enemy.active = true; enemy.x = x; enemy.health = enemy.maxHealth = health;
        world.activeEnemies++;
    }
}
