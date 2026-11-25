package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Short encounter checks; these do not simulate or establish twenty-minute balance. */
class SovereignDifficultyTest {
    private static final float EPSILON = .001f;

    @Test void fullRunWarnsForASecondThenReleasesTheLockedVolley() {
        ArenaWorld world = encounter(ArenaTimeline.TWENTY_MINUTES);
        var sovereign = world.sovereign;
        sovereign.attackCooldown = 0;
        step(world);
        assertEquals(1, sovereign.windup); assertEquals(1, sovereign.castDuration);
        assertEquals(2.8f, sovereign.attackCooldown, EPSILON);
        assertEquals(1, sovereign.releaseAge, "The windup has not released a projectile");
        assertFalse(sovereign.bossCastEnraged);
        assertEquals(0, shots(world));
        var warning = firstHazard(world);
        assertEquals(125, warning.radius); assertEquals(22, warning.damage);
        assertEquals(0, warning.x); assertEquals(0, warning.y);
        assertTrue(warning.warning > .9f);
        float aimX = sovereign.castFacingX, aimY = sovereign.castFacingY;
        float bossX = sovereign.x, bossY = sovereign.y;
        world.players[0].y = 220;
        for (int tick = 0; tick < 59; tick++) {
            step(world);
            assertEquals(0, shots(world), "No flight head may appear during the warning");
        }
        release(world);
        assertEquals(0, sovereign.releaseAge);
        assertEquals(bossX, sovereign.x); assertEquals(bossY, sovereign.y);
        assertVolley(world, 5, 220, 18, aimX, aimY);
        assertEquals(0, warning.x); assertEquals(0, warning.y,
            "The ground circle does not follow the player's escape");
    }

    @Test void aHealthThresholdCrossingCannotChangeAnAlreadyWarnedCast() {
        ArenaWorld world = encounter(ArenaTimeline.TWENTY_MINUTES);
        var sovereign = world.sovereign;
        sovereign.attackCooldown = 0;
        step(world);
        float aimX = sovereign.castFacingX, aimY = sovereign.castFacingY;
        sovereign.health = sovereign.maxHealth * .4f;
        world.players[0].y = 220;
        step(world);
        assertEquals(2, sovereign.encounterPhase);
        assertFalse(sovereign.bossCastEnraged, "The committed healthy cast keeps its original phase");
        release(world);
        assertVolley(world, 5, 220, 18, aimX, aimY);
        assertEquals(22, firstHazard(world).damage);

        clearProjectilesAndHazards(world);
        sovereign.attackCooldown = 0;
        step(world);
        assertTrue(sovereign.bossCastEnraged);
        assertEquals(2.1f, sovereign.attackCooldown, EPSILON);
        assertEquals(150, firstHazard(world).radius); assertEquals(28, firstHazard(world).damage);
        assertEquals(0, shots(world));
        aimX = sovereign.castFacingX; aimY = sovereign.castFacingY;
        world.players[0].y = -220;
        release(world);
        assertVolley(world, 7, 250, 22, aimX, aimY);
    }

    @Test void fullRunMovementAndReleasedShotsApplyTheRevisedPressure() {
        for (boolean enraged : new boolean[]{false, true}) {
            ArenaWorld world = encounter(ArenaTimeline.TWENTY_MINUTES);
            var sovereign = world.sovereign;
            sovereign.health = sovereign.maxHealth * (enraged ? .4f : 1);
            sovereign.attackCooldown = 10;
            float x = sovereign.x, y = sovereign.y;
            step(world);
            assertEquals((enraged ? 80 : 65) * ArenaWorld.STEP,
                Math.hypot(sovereign.x - x, sovereign.y - y), EPSILON);

            sovereign.attackCooldown = 0;
            step(world); release(world);
            // Let the volley leave the boss's contact body before placing the hero in its lane.
            for (int tick = 0; tick < 35; tick++) step(world);
            var shot = firstShot(world);
            assertTrue(Math.hypot(shot.vx, shot.vy) > ArenaWorld.Hero.RANGER.moveSpeed,
                "A base-speed Ranger cannot outrun the incoming flight head along its lane");
            clearHazards(world);
            world.players[0].x = shot.x + shot.vx * ArenaWorld.STEP;
            world.players[0].y = shot.y + shot.vy * ArenaWorld.STEP;
            world.players[0].invulnerable = 0;
            step(world);
            assertEquals(enraged ? 78 : 82, world.players[0].health, EPSILON,
                "The revised shot damage must reach actual player health");
            assertEquals(1, world.heroHits);
        }
    }

    @Test void skillAndLootChoicesFreezeTheSamePendingSovereignCast() {
        ArenaWorld world = encounter(ArenaTimeline.TWENTY_MINUTES);
        world.sovereign.attackCooldown = 0; step(world);
        float windup = world.sovereign.windup, age = world.sovereign.castAge, elapsed = world.elapsed;
        world.equipment.grantReward();
        assertTrue(world.lootPending());
        world.progression.addXp(world.progression.xpToNext);
        assertTrue(world.levelUpPending());
        world.update(.2f);
        assertEquals(windup, world.sovereign.windup); assertEquals(age, world.sovereign.castAge);
        assertEquals(elapsed, world.elapsed); assertEquals(0, shots(world));
        finishChoices(world); step(world);
        assertTrue(world.sovereign.windup < windup);
        assertFalse(world.sovereign.bossCastEnraged);
        release(world);
        assertEquals(5, shots(world));
    }

    @Test void theShortDevelopmentFixtureRetainsItsLegacyEncounterNumbers() {
        ArenaWorld world = encounter(ArenaTimeline.SHORT);
        assertEquals(450, world.sovereign.maxHealth);
        var sovereign = world.sovereign;
        sovereign.attackCooldown = 10;
        float x = sovereign.x;
        step(world);
        assertEquals(44 * ArenaWorld.STEP, sovereign.x - x, EPSILON);
        sovereign.attackCooldown = 0; step(world);
        assertEquals(3.4f, sovereign.attackCooldown, EPSILON);
        assertEquals(5, shots(world), "The existing short fixture still releases immediately");
        for (var shot : world.shots) if (shot.active) {
            assertEquals(145, Math.hypot(shot.vx, shot.vy), EPSILON);
            assertEquals(15, shot.damage);
        }
    }

    private static ArenaWorld encounter(ArenaTimeline timeline) {
        ArenaWorld world = new ArenaWorld(timeline); world.activatePlayer(0);
        world.players[0].attackCooldown = 1000; world.players[0].invulnerable = 1000;
        world.debugStartSovereign();
        world.update(ArenaWorld.STEP); finishChoices(world);
        isolate(world);
        var sovereign = world.sovereign;
        sovereign.x = -360; sovereign.y = 0; sovereign.windup = 0;
        sovereign.attackCooldown = 1000;
        sovereign.stunTime = 0;
        world.players[0].x = 0; world.players[0].y = 0;
        clearProjectilesAndHazards(world);
        return world;
    }

    private static void step(ArenaWorld world) {
        finishChoices(world); isolate(world); world.update(ArenaWorld.STEP);
    }

    private static void isolate(ArenaWorld world) {
        int active = 0;
        for (var enemy : world.enemies) {
            if (enemy != world.sovereign) enemy.active = false;
            if (enemy.active) active++;
        }
        world.activeEnemies = active;
    }

    private static void release(ArenaWorld world) {
        for (int tick = 0; tick < 61 && world.sovereign.windup > 0; tick++) step(world);
        assertEquals(0, world.sovereign.windup, "A bounded fixture must finish this one-second windup");
    }

    private static void assertVolley(ArenaWorld world, int count, float speed, float damage, float aimX, float aimY) {
        assertEquals(count, shots(world));
        double aim = Math.atan2(aimY, aimX), min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var shot : world.shots) if (shot.active) {
            assertEquals(ArenaWorld.EnemyKind.SOVEREIGN, shot.casterKind);
            assertEquals(aimX, shot.launchFacingX); assertEquals(aimY, shot.launchFacingY);
            assertEquals(speed, Math.hypot(shot.vx, shot.vy), EPSILON); assertEquals(damage, shot.damage);
            double angle = Math.atan2(Math.sin(Math.atan2(shot.vy, shot.vx) - aim), Math.cos(Math.atan2(shot.vy, shot.vx) - aim));
            min = Math.min(min, angle); max = Math.max(max, angle);
        }
        assertEquals(-(count - 1) * .11, min, EPSILON);
        assertEquals((count - 1) * .11, max, EPSILON);
    }

    private static int shots(ArenaWorld world) {
        int count = 0; for (var shot : world.shots) if (shot.active) count++; return count;
    }
    private static ArenaWorld.Shot firstShot(ArenaWorld world) {
        for (var shot : world.shots) if (shot.active) return shot;
        throw new AssertionError("Expected a released shot");
    }
    private static ArenaWorld.Hazard firstHazard(ArenaWorld world) {
        for (var hazard : world.hazards) if (hazard.active) return hazard;
        throw new AssertionError("Expected a ground warning");
    }
    private static void clearHazards(ArenaWorld world) { for (var hazard : world.hazards) hazard.active = false; }
    private static void clearProjectilesAndHazards(ArenaWorld world) {
        for (var shot : world.shots) shot.active = false; clearHazards(world);
    }
    private static void finishChoices(ArenaWorld world) {
        for (int round = 0; round < 32 && world.choicePending(); round++) for (int slot = 0; slot < 2; slot++) {
            if (world.progression.offers[slot].pending)
                assertTrue(world.progression.choose(slot, world.progression.offers[slot].id, 0));
            if (!world.levelUpPending() && world.equipment.offers[slot].pending)
                assertTrue(world.equipment.choose(slot, world.equipment.offers[slot].id, -1));
        }
        assertFalse(world.choicePending());
    }
}
