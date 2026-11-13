package com.couchcrypt.game;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Short event fixtures: presentation follows committed combat and its existing freeze boundaries. */
class ActorPresentationStateTest {
    private static final float EPSILON = .00001f;

    @Test void wardenAttackIsImmediateWhileMovingAndDodgingAndKeepsItsAimSnapshot() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0];
        ArenaWorld.Enemy target = enemy(world, 0, ArenaWorld.EnemyKind.GHOUL, 80, 0);
        world.setInput(0, 0, 1, true);
        world.update(ArenaWorld.STEP);

        assertTrue(player.moving); assertTrue(player.y > 0); assertTrue(player.dodgeTime > 0);
        assertEquals(72, target.health, EPSILON); assertEquals(1, world.swordSwings);
        assertEquals(0, player.attackAge); assertTrue(player.attackFacingX > .9f);
        assertTrue(player.attackFacingY < 0);
        assertEquals(0, player.dodgeFacingX()); assertEquals(1, player.dodgeFacingY());
        float aimX = player.attackFacingX, aimY = player.attackFacingY, y = player.y;

        world.setInput(0, -1, 0, false);
        world.update(ArenaWorld.STEP);
        assertTrue(player.y > y, "The original dodge keeps moving during the sword action");
        assertEquals(ArenaWorld.STEP, player.attackAge, EPSILON);
        assertEquals(aimX, player.attackFacingX); assertEquals(aimY, player.attackFacingY);
        assertEquals(-1, player.facingX, EPSILON);
        assertEquals(0, player.dodgeFacingX()); assertEquals(1, player.dodgeFacingY());
        assertEquals(72, target.health, EPSILON); assertEquals(1, world.swordSwings);
    }

    @Test void noTargetDoesNotStartAnAttackAndRangerShotHasNoPresentationDelay() {
        ArenaWorld empty = solo(ArenaWorld.Hero.RANGER);
        empty.update(ArenaWorld.STEP);
        assertEquals(1, empty.players[0].attackAge); assertEquals(0, empty.arrowsFired);

        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        ArenaWorld.Player player = world.players[0];
        enemy(world, 0, ArenaWorld.EnemyKind.GHOUL, 400, 0);
        world.setInput(0, 0, 1, false);
        world.update(ArenaWorld.STEP);
        assertTrue(player.moving); assertEquals(1, world.arrowsFired);
        assertEquals(0, player.attackAge); assertTrue(world.bolts[0].active);
        assertTrue(world.bolts[0].x > player.x, "The bolt already travels in the action's first tick");
        assertTrue(player.attackFacingX > .9f);
        assertEquals(.55f, player.attackCooldown, EPSILON);
    }

    @Test void actualMovementDistinguishesClampingAndStunFromInputOrStaleVelocity() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0]; player.attackCooldown = 10;
        player.x = ArenaWorld.HALF_WIDTH - 20;
        world.setInput(0, 1, 0, false);
        world.update(ArenaWorld.STEP);
        assertFalse(player.moving, "Input against the boundary does not move the actor");

        player.x = 0; world.clearInputs();
        ArenaWorld.Enemy enemy = enemy(world, 0, ArenaWorld.EnemyKind.GHOUL, 400, 0);
        world.update(ArenaWorld.STEP);
        assertTrue(enemy.moving); assertTrue(enemy.vx < 0);
        assertEquals(-1, enemy.facingX, EPSILON);
        float phase = enemy.moveTime, x = enemy.x, animation = enemy.animTime;
        enemy.stunTime = .2f;
        world.update(ArenaWorld.STEP);
        assertEquals(x, enemy.x); assertTrue(enemy.vx < 0, "Gameplay velocity remains unchanged by this snapshot");
        assertFalse(enemy.moving); assertEquals(phase, enemy.moveTime);
        assertEquals(animation + ArenaWorld.STEP, enemy.animTime, EPSILON);
        assertEquals(-1, enemy.facingX, EPSILON);
    }

    @Test void hurtPoseRequiresRealDamageAndIsNotTriggeredByJoinOrDodge() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0]; player.attackCooldown = 10;
        world.update(ArenaWorld.STEP);
        assertTrue(player.invulnerable > 0); assertEquals(0, player.hurtTime);

        player.invulnerable = 0;
        shotAtPlayer(world); world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertEquals(100, player.health); assertEquals(0, player.hurtTime);
        player.dodgeTime = player.invulnerable = 0; world.clearInputs();
        shotAtPlayer(world); world.update(ArenaWorld.STEP);
        assertEquals(85, player.health); assertEquals(.13f, player.hurtTime, EPSILON);
        world.update(ArenaWorld.STEP);
        assertEquals(.13f - ArenaWorld.STEP, player.hurtTime, EPSILON);
        assertTrue(player.invulnerable > player.hurtTime, "The hit pose is not the invulnerability clock");
    }

    @Test void aBlockedHitDoesNotBecomeADamagePose() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0]; player.attackCooldown = 10;
        world.equipment.equip(0, 0, new ItemCatalog.Item("block_fixture", "Block fixture",
            ItemCatalog.Slot.HELMET, 0, "Deterministic block fixture", List.of(
                new ItemCatalog.Modifier(ItemCatalog.Stat.BLOCK_CHANCE, .2f))));
        boolean blocked = false;
        // Fixed run seed; all attempts fit before the first reinforcement and do not change combat RNG.
        for (int attempt = 0; attempt < 32 && !blocked; attempt++) {
            player.invulnerable = player.hurtTime = 0; player.health = 100;
            shotAtPlayer(world); world.update(ArenaWorld.STEP);
            if (player.health == 100) {
                blocked = true; assertEquals(.3f, player.invulnerable, EPSILON); assertEquals(0, player.hurtTime);
            }
        }
        assertTrue(blocked, "The fixed-seed fixture must exercise a real blocked shot");
    }

    @Test void skillAndLootChoicesFreezeAllPresentationSnapshotsUntilCombatResumes() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0]; player.invulnerable = 0;
        enemy(world, 0, ArenaWorld.EnemyKind.GHOUL, 100, 0);
        enemy(world, 1, ArenaWorld.EnemyKind.GHOUL, 500, 100);
        ArenaWorld.Enemy cultist = enemy(world, 2, ArenaWorld.EnemyKind.CULTIST, 600, 0);
        cultist.attackCooldown = 0;
        ArenaWorld.Enemy boss = enemy(world, 3, ArenaWorld.EnemyKind.BOSS, 800, 100);
        boss.attackCooldown = 0;
        shotAtPlayer(world); world.setInput(0, 0, 1, false); world.update(ArenaWorld.STEP);
        assertEquals(0, player.attackAge); assertTrue(player.hurtTime > 0);
        assertTrue(world.enemies[1].moving); assertEquals(0, cultist.castAge);
        assertEquals(0, boss.releaseAge);
        Snapshot before = snapshot(world); float elapsed = world.elapsed;

        world.equipment.grantReward(); assertTrue(world.lootPending());
        world.update(.1f);
        assertEquals(before, snapshot(world)); assertEquals(elapsed, world.elapsed);
        float lootTime = world.equipment.choiceSecondsLeft();
        world.progression.addXp(world.progression.xpToNext); assertTrue(world.levelUpPending());
        world.update(.1f);
        assertEquals(before, snapshot(world)); assertEquals(elapsed, world.elapsed);
        assertEquals(lootTime, world.equipment.choiceSecondsLeft());

        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        assertTrue(world.equipment.choose(0, world.equipment.offers[0].id, -1));
        assertFalse(world.choicePending()); world.update(ArenaWorld.STEP);
        assertEquals(before.attackAge + ArenaWorld.STEP, player.attackAge, EPSILON);
        assertEquals(before.hurtTime - ArenaWorld.STEP, player.hurtTime, EPSILON);
        assertTrue(world.enemies[1].moveTime > before.enemyMoveTime);
        assertEquals(before.castAge + ArenaWorld.STEP, cultist.castAge, EPSILON);
        assertEquals(before.bossReleaseAge + ArenaWorld.STEP, boss.releaseAge, EPSILON);
    }

    @Test void reconnectPreservesAnActionButFreshParticipantAndRunResetClearIt() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player player = world.players[0]; player.invulnerable = 0;
        enemy(world, 0, ArenaWorld.EnemyKind.GHOUL, 100, 0);
        shotAtPlayer(world); world.setInput(0, 0, 1, false); world.update(ArenaWorld.STEP);
        world.update(ArenaWorld.STEP);
        float age = player.attackAge, hurt = player.hurtTime, phase = player.walkTime;
        float aimX = player.attackFacingX, aimY = player.attackFacingY, health = player.health;
        world.activatePlayer(0);
        assertEquals(age, player.attackAge); assertEquals(hurt, player.hurtTime);
        assertEquals(phase, player.walkTime); assertEquals(health, player.health);
        assertEquals(aimX, player.attackFacingX); assertEquals(aimY, player.attackFacingY); assertTrue(player.moving);

        world.deactivatePlayer(0); world.activatePlayer(0);
        assertEquals(1, player.attackAge); assertEquals(0, player.hurtTime);
        assertEquals(0, player.walkTime); assertFalse(player.moving);
        world.update(ArenaWorld.STEP); world.reset();
        assertFalse(player.active); assertFalse(player.moving);
        assertEquals(1, player.attackAge); assertEquals(0, player.hurtTime); assertEquals(0, player.walkTime);
    }

    @Test void pooledSpawnDoesNotInheritPreviousMovementOrCastPresentation() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy previous = enemy(world, 0, ArenaWorld.EnemyKind.CULTIST, 400, 0);
        previous.attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(.7f, previous.castDuration); assertEquals(0, previous.castAge);
        previous.windup = ArenaWorld.STEP; world.update(ArenaWorld.STEP);
        assertEquals(0, previous.releaseAge, "The pooled slot has exercised a real release event");
        previous.active = false; world.activeEnemies = 0;
        world.debugStartBoss();
        assertSame(previous, world.boss); assertEquals(ArenaWorld.EnemyKind.BOSS, previous.kind);
        assertFalse(previous.moving); assertEquals(0, previous.moveTime);
        assertEquals(1, previous.castAge); assertEquals(0, previous.castDuration);
        assertEquals(1, previous.releaseAge);
        assertEquals(1, previous.facingX * previous.facingX + previous.facingY * previous.facingY, EPSILON);
        world.reset();
        assertEquals(0, previous.moveTime); assertEquals(0, previous.castDuration); assertEquals(1, previous.castAge);
        assertEquals(1, previous.releaseAge);
    }

    @Test void cultistCastTracksItsExistingDelayedReleaseAndBossBoltsStillStartImmediately() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy cultist = enemy(world, 0, ArenaWorld.EnemyKind.CULTIST, 350, 0);
        cultist.attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertEquals(0, cultist.castAge); assertEquals(.7f, cultist.castDuration);
        assertEquals(1, cultist.releaseAge, "Starting a windup is not a shot release");
        assertTrue(cultist.windup > 0); assertFalse(cultist.moving); assertEquals(0, activeShots(world));
        world.players[0].y = 80;
        for (int tick = 0; tick < 44 && cultist.windup > 0; tick++) world.update(ArenaWorld.STEP);
        assertEquals(0, cultist.windup); assertEquals(1, activeShots(world));
        assertEquals(0, cultist.releaseAge, "Release artwork starts in the projectile's actual creation tick");
        assertTrue(cultist.castFacingX < 0); assertTrue(cultist.castFacingY > 0,
            "Release still aims at the live target, not its position at cast start");

        ArenaWorld bossWorld = solo(ArenaWorld.Hero.WARDEN); bossWorld.players[0].attackCooldown = 10;
        bossWorld.debugStartBoss(); bossWorld.boss.attackCooldown = 0;
        bossWorld.update(ArenaWorld.STEP);
        assertEquals(0, bossWorld.boss.castAge); assertEquals(1, bossWorld.boss.castDuration);
        assertEquals(0, bossWorld.boss.releaseAge);
        assertTrue(bossWorld.boss.windup > 0); assertEquals(5, activeShots(bossWorld));
        assertTrue(bossWorld.hazards[0].active); assertTrue(bossWorld.hazards[0].warning > .9f);
        assertEquals(100, bossWorld.players[0].health);
    }

    @Test void projectilePresentationKeepsItsEmitterAfterDeathAndRefreshesOnShotPoolReuse() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy cultist = enemy(world, 0, ArenaWorld.EnemyKind.CULTIST, 350, 0);
        cultist.elite = true; cultist.attackCooldown = 0; cultist.damage = 12;
        world.update(ArenaWorld.STEP);
        for (int tick = 0; tick < 44 && cultist.windup > 0; tick++) world.update(ArenaWorld.STEP);
        ArenaWorld.Shot shot = world.shots[0];
        assertTrue(shot.active); assertEquals(ArenaWorld.EnemyKind.CULTIST, shot.casterKind);
        assertTrue(shot.casterElite); assertEquals(12, shot.damage);
        assertEquals(180, Math.hypot(shot.vx, shot.vy), .001);

        cultist.active = false; world.activeEnemies--;
        ArenaWorld.Enemy boss = enemy(world, 0, ArenaWorld.EnemyKind.BOSS, 350, 0);
        boss.elite = false; boss.attackCooldown = 10;
        world.update(ArenaWorld.STEP);
        assertEquals(ArenaWorld.EnemyKind.CULTIST, shot.casterKind,
            "Reusing the caster's slot cannot change an already flying shot");
        assertTrue(shot.casterElite);

        shot.active = false; boss.attackCooldown = 0; world.update(ArenaWorld.STEP);
        assertTrue(shot.active); assertEquals(ArenaWorld.EnemyKind.BOSS, shot.casterKind);
        assertFalse(shot.casterElite); assertEquals(15, shot.damage);
        assertEquals(145, Math.hypot(shot.vx, shot.vy), .001);
        assertEquals(5, activeShots(world));
    }

    private static ArenaWorld solo(ArenaWorld.Hero hero) {
        ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0); return world;
    }

    private static ArenaWorld.Enemy enemy(ArenaWorld world, int index, ArenaWorld.EnemyKind kind, float x, float y) {
        ArenaWorld.Enemy enemy = world.enemies[index];
        enemy.active = true; enemy.kind = kind; enemy.x = x; enemy.y = y;
        enemy.health = enemy.maxHealth = 100; enemy.attackCooldown = 10;
        world.activeEnemies++; return enemy;
    }

    private static void shotAtPlayer(ArenaWorld world) {
        ArenaWorld.Shot shot = world.shots[0]; shot.active = true;
        shot.x = world.players[0].x; shot.y = world.players[0].y;
        shot.vx = shot.vy = 0; shot.radius = 10; shot.damage = 15; shot.ttl = 1;
    }

    private static int activeShots(ArenaWorld world) {
        int count = 0; for (ArenaWorld.Shot shot : world.shots) if (shot.active) count++; return count;
    }

    private record Snapshot(float attackAge, float hurtTime, float walkTime, boolean moving,
                            float attackFacingX, float attackFacingY, float enemyMoveTime,
                            boolean enemyMoving, float enemyAnimTime, float enemyHitFlash,
                            float castAge, float castDuration, float castFacingX, float castFacingY,
                            float releaseAge, float bossReleaseAge) {}

    private static Snapshot snapshot(ArenaWorld world) {
        ArenaWorld.Player player = world.players[0]; ArenaWorld.Enemy walker = world.enemies[1], caster = world.enemies[2];
        return new Snapshot(player.attackAge, player.hurtTime, player.walkTime, player.moving,
            player.attackFacingX, player.attackFacingY, walker.moveTime, walker.moving, walker.animTime,
            world.enemies[0].hitFlash, caster.castAge, caster.castDuration, caster.castFacingX, caster.castFacingY,
            caster.releaseAge, world.enemies[3].releaseAge);
    }
}
