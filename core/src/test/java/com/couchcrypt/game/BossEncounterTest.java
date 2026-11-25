package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossEncounterTest {
    private static final float EPSILON = .001f;

    @Test void sovereignAtTwelveMinutesAndGraveKingAtTwentyAreIndependentSingleUseSpawns() {
        ArenaWorld world = solo();
        assertEquals(720, world.timeline.sovereignSeconds());
        assertEquals(108, ArenaTimeline.SHORT.sovereignSeconds(), EPSILON);
        world.elapsed = 720 - 2 * ArenaWorld.STEP; world.update(ArenaWorld.STEP);
        assertFalse(world.sovereignSpawned); assertFalse(world.bossSpawned);
        finishChoices(world);
        world.update(ArenaWorld.STEP);
        assertTrue(world.sovereignSpawned); assertFalse(world.bossSpawned);
        ArenaWorld.Enemy sovereign = world.sovereign;
        assertEquals(5400, sovereign.health); assertSame(sovereign, world.activeBoss());
        assertEquals("THE HOLLOW SOVEREIGN", ArenaWorld.bossName(sovereign));
        assertFalse(sovereign.elite);
        finishChoices(world);
        world.elapsed = 780; world.update(ArenaWorld.STEP);
        assertTrue(world.elapsed > 780); assertEquals(8, world.wave);
        assertSame(sovereign, world.sovereign); assertTrue(sovereign.active);
        finishChoices(world);
        world.elapsed = 1200 - ArenaWorld.STEP; world.update(ArenaWorld.STEP);
        assertTrue(world.bossSpawned); assertTrue(sovereign.active);
        assertSame(world.boss, world.activeBoss());
        assertEquals("THE GRAVE KING", ArenaWorld.bossName(world.boss));
        assertEquals(8200, world.boss.maxHealth); assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
        finishChoices(world);
        world.update(ArenaWorld.STEP);
        assertEquals(1, count(world, ArenaWorld.EnemyKind.SOVEREIGN));
        assertEquals(1, count(world, ArenaWorld.EnemyKind.BOSS));
    }

    @Test void minibossDeathContinuesRunWithoutGrantingAnotherEquipmentReward() {
        ArenaWorld world = solo(); world.debugStartSovereign();
        finishChoicesAfterTick(world);
        ArenaWorld.Enemy sovereign = world.sovereign;
        int rewards = world.equipment.rewardsEarned();
        sovereign.x = 80; sovereign.y = 0; sovereign.health = 1;
        world.players[0].attackCooldown = 0;
        for (int tick = 0; tick < 15 && sovereign.active; tick++) world.update(ArenaWorld.STEP);
        assertTrue(world.sovereignDefeated); assertFalse(sovereign.active);
        assertFalse(world.bossDefeated); assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
        assertNull(world.activeBoss()); assertEquals(rewards, world.equipment.rewardsEarned());
        finishChoices(world);
        world.update(ArenaWorld.STEP);
        assertEquals(0, count(world, ArenaWorld.EnemyKind.SOVEREIGN));
        world.elapsed = 1200; world.update(ArenaWorld.STEP); finishChoices(world);
        world.boss.x = 80; world.boss.y = 0; world.boss.health = 1;
        world.players[0].attackCooldown = 0;
        for (int tick = 0; tick < 15 && world.boss.active; tick++) world.update(ArenaWorld.STEP);
        assertTrue(world.bossDefeated); assertEquals(ArenaWorld.Outcome.VICTORY, world.outcome);
    }

    @Test void encountersScaleAtTheirOwnSpawnAndJoiningNeverRefillsThem() {
        ArenaWorld world = solo(); world.debugStartSovereign();
        world.sovereign.health -= 100;
        world.activatePlayer(1);
        assertEquals(5400, world.sovereign.maxHealth); assertEquals(5300, world.sovereign.health);
        world.debugStartBoss();
        assertEquals(13120, world.boss.maxHealth, EPSILON);
        world.boss.health -= 200; world.activatePlayer(1);
        assertEquals(12920, world.boss.health, EPSILON);
        world.reset(); world.activatePlayer(0); world.activatePlayer(1); world.debugStartSovereign();
        assertEquals(8640, world.sovereign.maxHealth, EPSILON);
    }

    @Test void bothBossesSpawnClearOfBothPlayersAndFinalNeverEvictsTheMiniboss() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        world.players[0].x = 1180; world.players[0].y = 880;
        world.players[1].x = 560; world.players[1].y = 880;
        world.debugStartSovereign();
        ArenaWorld.Enemy sovereign = world.sovereign;
        for (ArenaWorld.Enemy enemy : world.enemies) if (!enemy.active) {
            enemy.active = true; enemy.kind = ArenaWorld.EnemyKind.GHOUL;
            enemy.health = enemy.maxHealth = 10000; enemy.x = -900; enemy.y = -800;
            world.activeEnemies++;
        }
        assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        world.debugStartBoss(); assertTrue(world.bossSpawned);
        assertSame(sovereign, world.sovereign); assertTrue(sovereign.active);
        assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        for (ArenaWorld.Enemy enemy : new ArenaWorld.Enemy[]{sovereign, world.boss})
            for (ArenaWorld.Player player : world.players)
                assertTrue(Math.hypot(enemy.x - player.x, enemy.y - player.y) >= 300);
    }

    @Test void unavailableRequiredBossSlotRetriesWithoutDuplicatingOrRemovingElites() {
        ArenaWorld world = solo();
        for (ArenaWorld.Enemy enemy : world.enemies) {
            enemy.active = enemy.elite = true; enemy.health = enemy.maxHealth = 10000;
            enemy.x = -900; enemy.y = -800; world.activeEnemies++;
        }
        world.elapsed = 720; world.update(ArenaWorld.STEP);
        assertFalse(world.sovereignSpawned); assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        finishChoices(world);
        world.enemies[0].active = false; world.activeEnemies--;
        world.update(ArenaWorld.STEP);
        assertTrue(world.sovereignSpawned); assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        world.update(ArenaWorld.STEP);
        assertEquals(1, count(world, ArenaWorld.EnemyKind.SOVEREIGN));
    }

    @Test void graveKingAlternatesGroundStrikeAndDelayedLockedVolleyAndEnragesIndependently() {
        ArenaWorld world = solo(); world.debugStartSovereign(); world.debugStartBoss(); finishChoicesAfterTick(world);
        ArenaWorld.Enemy king = world.boss;
        world.sovereign.attackCooldown = 1000; // Isolate the final attack while the mini boss remains alive.
        king.attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(1, hazards(world)); assertEquals(0, shots(world));
        assertEquals(1, king.castDuration); assertEquals(1, king.releaseAge);
        assertEquals(3.4f, king.attackCooldown, EPSILON);
        float x = king.x, y = king.y;
        for (int tick = 0; tick < 61 && king.windup > 0; tick++) world.update(ArenaWorld.STEP);
        assertEquals(0, king.windup); assertEquals(x, king.x); assertEquals(y, king.y);
        assertEquals(0, shots(world));
        king.attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(.7f, king.castDuration); assertEquals(0, shots(world));
        float aimX = king.castFacingX, aimY = king.castFacingY;
        world.players[0].y += 100;
        for (int tick = 0; tick < 43 && king.windup > 0; tick++) world.update(ArenaWorld.STEP);
        assertEquals(5, shots(world)); assertEquals(0, king.releaseAge);
        for (ArenaWorld.Shot shot : world.shots) if (shot.active) {
            assertEquals(aimX, shot.launchFacingX); assertEquals(aimY, shot.launchFacingY);
            assertEquals(145, Math.hypot(shot.vx, shot.vy), EPSILON);
        }
        king.health = king.maxHealth * .4f; king.attackCooldown = 0;
        world.update(ArenaWorld.STEP);
        assertEquals(2, king.encounterPhase); assertEquals(2, world.bossPhase);
        assertEquals(2.4f, king.attackCooldown, EPSILON);
        assertEquals(1, world.sovereign.encounterPhase, "The healthy mini boss never inherits final enrage");
    }

    @Test void skillAndLootPausesFreezeSpawnBoundaryAndAnAlreadyPendingVolley() {
        ArenaWorld world = solo();
        world.elapsed = 720 - ArenaWorld.STEP;
        world.progression.addXp(world.progression.xpToNext);
        float elapsed = world.elapsed;
        world.update(.1f); assertEquals(elapsed, world.elapsed); assertFalse(world.sovereignSpawned);
        finishChoices(world); world.update(ArenaWorld.STEP); assertTrue(world.sovereignSpawned);
        finishChoices(world); world.debugStartBoss(); finishChoicesAfterTick(world);
        world.boss.bossAttackIndex = 1; world.boss.attackCooldown = 0;
        world.update(ArenaWorld.STEP);
        float windup = world.boss.windup, castAge = world.boss.castAge;
        int shots = shots(world); elapsed = world.elapsed;
        world.equipment.grantReward(); assertTrue(world.lootPending());
        world.update(.1f);
        assertEquals(elapsed, world.elapsed); assertEquals(windup, world.boss.windup);
        assertEquals(castAge, world.boss.castAge); assertEquals(shots, shots(world));
        finishChoices(world); world.update(ArenaWorld.STEP);
        assertTrue(world.boss.windup < windup);
    }

    @Test void finalDevelopmentFixtureDoesNotInventAHistoricalMiniboss() {
        ArenaWorld world = solo(); world.debugStartBoss(); finishChoicesAfterTick(world);
        world.update(ArenaWorld.STEP);
        assertTrue(world.bossSpawned); assertFalse(world.sovereignSpawned);
        assertFalse(world.sovereignDefeated); assertNull(world.sovereign);
        assertEquals(0, count(world, ArenaWorld.EnemyKind.SOVEREIGN));
        // An explicit mini fixture can still be requested for a coexistence inspection.
        world.debugStartSovereign(); assertTrue(world.sovereignSpawned);
    }

    @Test void resetClearsBothEncountersAndAllowsTheirNextRunSpawns() {
        ArenaWorld world = solo(); world.debugStartSovereign(); world.debugStartBoss();
        world.reset();
        assertFalse(world.sovereignSpawned); assertFalse(world.sovereignDefeated);
        assertFalse(world.bossSpawned); assertFalse(world.bossDefeated);
        assertNull(world.sovereign); assertNull(world.boss); assertNull(world.activeBoss());
        world.activatePlayer(0); world.debugStartSovereign(); assertTrue(world.sovereignSpawned);
        assertEquals(1, world.sovereign.encounterPhase); assertEquals(0, world.sovereign.bossAttackIndex);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(0); world.players[0].attackCooldown = 1000;
        world.players[0].invulnerable = 1000; return world;
    }
    private static int count(ArenaWorld world, ArenaWorld.EnemyKind kind) {
        int count = 0; for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active && enemy.kind == kind) count++; return count;
    }
    private static int shots(ArenaWorld world) { int count = 0; for (ArenaWorld.Shot shot : world.shots) if (shot.active) count++; return count; }
    private static int hazards(ArenaWorld world) { int count = 0; for (ArenaWorld.Hazard hazard : world.hazards) if (hazard.active) count++; return count; }
    private static void finishChoicesAfterTick(ArenaWorld world) { world.update(ArenaWorld.STEP); finishChoices(world); }
    private static void finishChoices(ArenaWorld world) {
        for (int round = 0; round < 32 && world.choicePending(); round++) {
            for (int slot = 0; slot < 2; slot++) {
                if (world.progression.offers[slot].pending)
                    assertTrue(world.progression.choose(slot, world.progression.offers[slot].id, 0));
                if (!world.levelUpPending() && world.equipment.offers[slot].pending)
                    assertTrue(world.equipment.choose(slot, world.equipment.offers[slot].id, -1));
            }
        }
        assertFalse(world.choicePending());
    }
}
