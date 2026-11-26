package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatVfxStateTest {
    @Test void swordImpactReportsActualContactWithoutChangingDamageCadenceOrPoolLimits() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy target = enemy(world, 80, 100);
        world.update(ArenaWorld.STEP);
        assertEquals(100, target.health, .0001f);
        assertEquals(.7f, world.players[0].attackCooldown, .0001f);
        assertEquals(0, world.swordHits);
        awaitHits(world, 1);
        assertEquals(72, target.health, .0001f);
        assertEquals(1, world.swordHits);
        ArenaWorld.Effect hit = hit(world);
        assertEquals(ArenaWorld.ImpactKind.SWORD, hit.impactKind);
        assertEquals(28, hit.amount); assertFalse(hit.critical); assertFalse(hit.heroHit);
        assertEquals(1, hit.dirX); assertEquals(0, hit.dirY);
        assertTrue(hit.maxTtl >= .2f);

        ArenaWorld full = solo(); target = enemy(full, 80, 100);
        for (ArenaWorld.Effect fx : full.effects) { fx.active = true; fx.kind = ArenaWorld.EffectKind.AURA; fx.ttl = 10; }
        full.update(ArenaWorld.STEP);
        assertEquals(100, target.health, .0001f);
        awaitHits(full, 1);
        assertEquals(72, target.health, .0001f);
        assertEquals(1, full.swordHits); assertEquals(ArenaWorld.MAX_EFFECTS, full.effects.length);
    }

    @Test void attackIndicatorRangeTracksGearAndSkillsAndMatchesPrimaryAcquisition() {
        ArenaWorld world = solo();
        ArenaWorld.Player player = world.players[0];
        assertEquals(160f, world.primaryAttackRange(player));
        player.cleaveMultiplier = 1.4f;
        world.equipment.equip(0, 6, ItemCatalog.byId("farreach_gauntlets"));
        float reach = 160f * 1.4f * 1.15f;
        assertEquals(reach, world.primaryAttackRange(player), .0001f);
        ArenaWorld.Enemy target = enemy(world, reach + 1f, 1000);
        world.update(ArenaWorld.STEP);
        assertEquals(0, world.swordSwings, "Enemy centers outside the indicated acquisition radius do not start a swing");
        target.x = reach - 1f;
        world.update(ArenaWorld.STEP);
        assertEquals(1, world.swordSwings);
        assertEquals(reach, wave(world).reach, .0001f);
        player.hero = ArenaWorld.Hero.RANGER;
        assertEquals(600f, world.primaryAttackRange(player), "Sword reach gear and ranks do not exaggerate Ranger targeting range");
    }

    @Test void rangerOnlyStartsFiringInsideItsShorterIndicatedRangeInSoloAndCoop() {
        for (int slot = 0; slot < 2; slot++) {
            ArenaWorld world = new ArenaWorld();
            if (slot == 1) world.activatePlayer(0);
            world.setHero(slot, ArenaWorld.Hero.RANGER); world.activatePlayer(slot);
            ArenaWorld.Player player = world.players[slot];
            ArenaWorld.Enemy target = enemy(world, player.x + 601f, 1000f);
            target.y = player.y; target.stunTime = 100f;
            world.update(ArenaWorld.STEP);
            assertEquals(0, world.arrowsFired, "Targets outside the smaller outline must not trigger a shot");
            target.x = player.x + 599f;
            world.update(ArenaWorld.STEP);
            assertEquals(1, world.arrowsFired, "Crossing inside the outline must immediately enable firing");
            assertEquals(.55f, player.attackCooldown, .0001f);
            assertEquals(16f, world.bolts[0].damage, .0001f);
        }
    }

    @Test void outwardWaveKeepsItsCommittedOriginAndAimWhileHasteCanOverlapItsFade() {
        ArenaWorld world = solo();
        ArenaWorld.Player player = world.players[0]; player.attackRateMultiplier = 3f;
        ArenaWorld.Enemy target = enemy(world, 80, 10000);
        world.update(ArenaWorld.STEP);
        MeleeWave first = wave(world);
        assertTrue(MeleeWave.TOTAL_SECONDS >= .35f && MeleeWave.TOTAL_SECONDS <= .4f, "The visible sweep needs enough reading time");
        assertEquals(.7f / 3f, player.attackCooldown, .0001f);
        assertEquals(10000f, target.health, .0001f);
        float angle = first.angle, x = first.x, y = first.y;
        world.setInput(0, 0, 1, false);
        world.update(.1f); world.update(.1f);
        assertTrue(first.active, "The trail remains visible beyond its former 0.18-second lifetime");
        assertEquals(angle, first.angle); assertEquals(x, first.x); assertEquals(y, first.y);
        assertTrue(player.y > 0); assertEquals(1, world.swordSwings);
        assertEquals(9972f, target.health, .0001f);
        world.update(.05f);
        assertEquals(2, world.swordSwings, "A longer cosmetic sweep must not postpone the next haste attack");
        assertEquals(9972f, target.health, .0001f, "The next wave must travel before dealing its own damage");
        int trails = 0;
        for (MeleeWave wave : world.meleeWaves) if (wave.active && wave.owner == 0) trails++;
        assertEquals(2, trails, "The older fade cannot erase the next traveling gameplay wave");
        awaitHits(world, 2);
        assertEquals(9944f, target.health, .0001f);
    }

    @Test void boltImpactKeepsItsCriticalTravelAimAndCasterIdentityAfterPoolReuse() {
        ArenaWorld world = solo(); world.players[0].attackCooldown = 10;
        ArenaWorld.Enemy target = enemy(world, 80, 25); target.elite = true;
        ArenaWorld.Bolt bolt = world.bolts[0];
        bolt.active = true; bolt.x = 70; bolt.y = 0; bolt.vx = 760; bolt.ttl = 1;
        bolt.damage = 32; bolt.critical = true; bolt.launchFacingX = .8f; bolt.launchFacingY = -.6f;
        world.update(ArenaWorld.STEP);
        assertFalse(target.active); assertEquals(1, world.boltHits); assertEquals(1, world.criticalHits);
        ArenaWorld.Effect fx = hit(world);
        assertEquals(ArenaWorld.ImpactKind.BOLT, fx.impactKind); assertTrue(fx.critical); assertEquals(25, fx.amount);
        assertEquals(70 + 760 * ArenaWorld.STEP, fx.x, .0001f);
        assertEquals(.8f, fx.projectileFacingX); assertEquals(-.6f, fx.projectileFacingY);
        assertTrue(fx.elite); assertEquals(ArenaWorld.EnemyKind.GHOUL, fx.enemyKind);
        target.kind = ArenaWorld.EnemyKind.BOSS; target.elite = false;
        assertTrue(fx.elite); assertEquals(ArenaWorld.EnemyKind.GHOUL, fx.enemyKind);
    }

    @Test void levelBurstRunsDuringChoiceFreezeAndDoesNotReplayForTheFirstAcknowledgment() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        ArenaWorld.Enemy target = enemy(world, 350, 100); target.spawnAge = .12f;
        world.effects[0].active = true; world.effects[0].ttl = .6f;
        world.progression.addXp(world.progression.xpToNext);
        assertEquals(0, world.levelUpFxAge, "The burst starts before the TV publishes the offer");
        int round = world.progression.choiceRound;
        world.update(.1f);
        assertEquals(.1f, world.levelUpFxAge, .0001f);
        assertEquals(0, world.elapsed); assertEquals(.12f, target.spawnAge); assertEquals(.6f, world.effects[0].ttl);
        assertEquals(29.9f, world.progression.choiceSecondsLeft(), .0001f);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(.1f);
        assertEquals(round, world.progression.choiceRound); assertEquals(.2f, world.levelUpFxAge, .0001f);
        assertTrue(world.progression.choose(1, world.progression.offers[1].id, 0));
        world.progression.addXp(world.progression.xpToNext);
        assertEquals(round + 1, world.progression.choiceRound); assertEquals(0, world.levelUpFxAge);
        world.update(0); assertEquals(0, world.levelUpFxAge);
    }

    @Test void queuedSkillRoundsEachStartOneBurstAndLootDoesNotStartAnother() {
        ArenaWorld world = solo(); world.progression.addXp(16);
        int round = world.progression.choiceRound;
        world.update(.1f);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        assertEquals(round + 1, world.progression.choiceRound); assertEquals(0, world.levelUpFxAge);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(.1f); float age = world.levelUpFxAge;
        world.equipment.grantReward(); world.update(.1f);
        assertEquals(round + 1, world.progression.choiceRound);
        assertEquals(age + .1f, world.levelUpFxAge, .0001f);
        world.reset(); assertEquals(2, world.levelUpFxAge); assertEquals(0, world.criticalHits);
    }

    @Test void arrivalsHaveACombatOwnedAgeAndResetOnActualEnemyReuse() {
        ArenaWorld world = solo(); world.players[0].attackCooldown = 10;
        for (int i = 0; i < 7; i++) world.update(.1f);
        ArenaWorld.Enemy born = null;
        for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active) { born = enemy; break; }
        assertNotNull(born); assertTrue(born.spawnAge < .1f);
        float age = born.spawnAge;
        world.progression.addXp(world.progression.xpToNext); world.update(.1f);
        assertEquals(age, born.spawnAge);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0)); world.update(.1f);
        assertEquals(age + .1f, born.spawnAge, .0001f);
        world.reset(); assertEquals(1, born.spawnAge); assertFalse(born.active);
    }

    private static ArenaWorld solo() { ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world; }
    private static ArenaWorld.Enemy enemy(ArenaWorld world, float x, float health) {
        ArenaWorld.Enemy e = world.enemies[0]; e.active = true; e.x = x; e.y = 0;
        e.health = e.maxHealth = health; e.attackCooldown = 10; world.activeEnemies = 1; return e;
    }
    private static MeleeWave wave(ArenaWorld world) {
        for (MeleeWave wave : world.meleeWaves) if (wave.active) return wave;
        throw new AssertionError("No committed melee wave");
    }
    private static void awaitHits(ArenaWorld world, int hits) {
        for (int i = 0; i < 15 && world.swordHits < hits; i++) world.update(ArenaWorld.STEP);
        assertEquals(hits, world.swordHits, "The expanding wave must reach its target within 240 ms");
    }
    private static ArenaWorld.Effect hit(ArenaWorld world) {
        for (ArenaWorld.Effect effect : world.effects) if (effect.active && effect.kind == ArenaWorld.EffectKind.HIT && !effect.heroHit) return effect;
        throw new AssertionError("No committed impact");
    }
}
