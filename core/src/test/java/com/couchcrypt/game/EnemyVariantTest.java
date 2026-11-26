package com.couchcrypt.game;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyVariantTest {
    private static final float EPSILON = .001f;

    @Test void spawnedRolesComposeWithPhaseCoopAndEliteScaling() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(1);
        world.elapsed = 1080;

        ArenaWorld.Enemy moss = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        assertStats(moss, 95 * 4.5f * 1.6f * 4 * 1.4f, 18 * 1.95f, 1.315f * .85f);
        ArenaWorld.Enemy frost = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        assertStats(frost, 32 * 4.5f * 1.6f * .8f, 12 * 1.95f, 1.315f * 1.25f);
        ArenaWorld.Enemy ember = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.EMBER);
        assertStats(ember, 35 * 4.5f * 1.6f * .85f, 12 * 1.95f * 1.25f, 1.315f);
        ArenaWorld.Enemy dusk = spawn(world, ArenaWorld.EnemyKind.WRAITH, false, EnemyVariant.DUSK);
        assertStats(dusk, 18 * 4.5f * 1.6f * 1.15f, 8 * 1.95f * .85f, 1.315f * 1.1f);

        ArenaWorld.Enemy boss = spawn(world, ArenaWorld.EnemyKind.BOSS, false, EnemyVariant.ORIGINAL);
        assertStats(boss, 8200 * 1.6f, 20, 1);
    }

    @Test void fullRunIntroducesEachSpeciesVariantsSeparatelyAndKeepsOriginalsCommon() {
        ArenaWorld.EnemyKind[] kinds = {ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH,
            ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST};
        EnemyVariant[] first = {EnemyVariant.MOSS, EnemyVariant.EMBER, EnemyVariant.MOSS, EnemyVariant.EMBER};
        EnemyVariant[] second = {EnemyVariant.FROST, EnemyVariant.DUSK, EnemyVariant.FROST, EnemyVariant.DUSK};
        float[] firstAt = {240, 480, 660, 840}, secondAt = {360, 600, 780, 960};
        for (int i = 0; i < kinds.length; i++) {
            int[] before = distribution(kinds[i], firstAt[i] - .001f);
            assertEquals(10000, before[EnemyVariant.ORIGINAL.ordinal()], kinds[i].name());
            int[] firstLearned = distribution(kinds[i], firstAt[i] + 90);
            assertShare(firstLearned, first[i], .17f, .23f);
            assertEquals(0, firstLearned[second[i].ordinal()], "Teach one variant before the next");
            int[] beforeSecond = distribution(kinds[i], secondAt[i] - .001f);
            assertEquals(0, beforeSecond[second[i].ordinal()]);
            int[] mature = distribution(kinds[i], secondAt[i] + 90);
            assertShare(mature, first[i], .17f, .23f);
            assertShare(mature, second[i], .17f, .23f);
            assertShare(mature, EnemyVariant.ORIGINAL, .55f, .65f);
        }
    }

    @Test void newlyIntroducedVariantsStartRareAndGrowGradually() {
        ArenaWorld.EnemyKind[] kinds = {ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH,
            ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST};
        EnemyVariant[][] variants = {{EnemyVariant.MOSS, EnemyVariant.FROST},
            {EnemyVariant.EMBER, EnemyVariant.DUSK}, {EnemyVariant.MOSS, EnemyVariant.FROST},
            {EnemyVariant.EMBER, EnemyVariant.DUSK}};
        float[][] introductions = {{240, 360}, {480, 600}, {660, 780}, {840, 960}};
        for (int species = 0; species < kinds.length; species++) for (int variant = 0; variant < 2; variant++) {
            EnemyVariant expected = variants[species][variant];
            float start = introductions[species][variant];
            int[] debut = distribution(kinds[species], start);
            int[] learning = distribution(kinds[species], start + 45);
            int[] established = distribution(kinds[species], start + 90);
            assertShare(debut, expected, .03f, .07f);
            assertShare(learning, expected, .10f, .15f);
            assertShare(established, expected, .17f, .23f);
            assertTrue(debut[expected.ordinal()] < learning[expected.ordinal()]);
            assertTrue(learning[expected.ordinal()] < established[expected.ordinal()]);
        }
        for (ArenaWorld.EnemyKind kind : ArenaWorld.EnemyKind.values()) if (ArenaWorld.isBoss(kind))
            assertEquals(10000, distribution(kind, 1500)[EnemyVariant.ORIGINAL.ordinal()]);
    }

    @Test void actualFullRunSpawnsUseCombatTimeAndChoicePauseCannotUnlockVariants() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        for (ArenaWorld.EnemyKind kind : new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.GHOUL,
            ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST}) {
            for (int sample = 0; sample < 64; sample++) {
                ArenaWorld.Enemy enemy = spawnOnce(world, kind, sample % 2 == 0);
                assertEquals(EnemyVariant.ORIGINAL, enemy.variant, "Full-run opening includes no alternate palettes");
                enemy.active = false; world.activeEnemies--;
            }
        }
        world.elapsed = 239.99f;
        world.progression.addXp(world.progression.xpToNext);
        for (int tick = 0; tick < 20; tick++) world.update(.1f);
        assertEquals(239.99f, world.elapsed);
        assertTrue(world.choicePending());
        for (int sample = 0; sample < 64; sample++) {
            ArenaWorld.Enemy enemy = spawnOnce(world, ArenaWorld.EnemyKind.GHOUL, false);
            assertEquals(EnemyVariant.ORIGINAL, enemy.variant);
            enemy.active = false; world.activeEnemies--;
        }
        world.elapsed = 450;
        assertEquals(EnemyVariant.FROST,
            spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST).variant);
    }

    @Test void explicitArtReviewLineupStillIncludesLockedVariantsAtRunStart() {
        ArenaWorld world = solo(ArenaTimeline.TWENTY_MINUTES);
        world.debugEnemySizeLineup(ActorAnimation.Facing.SE);
        assertEquals(0, world.elapsed);
        assertEquals(12, world.activeEnemies);
        for (ArenaWorld.EnemyKind kind : new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.GHOUL,
            ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.BRUTE}) {
            EnemyVariant durable = kind == ArenaWorld.EnemyKind.GHOUL || kind == ArenaWorld.EnemyKind.BRUTE
                ? EnemyVariant.MOSS : EnemyVariant.DUSK;
            int originals = 0, variants = 0, elites = 0;
            for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active && enemy.kind == kind) {
                if (enemy.elite) {
                    assertEquals(durable, enemy.variant);
                    elites++;
                } else if (enemy.variant == EnemyVariant.ORIGINAL) originals++;
                else {
                    assertEquals(durable, enemy.variant);
                    variants++;
                }
            }
            assertEquals(1, originals, kind.name());
            assertEquals(1, variants, kind.name());
            assertEquals(1, elites, kind.name());
        }
    }

    private static int[] distribution(ArenaWorld.EnemyKind kind, float elapsed) {
        int[] counts = new int[EnemyVariant.values().length];
        for (int serial = 0; serial < 10000; serial++)
            counts[EnemyVariant.forSpawn(kind, 17011, serial, elapsed).ordinal()]++;
        return counts;
    }

    private static void assertShare(int[] counts, EnemyVariant variant, float minimum, float maximum) {
        float share = counts[variant.ordinal()] / 10000f;
        assertTrue(share >= minimum && share <= maximum,
            variant + " share " + share + " should be between " + minimum + " and " + maximum);
    }

    @Test void frostClosesFasterAndMossSlowerThroughActualMovement() throws Exception {
        EnemyVariant[] variants = {EnemyVariant.ORIGINAL, EnemyVariant.MOSS, EnemyVariant.FROST};
        float[] expectedSpeeds = {57, 57 * .85f, 57 * 1.25f};
        for (int i = 0; i < variants.length; i++) {
            ArenaWorld world = solo(ArenaTimeline.SHORT);
            ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, variants[i]);
            float x = enemy.x;
            world.update(ArenaWorld.STEP);
            assertEquals(expectedSpeeds[i] * ArenaWorld.STEP, x - enemy.x, EPSILON, variants[i].name());
            assertTrue(enemy.moving);
        }
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy elite = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        float x = elite.x;
        world.update(ArenaWorld.STEP);
        assertEquals(37 * .85f * 1.15f * ArenaWorld.STEP, x - elite.x, EPSILON,
            "Elite movement keeps its existing additional speed factor");
    }

    @Test void cultistShotsDealVariantDamageEvenAfterCasterSlotReuse() throws Exception {
        EnemyVariant[] variants = {EnemyVariant.EMBER, EnemyVariant.DUSK};
        float[] expectedDamage = {15, 10.2f};
        for (int i = 0; i < variants.length; i++) {
            ArenaWorld world = solo(ArenaTimeline.SHORT);
            ArenaWorld.Enemy caster = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, variants[i]);
            caster.x = 350; caster.y = 0; caster.attackCooldown = 0;
            for (int tick = 0; tick < 44; tick++) world.update(ArenaWorld.STEP);
            ArenaWorld.Shot shot = world.shots[0];
            assertTrue(shot.active); assertEquals(1, world.hostileShotsFired);
            assertEquals(expectedDamage[i], shot.damage, EPSILON);
            assertEquals(-180, shot.vx, EPSILON, "Variant damage does not speed up the projectile");
            caster.active = false; world.activeEnemies--;
            ArenaWorld.Enemy replacement = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.ORIGINAL);
            assertSame(caster, replacement);
            assertEquals(12, replacement.damage, EPSILON);
            shot.x = world.players[0].x; shot.y = world.players[0].y;
            world.update(ArenaWorld.STEP);
            assertFalse(shot.active);
            assertEquals(100 - expectedDamage[i], world.players[0].health, EPSILON);
            assertEquals(expectedDamage[i], world.players[0].damageTaken, EPSILON);
        }
    }

    @Test void reusedEnemySlotsReplaceModifiersRatherThanCompoundingThem() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        assertStats(enemy, 95 * 4 * 1.4f, 18, .85f);
        enemy.health = 1; enemy.active = false; world.activeEnemies--;
        ArenaWorld.Enemy reused = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        assertSame(enemy, reused);
        assertStats(reused, 32 * .8f, 12, 1.25f);
        reused.active = false; world.activeEnemies--;
        reused = spawn(world, ArenaWorld.EnemyKind.CULTIST, false, EnemyVariant.ORIGINAL);
        assertSame(enemy, reused);
        assertStats(reused, 35, 12, 1);
        world.reset(); world.activatePlayer(0);
        reused = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.ORIGINAL);
        assertSame(enemy, reused);
        assertStats(reused, 32, 12, 1);
    }

    @Test void choicePauseAndReactivatingParticipantPreserveExistingVariantStats() throws Exception {
        ArenaWorld world = solo(ArenaTimeline.SHORT);
        ArenaWorld.Enemy enemy = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.FROST);
        enemy.health -= 3;
        float health = enemy.health, maxHealth = enemy.maxHealth, speed = enemy.speedMultiplier, x = enemy.x;
        world.progression.addXp(world.progression.xpToNext);
        assertTrue(world.choicePending());
        world.update(.1f);
        world.activatePlayer(0); // Existing participant reconnect is a no-op, not a fresh spawn.
        assertEquals(EnemyVariant.FROST, enemy.variant);
        assertEquals(x, enemy.x); assertEquals(health, enemy.health); assertEquals(maxHealth, enemy.maxHealth);
        assertEquals(speed, enemy.speedMultiplier);
        assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
        world.update(ArenaWorld.STEP);
        assertTrue(enemy.x < x);
        assertEquals(health, enemy.health); assertEquals(maxHealth, enemy.maxHealth);
        assertEquals(speed, enemy.speedMultiplier);
    }

    private static void assertStats(ArenaWorld.Enemy enemy, float health, float damage, float speed) {
        assertEquals(health, enemy.health, EPSILON);
        assertEquals(health, enemy.maxHealth, EPSILON);
        assertEquals(damage, enemy.damage, EPSILON);
        assertEquals(speed, enemy.speedMultiplier, EPSILON);
    }

    private static ArenaWorld solo(ArenaTimeline timeline) {
        ArenaWorld world = new ArenaWorld(timeline);
        world.activatePlayer(0); world.players[0].attackCooldown = 20; world.players[0].invulnerable = 0;
        return world;
    }

    /** Exercise the real spawn initializer without advancing a survival schedule or exposing a test API. */
    private static ArenaWorld.Enemy spawn(ArenaWorld world, ArenaWorld.EnemyKind kind, boolean elite,
                                          EnemyVariant variant) throws Exception {
        for (int attempt = 0; attempt < 64; attempt++) {
            ArenaWorld.Enemy enemy = spawnOnce(world, kind, elite);
            if (enemy.variant == variant) return enemy;
            enemy.active = false; world.activeEnemies--;
        }
        throw new AssertionError("No " + variant + " spawn for " + kind);
    }

    private static ArenaWorld.Enemy spawnOnce(ArenaWorld world, ArenaWorld.EnemyKind kind, boolean elite)
        throws Exception {
        Method spawn = ArenaWorld.class.getDeclaredMethod("spawn", ArenaWorld.EnemyKind.class,
            boolean.class, ArenaWorld.Player.class, float.class);
        spawn.setAccessible(true);
        ArenaWorld.Enemy enemy = (ArenaWorld.Enemy) spawn.invoke(world, kind, elite, world.players[0], 0f);
        assertNotNull(enemy);
        return enemy;
    }
}
