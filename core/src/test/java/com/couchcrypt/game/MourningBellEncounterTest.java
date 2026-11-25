package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MourningBellEncounterTest {
    private static final float EPSILON = .002f;

    @Test void fiveActiveMinutesSpawnsOnceAndBothLaterBossesRetainTheirScheduleAndPriority() {
        ArenaWorld world = solo();
        assertEquals(300, world.timeline.bellSeconds());
        assertEquals(45, ArenaTimeline.SHORT.bellSeconds());
        world.elapsed = 300 - 2 * ArenaWorld.STEP;
        tick(world); assertFalse(world.bellSpawned);
        tick(world); assertTrue(world.bellSpawned);
        ArenaWorld.Enemy bell = world.bell;
        assertEquals(1800, bell.maxHealth); assertFalse(bell.elite);
        assertEquals(EnemyVariant.ORIGINAL, bell.variant);
        assertSame(bell, world.activeBoss()); assertEquals("THE MOURNING BELL", ArenaWorld.bossName(bell));
        world.elapsed = 720; tick(world);
        assertTrue(world.sovereignSpawned); assertSame(bell, world.bell); assertTrue(bell.active);
        assertSame(world.sovereign, world.activeBoss());
        world.elapsed = 1200; tick(world);
        assertTrue(world.bossSpawned); assertSame(world.boss, world.activeBoss());
        tick(world); assertEquals(1, count(world, ArenaWorld.EnemyKind.MOURNING_BELL));
        assertEquals(1, count(world, ArenaWorld.EnemyKind.SOVEREIGN));
        assertEquals(1, count(world, ArenaWorld.EnemyKind.BOSS));
        assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
    }

    @Test void requiredSpawnRetriesFullElitePoolAndCanReplaceOnlyAnOrdinaryReinforcement() {
        ArenaWorld world = solo();
        for (ArenaWorld.Enemy enemy : world.enemies) {
            enemy.active = enemy.elite = true; enemy.x = -1000; enemy.y = -800;
            enemy.health = enemy.maxHealth = 10000; world.activeEnemies++;
        }
        world.elapsed = 300; tick(world);
        assertFalse(world.bellSpawned); assertEquals(64, world.activeEnemies);
        world.enemies[0].elite = false;
        tick(world); assertTrue(world.bellSpawned); assertEquals(64, world.activeEnemies);
        for (ArenaWorld.Player player : world.players) if (player.active)
            assertTrue(Math.hypot(player.x - world.bell.x, player.y - world.bell.y) >= 300);
        tick(world); assertEquals(1, count(world, ArenaWorld.EnemyKind.MOURNING_BELL));
    }

    @Test void spawnSnapshotsCoopHealthAndJoinOrReconnectNeverRefillsTheBell() {
        ArenaWorld world = solo(); world.debugStartBell();
        world.bell.health -= 100; world.activatePlayer(1); world.activatePlayer(1);
        assertEquals(1800, world.bell.maxHealth); assertEquals(1700, world.bell.health);
        world.reset(); world.activatePlayer(0); world.activatePlayer(1); world.debugStartBell();
        assertEquals(2880, world.bell.maxHealth, EPSILON);
    }

    @Test void lungeWarnsThenSweepsLockedLaneOnceAndRecoveryDoesNoContactDamage() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        bell.x = -200; bell.y = 0; bell.attackCooldown = 0;
        ArenaWorld.Player player = world.players[0]; player.invulnerable = 0;
        tick(world);
        assertEquals(ArenaWorld.EnemyAttack.BELL_LUNGE, bell.attack);
        assertEquals(MourningBell.LUNGE_WINDUP, bell.windup, EPSILON);
        assertEquals(-200, bell.attackStartX, EPSILON); assertTrue(bell.attackTargetX > 0);
        float endX = bell.attackTargetX;
        steps(world, 49); assertEquals(100, player.health); assertEquals(-200, bell.x, EPSILON);
        // Moving the target after commitment must not bend the warned lane.
        player.y = 300; steps(world, 28);
        assertEquals(endX, bell.x, EPSILON); assertEquals(0, bell.y, EPSILON);
        assertEquals(1, world.bellLunges); assertEquals(100, player.health);
        player.x = bell.x; player.y = bell.y; player.invulnerable = 0;
        steps(world, 30); assertEquals(100, player.health, "Recovery body cannot deal untelegraphed contact damage");

        world = isolated(); bell = world.bell; bell.x = -200; bell.y = 0; bell.attackCooldown = 0;
        world.players[0].invulnerable = 0; steps(world, 80);
        assertEquals(84, world.players[0].health, EPSILON);
        assertEquals(1, bell.attackHitMask); assertEquals(1, world.bellLunges);
    }

    @Test void dodgeConsumesLungeCrossingAndHasNoDelayedDamageAfterInvulnerabilityExpires() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        bell.x = -200; bell.y = 0; bell.attackCooldown = 0;
        steps(world, 59);
        ArenaWorld.Player player = world.players[0]; player.invulnerable = 0;
        world.setInput(0, -1, 0, true);
        steps(world, 8);
        assertEquals(1, world.dodges); assertTrue(player.invulnerable > 0);
        assertEquals(100, player.health); assertEquals(1, bell.attackHitMask);
        world.setInput(0, 0, 0, false); steps(world, 50);
        assertEquals(100, player.health);
    }

    @Test void tollIsAnExpandingRingWithSafeInsideAndOutsideRatherThanAFilledDisk() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        bell.x = 0; bell.y = 0; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        ArenaWorld.Player player = world.players[0]; player.x = 140; player.y = 0; player.invulnerable = 0;
        tick(world); assertEquals(ArenaWorld.EnemyAttack.BELL_TOLL, bell.attack);
        assertTrue(MourningBell.tollWarning(bell, 0)); assertFalse(MourningBell.tollActive(bell, 0));
        steps(world, 52); assertEquals(100, player.health);
        steps(world, 40); assertEquals(86, player.health, EPSILON); assertEquals(1, world.bellTolls);
        player.x = 0; player.invulnerable = 0;
        steps(world, 12); assertEquals(86, player.health, EPSILON, "Already passed wave leaves its centre safe");
        assertFalse(MourningBell.tollActive(bell, 0));

        world = isolated(); bell = world.bell; bell.x = bell.y = 0; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        world.players[0].x = 310; world.players[0].invulnerable = 0;
        steps(world, 130); assertEquals(100, world.players[0].health);
    }

    @Test void eachEnragedTollHasItsOwnWarningAndHitMaskAndHalfHealthCrossingCannotAddABeat() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        bell.x = bell.y = 0; bell.health = bell.maxHealth * .4f; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        ArenaWorld.Player player = world.players[0]; player.x = 140; player.invulnerable = 0;
        tick(world); assertTrue(bell.bossCastEnraged);
        steps(world, 101); assertTrue(MourningBell.tollWarning(bell, 1));
        assertEquals(.7f, bell.castDuration, EPSILON); assertEquals(1, world.bellTolls);
        steps(world, 62); assertEquals(2, world.bellTolls); assertEquals(72, player.health, EPSILON);
        assertEquals(5, bell.attackHitMask);

        world = isolated(); bell = world.bell; bell.x = bell.y = 0; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        tick(world); assertFalse(bell.bossCastEnraged); bell.health = bell.maxHealth * .4f;
        steps(world, 140); assertEquals(1, world.bellTolls);
        assertEquals(ArenaWorld.EnemyAttack.NONE, bell.attack);
    }

    @Test void coopTollHitsEachPlayerOnceAtTheirOwnDistanceAndPreservesIndependentDamage() {
        ArenaWorld world = solo(); world.activatePlayer(1); world.profileMourningBell();
        ArenaWorld.Enemy bell = world.bell;
        bell.x = bell.y = 0; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        for (ArenaWorld.Player player : world.players) {
            player.x = player.slot == 0 ? 100 : 220;
            player.y = 0; player.invulnerable = 0; player.attackCooldown = 1000;
        }
        steps(world, 80);
        assertEquals(86, world.players[0].health, EPSILON);
        assertEquals(100, world.players[1].health, EPSILON);
        steps(world, 25);
        assertEquals(86, world.players[0].health, EPSILON);
        assertEquals(86, world.players[1].health, EPSILON);
        assertEquals(3, bell.attackHitMask);
    }

    @Test void dodgeCanCrossTollWithoutLeavingADelayedHit() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        bell.x = bell.y = 0; bell.bossAttackIndex = 1; bell.attackCooldown = 0;
        ArenaWorld.Player player = world.players[0]; player.x = 140; player.invulnerable = 0;
        steps(world, 66);
        world.setInput(0, -1, 0, true); steps(world, 13);
        world.setInput(0, 0, 0, false); steps(world, 30);
        assertEquals(1, world.dodges); assertEquals(100, player.health);
        assertEquals(1, bell.attackHitMask, "Dodge crossing is consumed for this pulse");
    }

    @Test void choicePausesFreezeArrivalAttackClockAndWaveWithBothPlayers() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        world.elapsed = 300 - ArenaWorld.STEP;
        world.progression.addXp(world.progression.xpToNext);
        float elapsed = world.elapsed; world.update(.1f);
        assertEquals(elapsed, world.elapsed); assertFalse(world.bellSpawned);
        finishChoices(world); tick(world); assertTrue(world.bellSpawned);
        world.bell.attackCooldown = 0; tick(world);
        ArenaWorld.Enemy bell = world.bell;
        float age = bell.attackAge, x = bell.x, windup = bell.windup; elapsed = world.elapsed;
        world.equipment.grantReward(); world.update(.1f);
        assertEquals(elapsed, world.elapsed); assertEquals(age, bell.attackAge);
        assertEquals(x, bell.x); assertEquals(windup, bell.windup);
        finishChoices(world); tick(world); assertTrue(bell.attackAge > age);
    }

    @Test void deathAwardsSoulsWithoutEquipmentOrVictoryAndResetClearsAllState() {
        ArenaWorld world = isolated(); ArenaWorld.Enemy bell = world.bell;
        int rewards = world.equipment.rewardsEarned(); bell.x = 80; bell.y = 0; bell.health = 1;
        world.players[0].attackCooldown = 0; tick(world);
        assertTrue(world.bellDefeated); assertFalse(bell.active); assertNull(world.activeBoss());
        assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
        assertEquals(rewards, world.equipment.rewardsEarned());
        int souls = 0; for (ArenaWorld.Pickup pickup : world.pickups) if (pickup.active && !pickup.healing) souls += pickup.value;
        assertEquals(12, souls); assertEquals(8, world.chests.length);
        tick(world); assertEquals(0, count(world, ArenaWorld.EnemyKind.MOURNING_BELL));
        world.reset(); assertFalse(world.bellSpawned); assertFalse(world.bellDefeated); assertNull(world.bell);
        assertEquals(0, world.bellLunges); assertEquals(0, world.bellTolls);
        world.activatePlayer(0); world.debugStartBell();
        assertTrue(world.bellSpawned); assertEquals(ArenaWorld.EnemyAttack.NONE, world.bell.attack);
        assertEquals(0, world.bell.attackHitMask); assertEquals(0, world.bell.bossAttackIndex);
    }

    @Test void laterDevelopmentFixturesDoNotInventAnUnearnedEarlyBoss() {
        ArenaWorld world = solo(); world.debugStartSovereign(); tick(world);
        assertFalse(world.bellSpawned); assertNull(world.bell);
        world.debugStartBell(); assertTrue(world.bellSpawned);
        world = solo(); world.debugStartBoss(); tick(world);
        assertFalse(world.bellSpawned); assertFalse(world.sovereignSpawned);
        world = solo(); world.debugStartLateEncounter(); tick(world);
        assertFalse(world.bellSpawned); assertFalse(world.sovereignSpawned);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(0); world.players[0].attackCooldown = 1000; world.players[0].invulnerable = 1000;
        return world;
    }
    private static ArenaWorld isolated() {
        ArenaWorld world = solo(); world.profileMourningBell(); return world;
    }
    private static void tick(ArenaWorld world) { world.update(ArenaWorld.STEP); finishChoices(world); }
    private static void steps(ArenaWorld world, int count) { for (int i = 0; i < count; i++) tick(world); }
    private static int count(ArenaWorld world, ArenaWorld.EnemyKind kind) {
        int count = 0; for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active && enemy.kind == kind) count++; return count;
    }
    private static void finishChoices(ArenaWorld world) {
        for (int i = 0; i < 32 && world.choicePending(); i++) for (int slot = 0; slot < 2; slot++) {
            if (world.progression.offers[slot].pending)
                assertTrue(world.progression.choose(slot, world.progression.offers[slot].id, 0));
            if (!world.levelUpPending() && world.equipment.offers[slot].pending)
                assertTrue(world.equipment.choose(slot, world.equipment.offers[slot].id, -1));
        }
        assertFalse(world.choicePending());
    }
}
