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

    @Test void bossImmediateReleaseAndRemainingWarningUseDistinctPoses() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.players[0].attackCooldown = 10;
        // Keep the arena clock at its start: jumping to the finale would also
        // grant timed loot and correctly pause this short pose-transition fixture.
        world.boss = world.enemies[0]; world.boss.active = world.bossSpawned = true;
        world.boss.kind = ArenaWorld.EnemyKind.BOSS; world.boss.x = 500;
        world.boss.health = world.boss.maxHealth = 1100; world.activeEnemies = 1;
        world.update(ArenaWorld.STEP);
        assertEquals(ActorAnimation.State.ATTACK, ActorAnimation.state(world.boss));
        assertEquals(0, ActorAnimation.phase(world.boss, ActorAnimation.State.ATTACK));
        for (int tick = 0; tick < 12; tick++) world.update(ArenaWorld.STEP);
        assertTrue(world.boss.windup > 0);
        assertEquals(ActorAnimation.State.WINDUP, ActorAnimation.state(world.boss));
        assertTrue(world.hazards[0].warning > 0);
        assertEquals(100, world.players[0].health);
    }
}
