package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaTimelineTest {
    @Test void timelineBoundariesAndBossUseConfiguredDuration() {
        ArenaTimeline timeline = ArenaTimeline.TWENTY_MINUTES;
        assertEquals(1200, timeline.durationSeconds());
        assertEquals(6, timeline.phaseCount());
        assertEquals(0, timeline.phaseIndex(199.9f));
        assertEquals(1, timeline.phaseIndex(200));
        assertEquals(5, timeline.phaseIndex(1200));
        ArenaWorld world = solo(timeline);
        world.elapsed = 180;
        world.update(ArenaWorld.STEP);
        assertFalse(world.bossSpawned);
        assertTrue(world.timeRemaining() > 1000);
        world.elapsed = timeline.durationSeconds() - ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertTrue(world.bossSpawned);
        assertEquals(6, world.wave);
        assertEquals(0, world.timeRemaining());
        assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
    }

    @Test void checkpointsBeyondThirdPhaseAreSingleUseAndResetWithRun() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.elapsed = 600; world.update(ArenaWorld.STEP);
        assertEquals(4, world.wave);
        assertEquals(2, elites(world));
        world.update(ArenaWorld.STEP);
        assertEquals(2, elites(world));
        world.elapsed = 1000; world.update(ArenaWorld.STEP);
        assertEquals(6, world.wave);
        assertEquals(3, elites(world));
        world.reset(); world.activatePlayer(0);
        world.elapsed = 600; world.update(ArenaWorld.STEP);
        assertEquals(2, elites(world));
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
