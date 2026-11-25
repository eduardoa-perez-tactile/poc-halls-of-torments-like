package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillExpansionCombatTest {
    private static final float EPS = .001f;

    @Test void ricochetWaitsForPierceExhaustionAndCannotRevisitTheSameSpawn() {
        ArenaWorld world = solo(); world.players[0].hero = ArenaWorld.Hero.RANGER;
        ArenaWorld.Enemy first = enemy(world, 0, 75, 0, 100);
        ArenaWorld.Enemy second = enemy(world, 1, 110, 0, 100);
        ArenaWorld.Enemy third = enemy(world, 2, 150, 90, 100);
        ArenaWorld.Bolt bolt = bolt(world, 62, 0, 760, 0);
        bolt.pierce = 1; bolt.ricochetsRemaining = 1;
        world.update(ArenaWorld.STEP);
        assertEquals(90, first.health, EPS); assertEquals(100, second.health, EPS);
        assertEquals(1, bolt.ricochetsRemaining, "The original piercing hit must not consume a redirect");
        for (int i = 0; i < 4 && bolt.ricochetsRemaining > 0; i++) world.update(ArenaWorld.STEP);
        assertEquals(90, second.health, EPS); assertEquals(0, bolt.ricochetsRemaining);
        assertTrue(bolt.vy > 0, "The redirect acquires the unvisited third enemy");
        assertEquals(7.5f, bolt.damage, EPS);
        for (int i = 0; i < 15 && bolt.active; i++) world.update(ArenaWorld.STEP);
        assertEquals(92.5f, third.health, EPS); assertFalse(bolt.active);
        assertEquals(90, first.health, EPS); assertEquals(90, second.health, EPS);
    }

    @Test void aReusedEnemySlotIsANewRicochetCandidate() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy reused = enemy(world, 0, 120, 0, 100);
        ArenaWorld.Bolt bolt = bolt(world, 60, 0, 760, 0);
        bolt.ricochetsRemaining = 1; bolt.hitMask = 1;
        bolt.hitGenerations[0] = reused.spawnId;
        assertFalse(world.skills.ricochet(bolt));
        reused.spawnId++;
        assertTrue(world.skills.ricochet(bolt), "Pool storage is not the identity of a live enemy");
    }

    @Test void projectileSpeedChangesReleaseVelocityAndLifetimeButNotTravelBudget() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        player.hero = ArenaWorld.Hero.RANGER; player.projectileSpeedLevel = 3; player.attackCooldown = 0;
        enemy(world, 0, 700, 0, 1000);
        world.update(ArenaWorld.STEP);
        ArenaWorld.Bolt bolt = world.bolts[0];
        assertEquals(760 * 1.3f, bolt.vx, EPS);
        assertEquals(760 * 1.2f, bolt.vx * (bolt.ttl + ArenaWorld.STEP), .01f);
        assertEquals(ArenaWorld.STEP, bolt.age, EPS);
    }

    @Test void orbitersOnlyDamageContactAndHaveBoundedRepeatFrequency() {
        ArenaWorld world = solo(); world.players[0].sentinelLevel = 1;
        ArenaWorld.Enemy near = enemy(world, 0, 90, 3, 1000);
        ArenaWorld.Enemy far = enemy(world, 1, 260, 0, 1000);
        world.skills.update();
        assertEquals(988, near.health, EPS); assertEquals(1000, far.health, EPS);
        for (int i = 0; i < 8; i++) world.skills.update();
        assertEquals(988, near.health, EPS, "A blade touching on adjacent frames cannot deal frame-rate damage");
        assertEquals(2, countOrbiters(world));
        world.players[0].alive = false; world.skills.update(); assertEquals(0, countOrbiters(world));
    }

    @Test void fieldsRespectOwnerCapsDurationAndCorrectDamageOwnership() {
        ArenaWorld world = solo(); world.activatePlayer(1);
        ArenaWorld.Player player = world.players[1];
        player.consecrationLevel = 3; player.durationLevel = 3;
        ArenaWorld.Enemy target = enemy(world, 0, 300, 0, 10000);
        for (int i = 0; i < 200; i++) world.skills.update();
        int count = 0;
        for (SkillExpansion.GroundField field : world.skills.fields) if (field.active) {
            count++; assertEquals(1, field.owner); assertEquals(5.8f, field.maxTtl, EPS);
        }
        assertEquals(3, count); assertTrue(target.health < 10000);
        assertEquals(10000 - target.health, player.damageDealt, .02f);
        assertEquals(0, world.players[0].damageDealt);
        world.deactivatePlayer(1);
        for (SkillExpansion.GroundField field : world.skills.fields) assertFalse(field.active);
    }

    @Test void glaiveHitsOnceOnEachLegAndPreservesRangeAtHigherSpeed() {
        for (int speedRank = 0; speedRank <= 3; speedRank += 3) {
            ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
            player.glaiveLevel = 1; player.projectileSpeedLevel = speedRank;
            ArenaWorld.Enemy target = enemy(world, 0, 150, 0, 1000);
            float maximumX = 0;
            for (int tick = 0; tick < 110; tick++) {
                world.skills.update(); maximumX = Math.max(maximumX, world.skills.glaives[0].x);
            }
            assertEquals(960, target.health, EPS, "Exactly one 20-damage contact on each leg");
            assertEquals(280, maximumX, .01f); assertFalse(world.skills.glaives[0].active);
        }
    }

    @Test void wardAbsorbsOneRealHitAndKeepsHealthAndSustainIntact() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0]; player.wardLevel = 1;
        hostileShot(world); world.update(ArenaWorld.STEP);
        assertEquals(100, player.health); assertEquals(0, player.damageTaken);
        assertEquals(18, player.wardCooldown, EPS); assertEquals(1, world.blockedHits);
        player.invulnerable = 0; hostileShot(world); world.update(ArenaWorld.STEP);
        assertEquals(90, player.health, EPS); assertEquals(10, player.damageTaken, EPS);
        player.wardLevel = 3; world.skills.update();
        assertTrue(player.wardCooldown < 12, "A recharge upgrade applies immediately without giving a second charge");
    }

    @Test void frostResistsBossesAndDoesNotChangeCommittedAttackTiming() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        player.frostLevel = player.durationLevel = 3;
        ArenaWorld.Enemy boss = enemy(world, 0, 300, 0, 1000); boss.kind = ArenaWorld.EnemyKind.BOSS;
        world.skills.frost(player, boss);
        assertEquals(.0625f, boss.slowAmount, EPS); assertEquals(1.0875f, boss.slowTime, EPS);
        ArenaWorld.Enemy wraith = enemy(world, 1, 200, 0, 1000);
        wraith.kind = ArenaWorld.EnemyKind.WRAITH; wraith.attack = ArenaWorld.EnemyAttack.WRAITH_DASH;
        wraith.attackAge = .1f; wraith.attackCooldown = 3;
        world.skills.frost(player, wraith); world.update(ArenaWorld.STEP);
        assertEquals(.1f + ArenaWorld.STEP, wraith.attackAge, EPS);
        assertEquals(0, wraith.x - 200, EPS);
    }

    @Test void weakerHitsCannotExtendTheStrongerStasisSlow() {
        ArenaWorld world = solo(); ArenaWorld.Enemy target = enemy(world, 0, 300, 0, 1000);
        world.skills.slow(target, .5f, .5f); world.skills.slow(target, .25f, 3);
        assertEquals(.5f, target.slowAmount, EPS); assertEquals(.5f, target.slowTime, EPS);
        world.skills.slow(target, .5f, 3); assertEquals(3, target.slowTime, EPS);
    }

    @Test void echoAndReapUseCommittedPositionsAndNeverScheduleRecursiveEffects() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0]; player.reapingStroke = true;
        player.facingX = 1; player.facingY = 0;
        ArenaWorld.Enemy front = enemy(world, 0, 80, 0, 1000);
        ArenaWorld.Enemy behind = enemy(world, 1, -80, 0, 1000);
        world.skills.swordFollowup(player, 160); player.x = 500;
        for (int i = 0; i < 12; i++) world.skills.update();
        assertEquals(988.8f, front.health, EPS); assertEquals(1000, behind.health, EPS);
        world.skills.echo(0, 80, 0, 100); front.x = 250;
        ArenaWorld.Enemy replacement = enemy(world, 2, 80, 0, 1000);
        for (int i = 0; i < 20; i++) world.skills.update();
        assertEquals(955, replacement.health, EPS); assertEquals(988.8f, front.health, EPS);
        for (int i = 0; i < 30; i++) world.skills.update();
        assertEquals(955, replacement.health, EPS);
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) assertFalse(strike.active);
    }

    @Test void shatterDamagesNeighboursOnceWithoutRehittingPiercedVictims() {
        ArenaWorld world = solo();
        ArenaWorld.Enemy victim = enemy(world, 0, 100, 0, 1000);
        ArenaWorld.Enemy neighbour = enemy(world, 1, 100, 50, 1000);
        ArenaWorld.Bolt bolt = bolt(world, 100, 0, 760, 0); bolt.damage = 100;
        bolt.hitMask = 1; bolt.hitGenerations[0] = victim.spawnId;
        world.skills.shatter(bolt); world.skills.update(); world.skills.update();
        assertEquals(1000, victim.health, EPS); assertEquals(955, neighbour.health, EPS);
    }

    @Test void soulHarvestAttractsOnlyExistingSoulsAndAwardsPartyXpOnce() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        ArenaWorld.Pickup soul = world.pickups[0]; soul.active = true; soul.value = 1; soul.x = 500;
        ArenaWorld.Pickup heal = world.pickups[1]; heal.active = heal.healing = true; heal.value = 20; heal.x = 500;
        SkillExpansion.WorldRelic relic = relic(world, SkillExpansion.RelicKind.SOUL_HARVEST);
        world.skills.collectRelic(relic, player);
        assertEquals(0, soul.vacuumOwner); assertEquals(-1, heal.vacuumOwner);
        for (int i = 0; i < 30; i++) world.update(ArenaWorld.STEP);
        assertFalse(soul.active); assertTrue(heal.active); assertEquals(500, heal.x);
        assertEquals(1, world.progression.totalXp); assertEquals(1, world.souls);
        assertEquals(0, world.equipment.rewardsEarned());
        world.skills.collectRelic(relic, player); assertEquals(1, world.skills.relicsCollected);
    }

    @Test void choicePauseFreezesAllNewSimulationAndResetClearsIt() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        player.glaiveLevel = player.consecrationLevel = player.sentinelLevel = player.wardLevel = 1;
        player.wardCooldown = 10; enemy(world, 0, 150, 0, 10000);
        world.skills.update();
        float fieldTtl = world.skills.fields[0].ttl, glaiveX = world.skills.glaives[0].x;
        float ward = player.wardCooldown, angle = player.sentinelAngle;
        SkillExpansion.WorldRelic relic = relic(world, SkillExpansion.RelicKind.STASIS); relic.x = 500;
        world.progression.addXp(world.progression.xpToNext); world.update(.1f);
        assertEquals(fieldTtl, world.skills.fields[0].ttl); assertEquals(glaiveX, world.skills.glaives[0].x);
        assertEquals(ward, player.wardCooldown); assertEquals(angle, player.sentinelAngle); assertEquals(0, relic.age);
        world.reset();
        assertEquals(0, player.glaiveLevel); assertFalse(player.echoStorm); assertFalse(relic.active);
        for (SkillExpansion.GroundField field : world.skills.fields) assertFalse(field.active);
        for (SkillExpansion.Glaive glaive : world.skills.glaives) assertFalse(glaive.active);
    }

    @Test void scheduledRelicsAreBoundedAndDoNotGrantRewardsOrPauseOnAppearance() {
        ArenaWorld world = solo(); world.elapsed = world.durationSeconds() * .14f;
        world.skills.update(); int count = 0;
        for (SkillExpansion.WorldRelic relic : world.skills.relics) if (relic.active) {
            count++; assertEquals(SkillExpansion.RelicKind.SOUL_HARVEST, relic.kind);
            assertTrue(ArenaWorld.chestLandmarkClear(relic.x, relic.y));
        }
        assertEquals(1, count); assertEquals(1, world.skills.relicsPlaced);
        assertEquals(0, world.equipment.rewardsEarned()); assertFalse(world.choicePending());
        assertEquals(0, world.progression.totalXp);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); world.debugSceneryView(0, 0);
        world.players[0].attackCooldown = 100; return world;
    }
    private static ArenaWorld.Enemy enemy(ArenaWorld world, int index, float x, float y, float health) {
        ArenaWorld.Enemy enemy = world.enemies[index]; enemy.active = true; enemy.x = x; enemy.y = y;
        enemy.spawnId = index + 1; enemy.health = enemy.maxHealth = health; enemy.damage = 0;
        enemy.stunTime = enemy.attackCooldown = 100; world.activeEnemies++; return enemy;
    }
    private static ArenaWorld.Bolt bolt(ArenaWorld world, float x, float y, float vx, float vy) {
        ArenaWorld.Bolt bolt = world.bolts[0]; bolt.active = true; bolt.x = x; bolt.y = y;
        bolt.vx = vx; bolt.vy = vy; bolt.ttl = 1; bolt.damage = 10; bolt.owner = 0;
        java.util.Arrays.fill(bolt.hitGenerations, -1); return bolt;
    }
    private static void hostileShot(ArenaWorld world) {
        ArenaWorld.Shot shot = world.shots[0]; shot.active = true; shot.x = shot.y = shot.vx = shot.vy = 0;
        shot.radius = 10; shot.damage = 10; shot.ttl = 1;
    }
    private static int countOrbiters(ArenaWorld world) {
        int count = 0; for (SkillExpansion.OrbitBlade blade : world.skills.orbiters) if (blade.active) count++; return count;
    }
    private static SkillExpansion.WorldRelic relic(ArenaWorld world, SkillExpansion.RelicKind kind) {
        SkillExpansion.WorldRelic relic = world.skills.relics[0]; relic.active = true; relic.kind = kind;
        relic.x = world.players[0].x; relic.y = world.players[0].y; return relic;
    }
}
