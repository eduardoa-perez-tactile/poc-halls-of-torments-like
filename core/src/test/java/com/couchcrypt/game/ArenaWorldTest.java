package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaWorldTest {
    @Test void normalizesDiagonalInputAndBoundsCatchup() {
        ArenaWorld world = solo();
        world.setInput(0, 1f, 1f, false);
        world.update(20f);
        assertEquals(.1f, world.elapsed, .00001f);
        assertEquals(18f, Math.hypot(world.players[0].x, world.players[0].y), .001f);
        world.players[0].x = ArenaWorld.HALF_WIDTH - 20f;
        world.setInput(0, 1f, 0f, false);
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.HALF_WIDTH - 20f, world.players[0].x);
    }

    @Test void retainsDodgeRequestUntilTickAndHonorsCooldown() {
        ArenaWorld world = solo();
        world.setInput(0, 1f, 0f, true);
        world.setInput(0, 1f, 0f, false);
        world.update(ArenaWorld.STEP);
        ArenaWorld.Player player = world.players[0];
        assertEquals(9f, player.x, .001f);
        assertTrue(player.invulnerable > 0f);
        assertTrue(player.dodgeCooldown > 2f);
        player.dodgeTime = 0f;
        world.setInput(0, 1f, 0f, true);
        world.update(ArenaWorld.STEP);
        assertEquals(12f, player.x, .001f);
        assertEquals(0f, player.dodgeTime);
    }

    @Test void pauseDiscardsDodgeWaitingForFixedTick() {
        ArenaWorld world = solo();
        world.setInput(0, 1, 0, true);
        world.update(.001f);
        world.clearInputs();
        world.setInput(0, 1, 0, false);
        world.update(ArenaWorld.STEP);
        assertEquals(0, world.players[0].dodgeCooldown);
        assertEquals(3, world.players[0].x, .001f);
    }

    @Test void rejectsNonFiniteInputAndPreservesAnalogMagnitude() {
        ArenaWorld world = solo();
        world.setInput(0, Float.NaN, 1f, false);
        world.update(ArenaWorld.STEP);
        assertEquals(0f, world.players[0].x);
        assertEquals(0f, world.players[0].y);
        world.setInput(0, .5f, 0f, false);
        world.update(ArenaWorld.STEP);
        assertEquals(1.5f, world.players[0].x, .001f);
        world.update(Float.POSITIVE_INFINITY);
        assertTrue(Float.isFinite(world.elapsed));
    }

    @Test void lateJoinSpawnsNearLivingAllyWithoutResettingExistingPlayer() {
        ArenaWorld world = solo();
        world.players[0].x = 400f;
        world.players[0].y = -200f;
        world.activatePlayer(1);
        ArenaWorld.Player second = world.players[1];
        assertTrue(second.active && second.alive);
        assertEquals(64f, second.x - world.players[0].x, .001f);
        assertEquals(world.players[0].y, second.y);
        assertEquals(second.maxHealth, second.health);
        second.health = 37f;
        world.activatePlayer(1);
        assertEquals(37f, second.health);
    }

    @Test void autoAttackKillsCollectsSoulAndTracksOwner() {
        ArenaWorld world = solo();
        enemy(world, 30f, 0f, 12f);
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.kills);
        assertEquals(1, world.players[0].kills);
        assertEquals(1, world.souls);
        assertEquals(0, world.activeEnemies);
        assertFalse(world.enemies[0].active);
    }

    @Test void fullPickupPoolMergesSoulRewards() {
        ArenaWorld world = solo();
        for (ArenaWorld.Pickup p : world.pickups) {
            p.active = true; p.value = 1; p.x = -1000f; p.y = -800f;
        }
        enemy(world, 30f, 0f, 12f);
        world.update(ArenaWorld.STEP);
        int rewards = world.souls;
        for (ArenaWorld.Pickup p : world.pickups) if (p.active && !p.healing) rewards += p.value;
        assertEquals(ArenaWorld.MAX_PICKUPS + 1, rewards);
    }

    @Test void contactDamageUsesInvulnerabilityAndAllDownEndsRound() {
        ArenaWorld world = solo();
        ArenaWorld.Player player = world.players[0];
        player.health = 20f; player.invulnerable = 0f; player.attackCooldown = 10f;
        enemy(world, 20f, 0f, 100f);
        world.update(ArenaWorld.STEP);
        assertEquals(8f, player.health);
        world.update(ArenaWorld.STEP);
        assertEquals(8f, player.health);
        player.invulnerable = 0f;
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.Outcome.DEFEAT, world.outcome);
        assertFalse(player.alive);
    }

    @Test void nearbyAllyRevivesAfterTwoSecondsAndDistanceBreaksProgress() {
        ArenaWorld world = solo();
        world.activatePlayer(1);
        ArenaWorld.Player down = world.players[0];
        down.alive = false; down.health = 0f; down.reviveProgress = 1f;
        world.players[1].x = 300f;
        world.update(ArenaWorld.STEP);
        assertEquals(0f, down.reviveProgress);
        world.players[1].x = 50f;
        down.reviveProgress = 2f - ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertTrue(down.alive);
        assertEquals(45f, down.health);
        assertTrue(down.invulnerable > 1f);
    }

    @Test void survivalBoundaryFinishesWithoutSimulatingWholeRoundAndResetReusesPools() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy pooled = world.enemies[0];
        world.elapsed = ArenaWorld.ROUND_SECONDS - ArenaWorld.STEP;
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.Outcome.VICTORY, world.outcome);
        assertEquals(0f, world.timeRemaining());
        world.reset();
        assertSame(pooled, world.enemies[0]);
        assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
        assertFalse(world.players[0].active);
        world.update(.1f);
        assertEquals(0f, world.elapsed);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world;
    }
    private static void enemy(ArenaWorld world, float x, float y, float health) {
        ArenaWorld.Enemy enemy = world.enemies[0];
        enemy.active = true; enemy.x = x; enemy.y = y; enemy.health = enemy.maxHealth = health;
        world.activeEnemies = 1;
    }
}
