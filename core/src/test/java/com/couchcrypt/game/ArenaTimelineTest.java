package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaTimelineTest {
    @Test void timelineBoundariesAndBossUseConfiguredDuration() {
        ArenaTimeline timeline = ArenaTimeline.TWENTY_MINUTES;
        assertEquals(1200, timeline.durationSeconds());
        assertEquals(12, timeline.phaseCount());
        assertEquals(0, timeline.phaseIndex(89.9f));
        assertEquals(1, timeline.phaseIndex(90));
        assertEquals(11, timeline.phaseIndex(1200));
        ArenaWorld world = solo(timeline);
        world.elapsed = 180;
        world.update(ArenaWorld.STEP);
        assertFalse(world.bossSpawned);
        assertTrue(world.timeRemaining() > 1000);
        world.elapsed = timeline.durationSeconds() - ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertTrue(world.bossSpawned);
        assertEquals(12, world.wave);
        assertEquals(0, world.timeRemaining());
        assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
    }

    @Test void checkpointsBeyondThirdPhaseAreSingleUseAndResetWithRun() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.elapsed = 660; world.update(ArenaWorld.STEP);
        assertEquals(7, world.wave);
        assertEquals(3, elites(world));
        world.update(ArenaWorld.STEP);
        assertEquals(3, elites(world));
        world.elapsed = 1080; world.update(ArenaWorld.STEP);
        assertEquals(11, world.wave);
        assertEquals(5, elites(world));
        world.reset(); world.activatePlayer(0);
        world.elapsed = 660; world.update(ArenaWorld.STEP);
        assertEquals(3, elites(world));
    }

    @Test void authoredPacksHaveDistinctCompositionsAndKeepEliteSlotsFree() {
        for (ArenaTimeline.Pattern pattern : ArenaTimeline.Pattern.values()) {
            ArenaWorld world = solo(new ArenaTimeline(new ArenaTimeline.Phase(10, pattern, 2, 18, 3, null)));
            advanceToFirstPack(world);
            assertEquals(pattern.size(), world.activeEnemies);
            for (int i = 0; i < pattern.size(); i++) assertEquals(pattern.member(i), world.enemies[i].kind);
        }
        ArenaWorld full = solo(ArenaTimeline.SHORT);
        for (int i = 0; i < ArenaWorld.MAX_ENEMIES - 2; i++) {
            ArenaWorld.Enemy enemy = full.enemies[i];
            enemy.active = true; enemy.health = enemy.maxHealth = 10000; enemy.x = 1000; enemy.y = 800;
        }
        full.activeEnemies = ArenaWorld.MAX_ENEMIES - 2;
        for (int i = 0; i < 8; i++) full.update(.1f);
        assertEquals(ArenaWorld.MAX_ENEMIES - 2, full.activeEnemies);
    }

    @Test void breathersPauseReinforcementsWithoutCatchupBurst() {
        ArenaWorld world = solo(new ArenaTimeline(new ArenaTimeline.Phase(10, ArenaTimeline.Pattern.SWARM, 2, 2, 1, null)));
        world.elapsed = 1.1f; world.update(.1f);
        assertEquals(0, world.activeEnemies);
        world.elapsed = 2; advanceToFirstPack(world);
        assertEquals(3, world.activeEnemies);
    }

    @Test void packsSpawnClearOfBothLivingPlayersIncludingArenaCorners() {
        ArenaWorld world = new ArenaWorld();
        for (int sample = 0; sample < 8; sample++) {
            world.reset(); world.activatePlayer(0); world.activatePlayer(1);
            world.players[0].x = 1180; world.players[0].y = 880;
            world.players[1].x = 560; world.players[1].y = 880;
            for (ArenaWorld.Player player : world.players) player.attackCooldown = 1000;
            advanceToFirstPack(world);
            for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active) {
                for (ArenaWorld.Player player : world.players)
                    assertTrue(Math.hypot(enemy.x - player.x, enemy.y - player.y) >= 300);
                assertTrue(Math.abs(enemy.x) <= ArenaWorld.HALF_WIDTH - enemy.radius);
                assertTrue(Math.abs(enemy.y) <= ArenaWorld.HALF_HEIGHT - enemy.radius);
            }
        }
    }

    @Test void authoredFullRunHasEarlyMiddleLateEscalationAndFiveCheckpoints() {
        ArenaTimeline timeline = ArenaTimeline.TWENTY_MINUTES;
        int checkpoints = 0;
        for (int i = 0; i < timeline.phaseCount(); i++) {
            ArenaTimeline.Phase phase = timeline.phase(i);
            assertFalse(phase.name().isBlank());
            assertTrue(phase.breatherSeconds() >= 3 && phase.breatherSeconds() <= 5);
            assertTrue(phase.soloEnemyLimit() <= ArenaWorld.MAX_ENEMIES - 2);
            if (phase.eliteKind() != null) checkpoints++;
        }
        assertEquals(5, checkpoints);
        assertTrue(timeline.phase(0).healthMultiplier() < timeline.phase(6).healthMultiplier());
        assertTrue(timeline.phase(6).healthMultiplier() < timeline.phase(11).healthMultiplier());
        assertEquals(180, ArenaTimeline.SHORT.durationSeconds());
    }

    @Test void blockedCheckpointRetriesOnceRoomIsAvailable() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        for (ArenaWorld.Enemy enemy : world.enemies) {
            enemy.active = true; enemy.health = enemy.maxHealth = 1000;
            enemy.x = 900; enemy.y = 800;
        }
        world.activeEnemies = ArenaWorld.MAX_ENEMIES;
        world.elapsed = 180; world.update(ArenaWorld.STEP);
        assertEquals(0, elites(world));
        world.enemies[0].active = false; world.activeEnemies--;
        world.update(ArenaWorld.STEP);
        assertEquals(1, elites(world));
        world.update(ArenaWorld.STEP);
        assertEquals(1, elites(world));
        assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
    }

    @Test void lateFixtureHasBoundedSoloCoopPressureAndSnapshotStats() {
        ArenaWorld solo = solo(ArenaTimeline.TWENTY_MINUTES);
        solo.debugStartLateEncounter();
        assertEquals(46, solo.activeEnemies);
        ArenaWorld coop = solo(ArenaTimeline.TWENTY_MINUTES);
        coop.activatePlayer(1); coop.players[1].attackCooldown = 1000;
        coop.debugStartLateEncounter();
        assertEquals(58, coop.activeEnemies);
        assertEquals(1, elites(coop));
        assertEquals(solo.enemies[1].health * 1.2f, coop.enemies[1].health, .001f);
        assertEquals(100, solo.enemies[1].soulPercent);
        assertEquals(82, coop.enemies[1].soulPercent);
        coop.players[1].alive = false;
        assertEquals(46, coop.reinforcementLimit());
        assertEquals(82, coop.enemies[1].soulPercent);
        assertFalse(coop.bossSpawned);
        assertTrue(coop.elapsed < 1200);
    }

    @Test void coopSoulRoundingIsConservativeAndExactAcrossManySmallRewards() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(1); world.players[1].attackCooldown = 1000;
        world.elapsed = 20; // A short reinforcement breather, not a survival playthrough.
        for (int i = 0; i < 100; i++) {
            ArenaWorld.Enemy enemy = world.enemies[0];
            enemy.active = true; enemy.health = 1; enemy.x = 30; enemy.y = 0;
            enemy.kind = ArenaWorld.EnemyKind.GHOUL; enemy.soulPercent = 82;
            world.activeEnemies = 1; world.players[0].attackCooldown = 0;
            world.update(ArenaWorld.STEP);
        }
        assertEquals(82, world.souls);
        assertEquals(82, world.progression.totalXp);
    }

    @Test void lateJoiningDoesNotRevalueExistingSouls() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        ArenaWorld.Pickup soul = world.pickups[0];
        soul.active = true; soul.value = 9; soul.x = 0; soul.y = 0;
        world.activatePlayer(1);
        world.update(ArenaWorld.STEP);
        assertEquals(9, world.souls);
        assertEquals(9, world.progression.totalXp);
    }

    private static ArenaWorld solo(ArenaTimeline timeline) {
        ArenaWorld world = new ArenaWorld(timeline); world.activatePlayer(0);
        world.players[0].attackCooldown = 1000; return world;
    }

    private static void advanceToFirstPack(ArenaWorld world) {
        for (int i = 0; i < 60 && world.activeEnemies == 0; i++) world.update(ArenaWorld.STEP);
        assertTrue(world.activeEnemies > 0);
    }

    private static int elites(ArenaWorld world) {
        int count = 0;
        for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active && enemy.elite) count++;
        return count;
    }
}
