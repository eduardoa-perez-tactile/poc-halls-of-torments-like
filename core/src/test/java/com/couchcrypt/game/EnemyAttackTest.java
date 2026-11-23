package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyAttackTest {
    private static final float EPSILON = .0001f;

    @Test void wraithCommitsToOneStraightCrossingAndCanOnlyHitEachPlayerOnce() {
        ArenaWorld world = solo();
        world.activatePlayer(1); world.players[1].x = -10; world.players[1].y = 0;
        world.players[1].attackCooldown = 20; world.players[1].invulnerable = 0;
        ArenaWorld.Enemy wraith = enemy(world, ArenaWorld.EnemyKind.WRAITH, 210, 0);
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.EnemyAttack.WRAITH_DASH, wraith.attack);
        assertEquals(ActorAnimation.State.WINDUP, ActorAnimation.state(wraith));
        assertEquals(-1, wraith.castFacingX, EPSILON); assertEquals(0, wraith.castFacingY);
        steps(world, 29);
        assertEquals(210, wraith.x); assertEquals(100, world.players[0].health);
        for (int tick = 0; tick < 18; tick++) {
            for (ArenaWorld.Player p : world.players) p.invulnerable = 0;
            world.update(ArenaWorld.STEP);
        }
        assertEquals(-55, wraith.x, .001f); assertEquals(0, wraith.y);
        assertEquals(3, wraith.attackHitMask); assertEquals(2, world.heroHits); assertEquals(1, world.wraithDashes);
        assertEquals(92, world.players[0].health); assertEquals(92, world.players[1].health);
        assertEquals(ActorAnimation.Facing.SW, ActorAnimation.facing(wraith, ActorAnimation.state(wraith)));
    }

    @Test void dodgedWraithCrossingIsConsumedAndRecoveryHasNoPassiveContactDamage() {
        ArenaWorld world = solo();
        ArenaWorld.Player player = world.players[0]; player.invulnerable = 1;
        ArenaWorld.Enemy wraith = enemy(world, ArenaWorld.EnemyKind.WRAITH, 210, 0);
        world.update(ArenaWorld.STEP); steps(world, 46);
        assertEquals(1, wraith.attackHitMask); assertEquals(100, player.health);
        player.x = wraith.x; player.y = wraith.y; player.invulnerable = 0;
        steps(world, 15);
        assertEquals(100, player.health); assertEquals(0, world.heroHits);
    }

    @Test void wraithDirectionAndDashEndpointDoNotChaseDodgingTargetAndRespectArenaEdge() {
        ArenaWorld world = solo(); world.players[0].x = -1100;
        ArenaWorld.Enemy wraith = enemy(world, ArenaWorld.EnemyKind.WRAITH, -1050, 0);
        world.update(ArenaWorld.STEP);
        assertEquals(-1183, wraith.attackTargetX, EPSILON);
        world.players[0].x = -1050; world.players[0].y = 170;
        steps(world, 47);
        assertEquals(-1183, wraith.x, EPSILON); assertEquals(0, wraith.y);
        assertEquals(-1, wraith.castFacingX, EPSILON); assertEquals(0, wraith.castFacingY);
    }

    @Test void bruteWarnsOnMeasuredFacingStrikeAndOnlyDamagesOnceAtTheSlam() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy brute = enemy(world, ArenaWorld.EnemyKind.BRUTE, 85, 0);
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.EnemyAttack.BRUTE_SLAM, brute.attack);
        assertEquals(85 + EnemyAttackGeometry.bruteSlamX(ActorAnimation.Facing.SW, false), brute.attackTargetX, EPSILON);
        assertEquals(EnemyAttackGeometry.bruteSlamY(ActorAnimation.Facing.SW, false), brute.attackTargetY, EPSILON);
        assertEquals(55, brute.attackRadius); assertEquals(100, world.players[0].health);
        steps(world, 44); assertEquals(100, world.players[0].health);
        world.update(ArenaWorld.STEP); assertEquals(82, world.players[0].health);
        world.players[0].invulnerable = 0; steps(world, 25);
        assertEquals(82, world.players[0].health); assertEquals(1, world.heroHits); assertEquals(1, world.bruteSlams);
        assertEquals(85, brute.x); assertEquals(0, brute.y);

        ArenaWorld rotated = solo();
        ArenaWorld.Enemy second = enemy(rotated, ArenaWorld.EnemyKind.BRUTE, 0, -85);
        rotated.update(ArenaWorld.STEP);
        assertEquals(EnemyAttackGeometry.bruteSlamX(ActorAnimation.Facing.NW, false), second.attackTargetX, EPSILON);
        assertEquals(-85 + EnemyAttackGeometry.bruteSlamY(ActorAnimation.Facing.NW, false), second.attackTargetY, EPSILON);
    }

    @Test void bruteDodgeOutsideLockedCircleEscapesAndDoesNotRetarget() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy brute = enemy(world, ArenaWorld.EnemyKind.BRUTE, 85, 0);
        world.update(ArenaWorld.STEP);
        world.players[0].x = -100;
        steps(world, 60);
        assertEquals(85 + EnemyAttackGeometry.bruteSlamX(ActorAnimation.Facing.SW, false), brute.attackTargetX, EPSILON);
        assertEquals(EnemyAttackGeometry.bruteSlamY(ActorAnimation.Facing.SW, false), brute.attackTargetY, EPSILON);
        assertEquals(100, world.players[0].health); assertEquals(0, world.heroHits);
    }

    @Test void cultistReleasesOneLockedShotAndRecoversWithoutTurningOrMoving() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy cultist = enemy(world, ArenaWorld.EnemyKind.CULTIST, 350, 0);
        world.update(ArenaWorld.STEP);
        world.players[0].y = 100; steps(world, 42);
        ArenaWorld.Shot shot = world.shots[0];
        assertTrue(shot.active); assertEquals(-180, shot.vx, EPSILON); assertEquals(0, shot.vy);
        assertEquals(-1, shot.launchFacingX, EPSILON); assertEquals(0, shot.launchFacingY);
        assertEquals(ArenaWorld.EnemyKind.CULTIST, shot.casterKind);
        assertEquals(ArenaWorld.STEP, shot.age, EPSILON);
        steps(world, 25);
        int active = 0; for (ArenaWorld.Shot s : world.shots) if (s.active) active++;
        assertEquals(1, active); assertEquals(1, world.hostileShotsFired); assertEquals(350, cultist.x); assertEquals(0, cultist.y);
        assertEquals(0, cultist.facingY); assertTrue(cultist.attackAge > 1);
        assertEquals(ArenaWorld.EnemyAttack.CULTIST_CAST, cultist.attack);
    }

    @Test void knockbackMovesLiveWarningsWithTheirOriginButNeverSlidesACommittedSlam() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy brute = enemy(world, ArenaWorld.EnemyKind.BRUTE, 85, 0);
        world.update(ArenaWorld.STEP);
        float impactX = brute.attackTargetX, impactY = brute.attackTargetY;
        world.players[0].attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(97, brute.x, EPSILON); assertEquals(impactX + 12, brute.attackTargetX, EPSILON);
        assertEquals(impactY, brute.attackTargetY, EPSILON); assertEquals(-1, brute.castFacingX);
        world.players[0].attackCooldown = 20;
        while (brute.attackAge < ArenaWorld.BRUTE_WINDUP) world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.BRUTE_WINDUP, brute.attackAge);
        assertEquals(0, brute.windup); assertEquals(82, world.players[0].health);
        world.players[0].attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(109, brute.x, EPSILON);
        assertEquals(impactX + 12, brute.attackTargetX, EPSILON, "The burst remains at its committed impact");

        ArenaWorld dashWorld = solo();
        ArenaWorld.Enemy wraith = enemy(dashWorld, ArenaWorld.EnemyKind.WRAITH, 120, 0);
        dashWorld.update(ArenaWorld.STEP);
        float start = wraith.attackStartX, end = wraith.attackTargetX;
        dashWorld.players[0].attackCooldown = 0; dashWorld.update(ArenaWorld.STEP);
        assertEquals(start + 25, wraith.attackStartX, EPSILON);
        assertEquals(end + 25, wraith.attackTargetX, EPSILON);
        assertEquals(-1, wraith.castFacingX); assertEquals(0, wraith.castFacingY);
        dashWorld.players[0].attackCooldown = 20;
        while (wraith.attackAge < .55f) dashWorld.update(ArenaWorld.STEP);
        start = wraith.attackStartX; end = wraith.attackTargetX;
        dashWorld.players[0].attackCooldown = 0; dashWorld.update(ArenaWorld.STEP);
        assertEquals(start + 25, wraith.attackStartX, EPSILON);
        assertEquals(end + 25, wraith.attackTargetX, EPSILON);
    }

    @Test void activeAttackAndProjectileAgesFreezeDuringPartyChoicesAndResetWithPoolState() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy cultist = enemy(world, ArenaWorld.EnemyKind.CULTIST, 350, 0);
        world.update(ArenaWorld.STEP); steps(world, 43);
        ArenaWorld.Shot shot = world.shots[0];
        float age = cultist.attackAge, shotAge = shot.age, x = shot.x;
        world.progression.addXp(world.progression.xpToNext); assertTrue(world.choicePending());
        world.update(.1f);
        assertEquals(age, cultist.attackAge); assertEquals(shotAge, shot.age); assertEquals(x, shot.x);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(ArenaWorld.STEP); assertTrue(cultist.attackAge > age); assertTrue(shot.age > shotAge);
        world.reset(); assertEquals(ArenaWorld.EnemyAttack.NONE, cultist.attack);
        assertEquals(0, world.wraithDashes); assertEquals(0, world.bruteSlams); assertEquals(0, world.hostileShotsFired);
        assertEquals(0, cultist.attackHitMask); assertEquals(0, cultist.attackTargetX); assertEquals(0, cultist.attackRadius);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        world.players[0].attackCooldown = 20; world.players[0].invulnerable = 0;
        return world;
    }

    private static ArenaWorld.Enemy enemy(ArenaWorld world, ArenaWorld.EnemyKind kind, float x, float y) {
        ArenaWorld.Enemy e = world.enemies[0]; e.active = true; e.kind = kind; e.x = x; e.y = y;
        e.health = e.maxHealth = 1000; e.attackCooldown = 0;
        e.radius = kind == ArenaWorld.EnemyKind.WRAITH ? 17 : kind == ArenaWorld.EnemyKind.BRUTE ? 31 : 20;
        e.damage = kind == ArenaWorld.EnemyKind.BRUTE ? 18 : kind == ArenaWorld.EnemyKind.WRAITH ? 8 : 12;
        world.activeEnemies = 1; return e;
    }

    private static void steps(ArenaWorld world, int ticks) {
        for (int tick = 0; tick < ticks; tick++) world.update(ArenaWorld.STEP);
    }
}
