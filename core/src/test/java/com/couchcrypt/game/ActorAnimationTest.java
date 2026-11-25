package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationTest {
    @Test void worldAxesResolveToAuthoredDiagonalsWithoutMirroring() {
        assertEquals(ActorAnimation.Facing.NE, ActorAnimation.facing(1, 0, ActorAnimation.Facing.SE));
        assertEquals(ActorAnimation.Facing.NW, ActorAnimation.facing(0, 1, ActorAnimation.Facing.SE));
        assertEquals(ActorAnimation.Facing.SW, ActorAnimation.facing(-1, 0, ActorAnimation.Facing.SE));
        assertEquals(ActorAnimation.Facing.SE, ActorAnimation.facing(0, -1, ActorAnimation.Facing.NW));
        assertEquals(ActorAnimation.Facing.NW, ActorAnimation.facing(0, 0, ActorAnimation.Facing.NW));
        assertEquals(ActorAnimation.Facing.SW, ActorAnimation.facing(Float.NaN, 1, ActorAnimation.Facing.SW));
    }

    @Test void attackAimAndDodgeTravelStaySeparateFromNewPhoneInput() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        ArenaWorld.Player player = world.players[0];
        world.setInput(0, 0, 1, true); world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.State.DODGE, ActorAnimation.state(player));
        assertEquals(ActorAnimation.Facing.NW, ActorAnimation.facing(player, ActorAnimation.state(player)));
        world.setInput(0, -1, 0, false); world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.Facing.NW, ActorAnimation.facing(player, ActorAnimation.state(player)));

        ArenaWorld.Enemy target = world.enemies[0]; target.active = true;
        target.x = 70; target.y = player.y; target.health = target.maxHealth = 100; target.attackCooldown = 10;
        world.activeEnemies = 1; world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.State.ATTACK, ActorAnimation.state(player));
        assertEquals(ActorAnimation.Facing.NE, ActorAnimation.facing(player, ActorAnimation.State.ATTACK));
        world.update(ArenaWorld.STEP);
        assertTrue(player.y > 0); assertTrue(player.dodgeTime > 0);
        assertEquals(ActorAnimation.Facing.NE, ActorAnimation.facing(player, ActorAnimation.state(player)));
    }

    @Test void twoCameraReadsDoNotAdvanceClocksAndDownedOverridesAnyAction() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        ArenaWorld.Player player = world.players[0];
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        float phase = player.walkTime;
        for (int camera = 0; camera < 2; camera++) {
            ActorAnimation.State state = ActorAnimation.state(player);
            assertEquals(ActorAnimation.State.MOVE, state);
            assertEquals(phase, ActorAnimation.phase(player, state));
            ActorAnimation.facing(player, state);
        }
        assertEquals(phase, player.walkTime);
        player.alive = false; player.attackAge = 0;
        assertEquals(ActorAnimation.State.DOWNED, ActorAnimation.state(player));
    }

    @Test void cultistRetreatReversesItsGaitWithoutTurningItsAimAwayFromThePlayer() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy caster = world.enemies[0]; caster.active = true;
        caster.kind = ArenaWorld.EnemyKind.CULTIST; caster.x = 200; caster.y = 0;
        caster.health = caster.maxHealth = 100; caster.attackCooldown = 10; world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertTrue(caster.moving); assertTrue(caster.vx > 0); assertTrue(caster.facingX < 0);
        assertEquals(ActorAnimation.State.MOVE, ActorAnimation.state(caster));
        assertTrue(ActorAnimation.phase(caster, ActorAnimation.State.MOVE) < 0);
        assertEquals(ActorAnimation.Facing.SW, ActorAnimation.facing(caster, ActorAnimation.State.MOVE));
    }

    @Test void sovereignImmediateReleaseAndRemainingWarningUseDistinctPoses() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.players[0].attackCooldown = 10;
        // Keep the arena clock at its start: jumping to the finale would also
        // grant timed loot and correctly pause this short pose-transition fixture.
        ArenaWorld.Enemy sovereign = world.enemies[0]; sovereign.active = true;
        sovereign.kind = ArenaWorld.EnemyKind.SOVEREIGN; sovereign.x = 500;
        sovereign.health = sovereign.maxHealth = 1100; world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.Actor.SOVEREIGN, ActorAnimation.actor(sovereign.kind));
        assertEquals(ActorAnimation.State.ATTACK, ActorAnimation.state(sovereign));
        assertEquals(0, ActorAnimation.phase(sovereign, ActorAnimation.State.ATTACK));
        for (int tick = 0; tick < 12; tick++) world.update(ArenaWorld.STEP);
        assertTrue(sovereign.windup > 0);
        assertEquals(ActorAnimation.State.WINDUP, ActorAnimation.state(sovereign));
        assertTrue(world.hazards[0].warning > 0);
        assertEquals(100, world.players[0].health);
    }

    @Test void graveKingUsesItsOwnArtAndShowsCastBeforeProjectileRelease() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy king = world.enemies[0]; king.active = true;
        king.kind = ArenaWorld.EnemyKind.BOSS; king.x = 500;
        king.health = king.maxHealth = 1100; king.bossAttackIndex = 1; world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.Actor.GRAVE_KING, ActorAnimation.actor(king.kind));
        assertEquals(ActorAnimation.State.WINDUP, ActorAnimation.state(king));
        assertEquals(0, world.hostileShotsFired);
        for (int tick = 0; tick < 44; tick++) world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.State.ATTACK, ActorAnimation.state(king));
        assertTrue(world.hostileShotsFired > 0);
        assertEquals(100, world.players[0].health);
    }

    @Test void bellWarningJawAndRecoveryUseTheSameLockedFacingAndSecondBeatResetsPoseClock() {
        ArenaWorld world = new ArenaWorld();
        ArenaWorld.Enemy bell = world.enemies[0]; bell.kind = ArenaWorld.EnemyKind.MOURNING_BELL;
        bell.attack = ArenaWorld.EnemyAttack.BELL_TOLL;
        bell.castFacingX = -1; bell.castFacingY = 0;
        bell.facingX = 1; bell.facingY = 0;
        bell.windup = .6f; bell.castAge = .1f;
        bell.attackAge = MourningBell.SECOND_TOLL_WARNING_START + .1f;
        assertEquals(ActorAnimation.Actor.MOURNING_BELL, ActorAnimation.actor(bell.kind));
        assertEquals(ActorAnimation.State.WINDUP, ActorAnimation.state(bell));
        assertEquals(.1f, ActorAnimation.phase(bell, ActorAnimation.State.WINDUP));
        bell.windup = 0; bell.releaseAge = .02f;
        assertEquals(ActorAnimation.State.ATTACK, ActorAnimation.state(bell));
        assertEquals(.02f, ActorAnimation.phase(bell, ActorAnimation.State.ATTACK));
        bell.releaseAge = .4f;
        assertEquals(ActorAnimation.State.IDLE, ActorAnimation.state(bell));
        assertEquals(ActorAnimation.Facing.SW, ActorAnimation.facing(bell, ActorAnimation.State.IDLE),
            "Recovery cannot turn the committed cast toward new movement");
        float attackAge = bell.attackAge, castAge = bell.castAge;
        for (int camera = 0; camera < 2; camera++) {
            MourningBellPresentation.hover(world.elapsed);
            MourningBellPresentation.tilt(bell);
            ActorAnimation.phase(bell, ActorAnimation.state(bell));
        }
        assertEquals(attackAge, bell.attackAge); assertEquals(castAge, bell.castAge);
    }
}
