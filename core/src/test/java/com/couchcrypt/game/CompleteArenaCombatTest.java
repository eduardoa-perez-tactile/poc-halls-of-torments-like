package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompleteArenaCombatTest {
    @Test void wardenHitsConeTargetsButNotEnemyBehind() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Enemy front = enemy(world, 0, 95, 0, 100);
        ArenaWorld.Enemy beside = enemy(world, 1, 100, 50, 100);
        ArenaWorld.Enemy behind = enemy(world, 2, -80, 0, 100);
        // Make front uniquely nearest so aim is deterministic.
        behind.x = -120;
        world.update(ArenaWorld.STEP);
        assertEquals(72, front.health, .001f);
        assertEquals(72, beside.health, .001f);
        assertEquals(100, behind.health, .001f);
        assertEquals(1, world.swordSwings);
        assertEquals(56, world.players[0].damageDealt, .001f);
        assertTrue(front.stunTime > 0);
        assertFalse(world.bolts[0].active);
    }

    @Test void rangerArrowsPierceOnceAndNeverHitSameEnemyTwice() {
        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        ArenaWorld.Player p = world.players[0]; p.pierce = 1;
        ArenaWorld.Enemy first = enemy(world, 0, 80, 0, 100);
        ArenaWorld.Enemy second = enemy(world, 1, 120, 0, 100);
        for (int i = 0; i < 10; i++) world.update(ArenaWorld.STEP);
        assertEquals(84, first.health, .001f); assertEquals(84, second.health, .001f);
        assertEquals(1, world.arrowsFired); assertEquals(32, p.damageDealt, .001f);
    }

    @Test void critAndMultishotAffectActualAttackAndOverkillStats() {
        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        ArenaWorld.Player p = world.players[0]; p.critChance = 1; p.projectileCount = 3;
        ArenaWorld.Enemy target = enemy(world, 0, 30, 0, 10);
        world.update(ArenaWorld.STEP);
        assertFalse(target.active); assertEquals(3, world.arrowsFired);
        assertEquals(10, p.damageDealt, .001f); assertEquals(1, p.kills);
    }

    @Test void secondaryAbilitiesDealDamageAndEmitBoundedEffects() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player p = world.players[0]; p.attackCooldown = 10; p.auraLevel = p.stormLevel = 1;
        ArenaWorld.Enemy a = enemy(world, 0, 60, 0, 100);
        ArenaWorld.Enemy b = enemy(world, 1, 210, 0, 100);
        world.update(ArenaWorld.STEP);
        assertEquals(76, a.health, .001f); assertEquals(83, b.health, .001f);
        boolean aura = false, lightning = false;
        for (ArenaWorld.Effect fx : world.effects) if (fx.active) {
            aura |= fx.kind == ArenaWorld.EffectKind.AURA;
            lightning |= fx.kind == ArenaWorld.EffectKind.LIGHTNING;
        }
        assertTrue(aura && lightning);
    }

    @Test void hostileShotsRespectDodgeAndTrackActualDamage() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player p = world.players[0]; p.invulnerable = 0;
        ArenaWorld.Shot shot = world.shots[0]; shot.active = true; shot.x = 0; shot.y = 0;
        shot.radius = 10; shot.damage = 15; shot.ttl = 1;
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertEquals(100, p.health); assertFalse(shot.active);
        p.invulnerable = p.dodgeTime = 0;
        shot.active = true; shot.x = p.x; shot.y = p.y; shot.ttl = 1;
        world.update(ArenaWorld.STEP);
        assertEquals(85, p.health); assertEquals(15, p.damageTaken); assertEquals(1, world.heroHits);
    }

    @Test void groundHazardWarnsBeforeOneDamageEvent() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Player p = world.players[0]; p.invulnerable = 0;
        ArenaWorld.Hazard h = world.hazards[0]; h.active = true; h.radius = 100; h.warning = ArenaWorld.STEP * 2;
        h.ttl = .4f; h.damage = 22;
        world.update(ArenaWorld.STEP); assertEquals(100, p.health);
        world.update(ArenaWorld.STEP); assertEquals(78, p.health);
        p.invulnerable = 0; world.update(ArenaWorld.STEP); assertEquals(78, p.health);
    }

    @Test void finalBossClaimsFullPoolEnragesAndVictoryRequiresKill() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        for (int i = 0; i < world.enemies.length; i++) enemy(world, i, 900, 800, 100);
        world.debugStartBoss();
        assertEquals(ArenaWorld.MAX_ENEMIES, world.activeEnemies);
        assertTrue(world.bossSpawned); assertEquals(ArenaWorld.Outcome.RUNNING, world.outcome);
        world.boss.health = world.boss.maxHealth * .4f; world.players[0].attackCooldown = 10;
        world.update(ArenaWorld.STEP); assertEquals(2, world.bossPhase);
        world.players[0].attackCooldown = 0; world.boss.health = 1;
        world.boss.x = 80; world.boss.y = 0;
        for (ArenaWorld.Enemy e : world.enemies) if (e != world.boss) e.active = false;
        world.update(ArenaWorld.STEP);
        assertTrue(world.bossDefeated); assertEquals(ArenaWorld.Outcome.VICTORY, world.outcome);
    }

    @Test void elitesRewardSharedXpAndResetKeepsPoolsButChangesSeed() {
        ArenaWorld world = solo(ArenaWorld.Hero.WARDEN);
        ArenaWorld.Enemy elite = enemy(world, 0, 30, 0, 1); elite.elite = true;
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.elitesKilled); assertEquals(8, world.souls); assertEquals(8, world.progression.totalXp);
        int seed = world.runSeed; ArenaWorld.Shot shot = world.shots[0];
        world.reset(); assertSame(shot, world.shots[0]); assertNotEquals(seed, world.runSeed);
        assertEquals(0, world.players[0].damageDealt); assertFalse(world.bossSpawned);
    }

    @Test void heroSelectionIsInactiveOnlyAndRangerMovesFaster() {
        ArenaWorld world = solo(ArenaWorld.Hero.RANGER);
        assertThrows(IllegalStateException.class, () -> world.setHero(0, ArenaWorld.Hero.WARDEN));
        world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
        assertEquals(3.3f, world.players[0].x, .001f);
    }

    private static ArenaWorld solo(ArenaWorld.Hero hero) {
        ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0); return world;
    }
    private static ArenaWorld.Enemy enemy(ArenaWorld world, int index, float x, float y, float health) {
        ArenaWorld.Enemy e = world.enemies[index];
        e.active = true; e.x = x; e.y = y; e.health = e.maxHealth = health; e.attackCooldown = 10;
        world.activeEnemies++; return e;
    }
}
